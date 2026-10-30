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

    function policyText(level) {
        return level.policy === 'ALL' ? 'Everyone must approve; one rejection rejects the request.'
            : 'The first decision resolves this level; other pending tasks close.';
    }
    function render() {
        if (!state.cycles.length) return;
        if (!panel) {
            panel = document.createElement('section'); panel.id = 'groupedApprovalPanel';
            panel.className = 'grouped-panel'; panel.setAttribute('aria-label', 'Grouped approval progress');
            const host = document.querySelector('main') || document.querySelector('[layout\\:fragment]') || document.body;
            host.prepend(panel);
        }
        panel.innerHTML = '<h2>Approval levels</h2><p>Current request status: <strong>' + escape(state.requestStatus.replaceAll('_',' ')) + '</strong></p><p id="groupedStatus" role="status" aria-live="polite"></p>' + state.cycles.map(cycle =>
            `<details ${cycle === state.cycles.at(-1) ? 'open' : ''}><summary>Cycle ${cycle.cycleNo}: ${escape(cycle.pathName)} — ${escape(cycle.state.replaceAll('_',' '))}</summary>` +
            cycle.levels.map(level => `<section class="grouped-level"><h3>Level ${level.ordinal}: ${escape(level.name)} — ${escape(level.state.replaceAll('_',' '))}</h3>
                <p>${policyText(level)} Any active approver can return the request for correction.</p>
                <p>${level.policy === 'ALL' ? `${level.approvedCount} of ${level.memberCount} approvals received` : level.state === 'ACTIVE' ? 'Waiting for the first decision' : escape(level.state.replaceAll('_',' '))}</p>
                <ul>${level.members.map(member => `<li><strong>${escape(member.name)}</strong>: ${escape(member.state.replaceAll('_',' '))}
                    ${member.decidedAt ? `<span> (${escape(member.decidedAt.replace('T',' '))})</span>` : ''}
                    ${member.comment ? `<p>${escape(member.comment)}</p>` : ''}
                    ${member.supersessionReason ? `<p>${escape(member.supersessionReason)}</p>` : ''}
                    ${member.taskId ? `<div class="grouped-actions">${['APPROVE','REJECT','RETURN'].map(action => `<button type="button" data-task="${escape(member.taskId)}" data-action="${action}">${action === 'RETURN' ? 'Return for correction' : action === 'APPROVE' ? 'Approve' : 'Reject'}</button>`).join('')}</div>` : ''}
                </li>`).join('')}</ul></section>`).join('') + '</details>').join('');
        panel.querySelectorAll('[data-task]').forEach(button => button.addEventListener('click', () => openDecision(button.dataset.task, button.dataset.action)));
        // Grouped actions live here; remove stale legacy task buttons rather than offering two decision flows.
        const taskIds = new Set(state.cycles.flatMap(c => c.levels.flatMap(l => l.members.map(m => m.taskId).filter(Boolean))));
        document.querySelectorAll('[onclick]').forEach(button => {
            if (panel.contains(button)) return;
            const handler = button.getAttribute('onclick') || '';
            if ([...taskIds].some(id => handler.includes(id)) && /approval|approve|reject|return/i.test(handler)) button.hidden = true;
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
            <p>${effect}</p><label for="groupedComment">Comment ${action === 'APPROVE' ? '(optional)' : '(required)'}</label>
            <textarea id="groupedComment" rows="4" maxlength="2000" ${action !== 'APPROVE' ? 'required' : ''}></textarea>
            <p role="alert" class="grouped-error"></p><div class="grouped-actions"><button type="button" data-cancel>Cancel</button><button type="submit">Confirm decision</button></div></form>`;
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
    style.textContent = '.grouped-panel,.grouped-dialog{background:#fff;color:#172033;border:1px solid #cbd5e1;border-radius:12px;padding:20px;margin:12px 0}.grouped-panel h2,.grouped-dialog h2{font-size:1.2rem;font-weight:700}.grouped-panel h3{font-weight:700}.grouped-level{padding:12px 0;border-top:1px solid #cbd5e1}.grouped-level li{padding:8px 0;overflow-wrap:anywhere}.grouped-actions{display:flex;flex-wrap:wrap;gap:8px;margin-top:10px}.grouped-actions button{border:1px solid #475569;border-radius:6px;padding:8px 12px;background:#eff6ff;color:#172033}.grouped-actions button:focus-visible{outline:3px solid #2563eb;outline-offset:2px}.grouped-actions button:disabled{opacity:.6}.grouped-dialog{max-width:560px;width:calc(100% - 32px);margin:auto}.grouped-dialog::backdrop{background:#0008}.grouped-dialog textarea{display:block;width:100%;border:1px solid #64748b;padding:8px}.grouped-error{color:#991b1b}.grouped-panel summary{cursor:pointer;padding:8px 0;font-weight:600}.dark .grouped-panel{background:#1f2937;color:#f1f5f9}';
    document.head.append(style);
    // Wait for existing page scripts so their first render cannot immediately recreate duplicate action buttons.
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => setTimeout(load, 500)); else setTimeout(load, 500);
})();
