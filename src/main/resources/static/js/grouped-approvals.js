/* Shared grouped approval history and decisions for desktop, mobile and Finance detail pages. */
(() => {
    'use strict';
    const match = location.pathname.match(/\/requests\/(\d+)\/?$/);
    if (!match) return;
    const requestId = match[1];
    const endpoint = '/api/requests/grouped-approvals';
    const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    let state, panel, busy = false, timer, csrf;

    async function json(url, options) {
        const response = await fetch(url, options);
        if (!response.ok) {
            let message = 'Unable to complete the operation. Refresh and try again.';
            try { const data = await response.json(); message = data.detail || data.message || data.error || message; } catch (_) {}
            const error = new Error(message); error.status = response.status; throw error;
        }
        return response.status === 204 ? null : response.json();
    }

    // ---------- Presentation helpers ----------
    const label = value => { const s = String(value ?? '').replaceAll('_', ' ').toLowerCase(); return s.charAt(0).toUpperCase() + s.slice(1); };
    function tone(stateValue, kind) {
        const s = String(stateValue ?? '').toUpperCase();
        if (s.startsWith('APPROV') || s === 'COMPLETED') return 'ok';
        if (s.startsWith('REJECT')) return 'bad';
        if (s.startsWith('RETURN')) return 'warn';
        if (s === 'ACTIVE' || s === 'IN_PROGRESS') return 'info';
        if (kind === 'member-active' && (s === 'PENDING' || s === 'WAITING')) return 'info';
        if (kind === 'request' && !/^(CANCELLED|CLOSED|DRAFT)$/.test(s)) return 'info';
        return 'muted';
    }
    const badge = (stateValue, kind) => `<span class="gl-badge gl-${tone(stateValue, kind)}">${escape(label(stateValue))}</span>`;
    function when(value) {
        const date = new Date(value);
        return escape(isNaN(date) ? String(value).replace('T', ' ')
            : date.toLocaleString(undefined, {year:'numeric', month:'short', day:'numeric', hour:'2-digit', minute:'2-digit'}));
    }
    function policyText(level) {
        return level.policy === 'ALL' ? 'Everyone must approve; one rejection rejects the request.'
            : 'The first decision resolves this level; other pending tasks close.';
    }
    const ACTIONS = {
        APPROVE: {text:'Approve', cls:'gl-btn-approve'},
        REJECT:  {text:'Reject', cls:'gl-btn-reject'},
        RETURN:  {text:'Return for correction', cls:'gl-btn-return'}
    };

    const svg = path => `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${path}</svg>`;
    const ICONS = {
        ok: svg('<path d="M5 12.5l4.5 4.5L19 7.5"/>'),
        bad: svg('<path d="M6 6l12 12M18 6L6 18"/>'),
        warn: svg('<path d="M9 14L4 9l5-5"/><path d="M4 9h10a6 6 0 010 12h-3"/>'),
        tree: svg('<rect x="3" y="3" width="6" height="6" rx="1"/><rect x="15" y="9" width="6" height="6" rx="1"/><rect x="15" y="17" width="6" height="4" rx="1"/><path d="M9 6h3v9h3M12 19h3"/>')
    };
    function markerFor(level) {
        return ICONS[tone(level.state, 'level')] || escape(level.ordinal);
    }
    function memberRow(member, level) {
        return `<li class="gl-member${member.taskId ? ' is-actionable' : ''}">
            <div class="gl-member-head"><strong class="gl-name">${escape(member.name)}</strong>${badge(member.state, level.state === 'ACTIVE' ? 'member-active' : 'member')}</div>
            ${member.decidedAt ? `<p class="gl-meta">Decided ${when(member.decidedAt)}</p>` : ''}
            ${member.comment ? `<p class="gl-comment">${escape(member.comment)}</p>` : ''}
            ${member.supersessionReason ? `<p class="gl-meta">${escape(member.supersessionReason)}</p>` : ''}
            ${member.taskId ? `<div class="grouped-actions">${Object.entries(ACTIONS).map(([action, a]) =>
                `<button type="button" class="gl-btn ${a.cls}" data-task="${escape(member.taskId)}" data-action="${action}">${a.text}</button>`).join('')}</div>` : ''}
        </li>`;
    }
    function levelBlock(level) {
        const pct = level.memberCount ? Math.min(100, Math.round(level.approvedCount / level.memberCount * 100)) : 0;
        const progress = level.policy === 'ALL'
            ? `<div class="gl-progress-row"><div class="gl-progress" role="progressbar" aria-label="Approvals received" aria-valuemin="0" aria-valuemax="${escape(level.memberCount)}" aria-valuenow="${escape(level.approvedCount)}"><span data-pct="${pct}"></span></div>
               <span class="gl-meta">${escape(level.approvedCount)} of ${escape(level.memberCount)} approvals received</span></div>`
            : level.state === 'ACTIVE' ? '<p class="gl-meta">Waiting for the first decision</p>' : '';
        return `<section class="gl-level gl-tone-${tone(level.state, 'level')}">
            <span class="gl-marker" aria-hidden="true">${markerFor(level)}</span>
            <div class="gl-level-head"><h3>Level ${escape(level.ordinal)}: ${escape(level.name)}</h3>${badge(level.state, 'level')}</div>
            <p class="gl-meta">${policyText(level)}</p>
            ${progress}
            <ul class="gl-members">${level.members.map(member => memberRow(member, level)).join('')}</ul>
        </section>`;
    }

    function place(element) {
        const hero = document.querySelector('.request-hero');          // Employee / HR pages: sit right under the request header
        const financeContent = document.getElementById('content');     // Finance page: sit above the request content
        if (hero) hero.after(element);
        else if (financeContent && financeContent.parentElement?.id === 'finance-request-root') financeContent.before(element);
        else (document.querySelector('main') || document.querySelector('[layout\\:fragment]') || document.body).prepend(element);
    }

    function render() {
        if (!state.cycles.length) return;
        if (!panel) {
            panel = document.createElement('section'); panel.id = 'groupedApprovalPanel';
            panel.className = 'grouped-panel'; panel.setAttribute('aria-label', 'Grouped approval progress');
            place(panel);
        }
        // Keep cycles the person opened or closed as they were across the 15s refresh.
        const openState = new Map([...panel.querySelectorAll('details[data-cycle]')].map(d => [d.dataset.cycle, d.open]));
        panel.innerHTML = `<div class="gl-head">
                <span class="gl-icon" aria-hidden="true">${ICONS.tree}</span>
                <div class="gl-head-text"><h2>Approval levels</h2><p class="gl-meta">Any active approver can return the request for correction.</p></div>
                <div class="gl-request-status"><span class="gl-meta">Request status</span>${badge(state.requestStatus, 'request')}</div>
            </div>
            <p id="groupedStatus" class="gl-notice" role="status" aria-live="polite"></p>` +
            state.cycles.map(cycle => {
                const key = String(cycle.cycleNo);
                const open = openState.has(key) ? openState.get(key) : cycle === state.cycles.at(-1);
                return `<details class="gl-cycle" data-cycle="${escape(key)}" ${open ? 'open' : ''}>
                    <summary><span class="gl-cycle-title">Cycle ${escape(cycle.cycleNo)}</span><span class="gl-cycle-path">${escape(cycle.pathName)}</span>${badge(cycle.state, 'cycle')}</summary>
                    <div class="gl-levels">${cycle.levels.map(levelBlock).join('')}</div></details>`;
            }).join('');
        panel.querySelectorAll('[data-pct]').forEach(bar => bar.style.width = bar.dataset.pct + '%');
        panel.querySelectorAll('[data-task]').forEach(button => button.addEventListener('click', () => openDecision(button.dataset.task, button.dataset.action)));
        // Grouped actions live here; remove stale legacy task buttons rather than offering two decision flows.
        const taskIds = new Set(state.cycles.flatMap(c => c.levels.flatMap(l => l.members.map(m => m.taskId).filter(Boolean))));
        document.querySelectorAll('[onclick]').forEach(button => {
            if (panel.contains(button)) return;
            const handler = button.getAttribute('onclick') || '';
            if ([...taskIds].some(id => handler.includes(id)) && /approval|approve|reject|return/i.test(handler)) { button.hidden = true; button.style.display = 'none'; }
        });
    }

    // Existing page renderers can recreate task buttons after this panel loads. Capture those clicks too.
    document.addEventListener('click', event => {
        const button = event.target.closest('[onclick]');
        if (!button || !state || panel?.contains(button)) return;
        const handler = button.getAttribute('onclick') || '';
        const member = state.cycles.flatMap(c => c.levels.flatMap(l => l.members)).find(m => m.taskId && handler.includes(m.taskId));
        const action = handler.match(/['"](approve|reject|return)['"]/i)?.[1];
        if (member && action) { event.preventDefault(); event.stopImmediatePropagation(); openDecision(member.taskId, action.toUpperCase()); }
    }, true);

    function openDecision(taskId, action) {
        if (busy) return;
        const level = state.cycles.flatMap(c => c.levels).find(l => l.members.some(m => m.taskId === taskId));
        const dialog = document.createElement('dialog'); dialog.className = 'grouped-dialog';
        const effect = action === 'REJECT' ? 'This rejects the request and closes other pending approval tasks.'
            : action === 'RETURN' ? 'This returns the request for correction and closes other pending approval tasks.'
            : level.policy === 'ANY_ONE' ? 'This resolves the level and closes other pending tasks. The next level will start, or the request will be approved if this is the final level.'
            : 'Your approval will be recorded. The level advances only when everyone has approved.';
        dialog.innerHTML = `<form><h2 id="groupedDecisionTitle">${action === 'RETURN' ? 'Return for correction' : action === 'APPROVE' ? 'Approve' : 'Reject'}</h2>
            <p class="gl-dialog-text">${effect}</p><label for="groupedComment">Comment ${action === 'APPROVE' ? '(optional)' : '(required)'}</label>
            <textarea id="groupedComment" rows="4" maxlength="2000" ${action !== 'APPROVE' ? 'required' : ''}></textarea>
            <p role="alert" class="grouped-error"></p><div class="grouped-actions"><button type="button" class="gl-btn gl-btn-secondary" data-cancel>Cancel</button><button type="submit" class="gl-btn ${(ACTIONS[action] || ACTIONS.APPROVE).cls}">Confirm decision</button></div></form>`;
        dialog.setAttribute('aria-labelledby', 'groupedDecisionTitle'); document.body.append(dialog);
        const close = () => { dialog.close(); dialog.remove(); const opener = panel.querySelector(`[data-task="${CSS.escape(taskId)}"]`); if (opener) opener.focus(); };
        dialog.querySelector('[data-cancel]').onclick = close;
        dialog.addEventListener('cancel', event => { if (busy) event.preventDefault(); else { event.preventDefault(); close(); } });
        dialog.querySelector('form').onsubmit = async event => {
            event.preventDefault(); if (busy) return; busy = true;
            dialog.querySelectorAll('button').forEach(button => button.disabled = true);
            try {
                csrf = await json(endpoint + '/csrf');
                const result = await json(endpoint + '/decisions', {method:'POST', headers:{'Content-Type':'application/json', [csrf.headerName]:csrf.token},
                    body:JSON.stringify({taskId, action, comment:dialog.querySelector('textarea').value.trim()})});
                close(); await load(); panel.querySelector('[role=status]').textContent = result.message;
            } catch (error) {
                dialog.querySelector('[role=alert]').textContent = error.status === 409 ? 'This task has changed. Your decision was not applied. Close this dialog to see the current outcome.' : error.message;
                if (error.status === 409) await load();
            } finally { busy = false; dialog.querySelectorAll('button').forEach(button => button.disabled = false); }
        };
        dialog.showModal(); dialog.querySelector('textarea').focus();
    }

    async function load() {
        clearTimeout(timer);
        try { state = await json(endpoint + '/' + requestId); render(); }
        catch (error) { if (panel) panel.querySelector('[role=status]').textContent = 'Progress could not refresh. Reload this page to retry.'; }
        if (state?.cycles.some(c => c.state === 'ACTIVE') && !document.hidden) timer = setTimeout(load, 15000);
    }
    document.addEventListener('visibilitychange', () => { if (document.hidden) clearTimeout(timer); else load(); });

    const style = document.createElement('style');
    style.textContent = `
.grouped-panel,.grouped-dialog{--gl-ink:#111827;--gl-muted:#64748b;--gl-line:#e5e7eb;--gl-field:#cbd5e1;--gl-surface:#fff;--gl-soft:#f8fafc;--gl-chip:#f1f5f9;
  --gl-blue:#2563eb;--gl-blue-soft:#dbeafe;--gl-blue-ink:#1d4ed8;--gl-green:#16a34a;--gl-green-soft:#dcfce7;--gl-green-ink:#166534;
  --gl-red:#dc2626;--gl-red-soft:#fee2e2;--gl-red-ink:#b91c1c;--gl-amber:#d97706;--gl-amber-soft:#fef3c7;--gl-amber-ink:#92400e}
.dark .grouped-panel,.dark .grouped-dialog{--gl-ink:#f3f4f6;--gl-muted:#9ca3af;--gl-line:#374151;--gl-field:#4b5563;--gl-surface:#1f2937;--gl-soft:#111827;--gl-chip:#374151;
  --gl-blue-soft:rgba(37,99,235,.28);--gl-blue-ink:#93c5fd;--gl-green-soft:rgba(22,163,74,.24);--gl-green-ink:#86efac;
  --gl-red-soft:rgba(220,38,38,.24);--gl-red-ink:#fca5a5;--gl-amber-soft:rgba(217,119,6,.26);--gl-amber-ink:#fcd34d}

.grouped-panel{background:var(--gl-surface);color:var(--gl-ink);border:1px solid var(--gl-line);border-radius:14px;box-shadow:0 12px 28px -24px rgba(15,23,42,.5);overflow:hidden;font-size:.9375rem;line-height:1.5}
.grouped-panel h2,.grouped-panel h3,.grouped-panel p,.grouped-panel ul,.grouped-dialog h2,.grouped-dialog p{margin:0}
.grouped-panel *,.grouped-dialog *{box-sizing:border-box}
.gl-meta{font-size:.8125rem;color:var(--gl-muted)}

.gl-head{display:flex;align-items:center;gap:12px;flex-wrap:wrap;padding:16px 20px;border-bottom:1px solid var(--gl-line)}
.gl-icon{flex:none;width:38px;height:38px;border-radius:12px;display:inline-flex;align-items:center;justify-content:center;background:var(--gl-blue-soft);color:var(--gl-blue-ink)}
.gl-head-text{flex:1 1 220px;min-width:0}
.grouped-panel h2{font-size:1rem;font-weight:700;line-height:1.3}
.gl-request-status{display:flex;align-items:center;gap:8px}
.gl-notice{margin:12px 20px 0;padding:10px 12px;border-radius:10px;background:var(--gl-blue-soft);color:var(--gl-blue-ink);font-size:.875rem;font-weight:600}
.gl-notice:empty{display:none}

.gl-badge{display:inline-block;flex:none;padding:2px 10px;border-radius:999px;font-size:.75rem;font-weight:700;line-height:1.5;white-space:nowrap;background:var(--gl-chip);color:var(--gl-muted)}
.gl-badge.gl-ok{background:var(--gl-green-soft);color:var(--gl-green-ink)}
.gl-badge.gl-bad{background:var(--gl-red-soft);color:var(--gl-red-ink)}
.gl-badge.gl-warn{background:var(--gl-amber-soft);color:var(--gl-amber-ink)}
.gl-badge.gl-info{background:var(--gl-blue-soft);color:var(--gl-blue-ink)}

.gl-cycle + .gl-cycle{border-top:1px solid var(--gl-line)}
.gl-cycle > summary{display:flex;align-items:center;gap:10px;padding:14px 20px;cursor:pointer;list-style:none}
.gl-cycle > summary::-webkit-details-marker{display:none}
.gl-cycle > summary::before{content:"";flex:none;width:8px;height:8px;border-right:2px solid var(--gl-muted);border-bottom:2px solid var(--gl-muted);transform:rotate(-45deg);transition:transform .12s;margin-right:4px}
.gl-cycle[open] > summary::before{transform:rotate(45deg)}
.gl-cycle > summary:hover{background:var(--gl-soft)}
.gl-cycle > summary:focus-visible,.gl-btn:focus-visible,.grouped-dialog textarea:focus-visible{outline:3px solid var(--gl-blue);outline-offset:2px}
.gl-cycle-title{font-weight:700}
.gl-cycle-path{flex:1;min-width:0;color:var(--gl-muted);font-size:.875rem;overflow-wrap:anywhere}
.gl-levels{padding:6px 20px 20px}

.gl-level{position:relative;padding:0 0 22px 48px}
.gl-level:last-child{padding-bottom:0}
.gl-level:not(:last-child)::before{content:"";position:absolute;left:15px;top:38px;bottom:6px;width:2px;background:var(--gl-line)}
.gl-marker{position:absolute;left:0;top:0;width:32px;height:32px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;font-size:.875rem;font-weight:700;background:var(--gl-chip);color:var(--gl-muted);border:1px solid var(--gl-line)}
.gl-marker svg,.gl-icon svg{display:block}
.gl-tone-ok .gl-marker{background:var(--gl-green);border-color:var(--gl-green);color:#fff}
.gl-tone-info .gl-marker{background:var(--gl-blue);border-color:var(--gl-blue);color:#fff}
.gl-tone-bad .gl-marker{background:var(--gl-red);border-color:var(--gl-red);color:#fff}
.gl-tone-warn .gl-marker{background:var(--gl-amber);border-color:var(--gl-amber);color:#fff}
.gl-level-head{display:flex;align-items:center;gap:10px;flex-wrap:wrap;min-height:32px}
.grouped-panel h3{font-size:.9375rem;font-weight:700;line-height:1.3;overflow-wrap:anywhere}
.gl-progress-row{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-top:8px}
.gl-progress{width:160px;max-width:100%;height:6px;border-radius:999px;background:var(--gl-chip);overflow:hidden}
.gl-progress span{display:block;height:100%;border-radius:inherit;background:var(--gl-green)}
.gl-level > .gl-meta + .gl-meta{margin-top:2px}

.gl-members{list-style:none;padding:0;margin-top:12px;border:1px solid var(--gl-line);border-radius:12px;background:var(--gl-soft);overflow:hidden}
.gl-member{padding:12px 14px;overflow-wrap:anywhere}
.gl-member + .gl-member{border-top:1px solid var(--gl-line)}
.gl-member.is-actionable{background:var(--gl-surface);box-shadow:inset 3px 0 0 var(--gl-blue)}
.gl-member-head{display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap}
.gl-name{font-weight:600}
.gl-member .gl-meta{margin-top:2px}
.gl-comment{margin-top:8px;padding-left:10px;border-left:3px solid var(--gl-line);font-size:.875rem}

.grouped-actions{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}
.gl-btn{display:inline-flex;align-items:center;justify-content:center;gap:8px;border:1px solid transparent;border-radius:10px;padding:10px 16px;font-size:14px;font-weight:700;line-height:1.2;cursor:pointer;transition:filter .12s,background-color .12s}
.gl-btn:hover:not(:disabled){filter:brightness(1.08)}
.gl-btn:disabled{opacity:.6;cursor:not-allowed}
.gl-btn-approve{background:#16a34a;color:#fff}
.gl-btn-reject{background:#fff;color:#b91c1c;border-color:#fecaca}
.gl-btn-reject:hover:not(:disabled){background:#fef2f2;filter:none}
.gl-btn-return{background:#d97706;color:#fff}
.gl-btn-secondary{background:var(--gl-surface);color:var(--gl-ink);border-color:var(--gl-field)}

.grouped-dialog{max-width:480px;width:calc(100% - 32px);margin:auto;padding:0;border:1px solid var(--gl-line);border-radius:16px;background:var(--gl-surface);color:var(--gl-ink);box-shadow:0 24px 80px -28px rgba(15,23,42,.7)}
.grouped-dialog::backdrop{background:rgba(15,23,42,.55)}
.grouped-dialog form{padding:20px}
.grouped-dialog h2{font-size:1.0625rem;font-weight:700;line-height:1.3}
.grouped-dialog .gl-dialog-text{margin-top:6px;color:var(--gl-muted);font-size:.875rem}
.grouped-dialog label{display:block;margin:16px 0 6px;font-size:.875rem;font-weight:600}
.grouped-dialog textarea{display:block;width:100%;padding:10px 12px;border:1px solid var(--gl-field);border-radius:10px;background:var(--gl-surface);color:var(--gl-ink);font:inherit;font-size:.875rem;resize:vertical}
.grouped-error{margin-top:8px;color:var(--gl-red-ink);font-size:.875rem;font-weight:600}
.grouped-error:empty{display:none}
.grouped-dialog .grouped-actions{justify-content:flex-end;margin-top:16px}

@media (max-width:560px){
  .gl-head,.gl-cycle > summary{padding-left:16px;padding-right:16px}
  .gl-levels{padding:6px 16px 16px}
  .gl-notice{margin:12px 16px 0}
  .gl-level{padding-left:44px}
  .grouped-panel .grouped-actions .gl-btn{flex:1 1 100%}
}
@media (prefers-reduced-motion:reduce){.gl-btn,.gl-cycle > summary::before{transition:none}}`;
    document.head.append(style);
    // Wait for existing page scripts so their first render cannot immediately recreate duplicate action buttons.
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => setTimeout(load, 500)); else setTimeout(load, 500);
})();