(() => {
    'use strict';
    const root = document.getElementById('groupedConfiguration');
    const api = '/api/grouped-approval-paths';
    const byId = id => document.getElementById(id);
    const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    const label = value => { const s = String(value ?? '').replaceAll('_', ' ').toLowerCase(); return s.charAt(0).toUpperCase() + s.slice(1); };
    let paths = [], people = [], types = [], draftId = null, draftVersion = null, dirty = false, enabled = false, pending = false, nextId = 0;
    async function json(url, options = {}) {
        if (options.method && options.method !== 'GET') {
            const token = await fetch('/api/requests/grouped-approvals/csrf').then(r => r.json());
            options.headers = {...options.headers, [token.headerName]:token.token};
        }
        const response = await fetch(url, options);
        if (!response.ok) {
            let data; try { data = await response.json(); } catch (_) {}
            throw new Error(data?.detail || data?.message || data?.error || 'Operation failed. Refresh and try again.');
        }
        return response.headers.get('content-type')?.includes('application/json') ? response.json() : null;
    }
    function error(message) { byId('gcError').textContent = message; byId('gcError').focus(); }
    async function run(operation) {
        if (pending) return; pending = true; byId('gcError').textContent = '';
        root.querySelectorAll('button').forEach(b => b.disabled = true);
        try { await operation(); } catch (e) { error(e.message); }
        finally { pending = false; root.querySelectorAll('button').forEach(b => b.disabled = !enabled); }
    }
    // In-page confirmation modal. Resolves true on confirm, false on cancel / Esc / backdrop click.
    function ask({title, message, detail = '', confirmText = 'Confirm', danger = false}) {
        return new Promise(resolve => {
            const opener = document.activeElement;
            const dialog = document.createElement('dialog');
            dialog.className = 'gc-modal';
            dialog.setAttribute('aria-labelledby', 'gcModalTitle');
            dialog.innerHTML = `<div class="gc-modal-body"><h2 id="gcModalTitle">${esc(title)}</h2><p>${esc(message)}</p>
                ${detail ? `<pre class="gc-preview">${esc(detail)}</pre>` : ''}
                <div class="gc-modal-actions"><button type="button" class="gc-btn" data-cancel>Cancel</button>
                <button type="button" class="gc-btn ${danger ? 'gc-btn-danger-solid' : 'gc-btn-primary'}" data-confirm>${esc(confirmText)}</button></div></div>`;
            let result = false;
            const finish = value => { result = value; dialog.close(); };
            dialog.querySelector('[data-cancel]').onclick = () => finish(false);
            dialog.querySelector('[data-confirm]').onclick = () => finish(true);
            dialog.addEventListener('click', event => { if (event.target === dialog) finish(false); });
            dialog.addEventListener('close', () => { dialog.remove(); if (opener && opener.focus) opener.focus(); resolve(result); });
            root.append(dialog);
            dialog.showModal();
            dialog.querySelector(danger ? '[data-cancel]' : '[data-confirm]').focus();
        });
    }
    const discardChanges = () => ask({title:'Discard unsaved changes?', message:"Your changes to this draft haven't been saved.", confirmText:'Discard changes', danger:true});

    function reset() {
        draftId = null; draftVersion = null; byId('gcForm').reset(); byId('gcLevels').replaceChildren();
        byId('gcFormTitle').textContent = 'New draft'; addLevel(); dirty = false;
    }
    function addLevel(value = {name:'', policy:'ALL', employeeIds:[]}) {
        const card = document.createElement('fieldset'); card.className = 'gc-level'; card.dataset.level = '';
        const id = ++nextId;
        card.innerHTML = `<legend>Approval level</legend>
            <div class="gc-row">
                <div class="gc-field"><label for="gcLevelName${id}">Level name</label><input id="gcLevelName${id}" data-name required maxlength="100" value="${esc(value.name)}"></div>
                <div class="gc-field"><label for="gcPolicy${id}">Decision policy</label><select data-policy id="gcPolicy${id}">
                    <option value="ALL">Everyone must approve; one rejection rejects</option><option value="ANY_ONE">First decision resolves the level</option></select></div>
            </div>
            <div class="gc-help"><p data-policy-help></p><p>Any active approver can return the request for correction.</p></div>
            <div class="gc-field"><label for="gcSearch${id}">Find employees by name or number</label><input id="gcSearch${id}" type="search" data-search placeholder="Search approvers"></div>
            <div class="gc-member-list" role="group" aria-label="Select approvers">${people.map(p => `<label class="gc-check" data-employee-label><input type="checkbox" value="${p.value}" ${value.employeeIds.includes(p.value) ? 'checked' : ''}> <span>${esc(p.label)}</span></label>`).join('')}</div>
            <p class="gc-count" data-count aria-live="polite"></p>
            <div class="gc-level-actions"><button type="button" class="gc-btn gc-btn-sm" data-up>Move up</button><button type="button" class="gc-btn gc-btn-sm" data-down>Move down</button><button type="button" class="gc-btn gc-btn-sm gc-btn-danger gc-push" data-remove>Remove level</button></div>`;
        card.querySelector('[data-policy]').value = value.policy;
        const update = () => {
            card.querySelector('[data-policy-help]').textContent = card.querySelector('[data-policy]').value === 'ALL'
                ? 'Every selected employee must approve. One rejection stops the request.' : 'The first committed approval advances; the first committed rejection rejects. Other pending tasks close.';
            card.querySelector('[data-count]').textContent = card.querySelectorAll('input[type=checkbox]:checked').length + ' approver(s) selected';
        };
        card.addEventListener('change', () => { dirty = true; update(); });
        card.querySelector('[data-search]').oninput = event => {
            const query = event.target.value.toLowerCase();
            card.querySelectorAll('[data-employee-label]').forEach(label => label.hidden = !label.textContent.toLowerCase().includes(query) && !label.querySelector('input').checked);
        };
        card.querySelector('[data-up]').onclick = () => { if (card.previousElementSibling) { card.before(card.previousElementSibling); dirty = true; renumber(); card.querySelector('[data-up]').focus(); } };
        card.querySelector('[data-down]').onclick = () => { if (card.nextElementSibling) { card.after(card.nextElementSibling); dirty = true; renumber(); card.querySelector('[data-down]').focus(); } };
        card.querySelector('[data-remove]').onclick = async () => { if (await ask({title:'Remove this level?', message:'The level and its selected approvers will be removed from this draft.', confirmText:'Remove level', danger:true})) { card.remove(); dirty = true; renumber(); byId('gcAddLevel').focus(); } };
        byId('gcLevels').append(card); update(); renumber();
    }
    function renumber() { [...byId('gcLevels').children].forEach((card, i) => card.querySelector('legend').textContent = 'Level ' + (i + 1)); }
    function payload() {
        const seen = new Set();
        const levels = [...byId('gcLevels').children].map(card => {
            const employeeIds = [...card.querySelectorAll('input[type=checkbox]:checked')].map(c => Number(c.value));
            const name = card.querySelector('[data-name]').value.trim();
            if (!name || !employeeIds.length) throw new Error('Every level needs a name and at least one approver.');
            employeeIds.forEach(id => { if (seen.has(id)) throw new Error('An employee cannot appear more than once in a path.'); seen.add(id); });
            return {name, policy:card.querySelector('[data-policy]').value, employeeIds};
        });
        if (!levels.length || levels.length > 20) throw new Error('Add between 1 and 20 levels.');
        if (!byId('gcName').value.trim()) throw new Error('A path name is required.');
        return {name:byId('gcName').value.trim(), description:byId('gcDescription').value.trim(), levels};
    }
    function previewText(value) {
        return value.name + '\n\n' + value.levels.map((l,i) =>
            `Level ${i+1}: ${l.name}\n${l.policy === 'ALL' ? 'Everyone must approve; one rejection rejects.' : 'The first decision resolves the level.'}\n` +
            l.employeeIds.map(id => people.find(p => p.value === id)?.label || `Employee #${id}`).join(', ')).join('\n\n');
    }
    const preview = value => { byId('gcPreviewText').textContent = previewText(value); };
    function versionRow(path) {
        const stateClass = 'gc-badge-' + String(path.state).toLowerCase();
        const actions = path.state === 'DRAFT'
            ? `<button type="button" class="gc-btn gc-btn-sm" data-edit="${path.id}">Edit draft</button><button type="button" class="gc-btn gc-btn-sm gc-btn-primary" data-publish="${path.id}">Preview and publish</button>`
            : `<button type="button" class="gc-btn gc-btn-sm" data-clone="${path.id}">Clone as new draft</button>`;
        return `<article class="gc-version"><div class="gc-version-main"><h3>${esc(path.name)}</h3>
            <p class="gc-meta"><span class="gc-badge ${esc(stateClass)}">${esc(label(path.state))}</span><span class="gc-badge ${path.enabled ? 'gc-badge-enabled' : ''}">${path.enabled ? 'Enabled' : 'Disabled'}</span><span>Revision ${esc(path.revision)}</span><span>${path.levels.length} level${path.levels.length === 1 ? '' : 's'}</span></p></div>
            <div class="gc-actions">${actions}<button type="button" class="gc-btn gc-btn-sm" data-toggle="${path.id}">${path.enabled ? 'Disable' : 'Enable'}</button></div></article>`;
    }
    async function load() {
        const [configuration, employees, requestTypes] = await Promise.all([json(api), json('/api/approval-paths/approvers'), json('/api/requests/types')]);
        paths = configuration.paths; enabled = configuration.enabled; people = employees; types = requestTypes;
        byId('gcStatus').textContent = enabled ? 'Grouped approval is available for new submissions.' : 'New grouped submissions/configuration are disabled. Existing grouped requests can still be decided.';
        byId('gcPaths').innerHTML = paths.length ? paths.map(versionRow).join('') : '<p class="gc-empty">No grouped paths yet. Create a draft below.</p>';
        byId('gcType').innerHTML = types.filter(t => t.requiresApproval).map(t => `<option value="${esc(t.code)}">${esc(t.name)}${t.groupedApprovalPathVersionId ? ' (grouped path configured)' : ''}</option>`).join('');
        byId('gcVersion').innerHTML = '<option value="">Select a published version</option>' + paths.filter(p => p.state === 'PUBLISHED' && p.enabled).map(p => `<option value="${p.id}">${esc(p.name)} — revision ${p.revision}</option>`).join('');
        byId('gcRequester').innerHTML = people.map(p => `<option value="${p.value}">${esc(p.label)}</option>`).join('');
        root.querySelectorAll('button').forEach(b => b.disabled = !enabled);
    }
    byId('gcPaths').onclick = event => run(async () => {
        const button = event.target.closest('button'); if (!button) return;
        const id = Number(button.dataset.edit || button.dataset.publish || button.dataset.clone || button.dataset.toggle);
        const path = paths.find(p => p.id === id); if (!path) return;
        if (button.dataset.edit) {
            if (dirty && !(await discardChanges())) return;
            draftId = path.id; draftVersion = path.version; byId('gcName').value = path.name; byId('gcDescription').value = path.description || '';
            byId('gcFormTitle').textContent = 'Edit draft — revision ' + path.revision;
            byId('gcLevels').replaceChildren(); path.levels.forEach(addLevel); dirty = false; byId('gcName').focus();
        } else if (button.dataset.publish) {
            if (await ask({title:'Publish this version?', message:'Publishing freezes this version. Existing requests will not change.', detail:previewText(path), confirmText:'Publish'})) {
                await json(`${api}/${id}/publish?version=${path.version}`, {method:'POST'}); await load();
                if (draftId === id) reset();
            }
        } else if (button.dataset.clone) { await json(`${api}/${id}/clone`, {method:'POST'}); await load(); }
        else { await json(`${api}/${id}/enabled?enabled=${!path.enabled}`, {method:'POST'}); await load(); }
    });
    byId('gcNew').onclick = async () => { if (!dirty || await discardChanges()) { reset(); byId('gcName').focus(); } };
    byId('gcAddLevel').onclick = () => { addLevel(); dirty = true; };
    byId('gcPreview').onclick = () => { try { preview(payload()); } catch(e) { error(e.message); } };
    byId('gcForm').addEventListener('input', () => dirty = true);
    byId('gcForm').onsubmit = event => { event.preventDefault(); run(async () => {
        const saved = await json(draftId ? `${api}/${draftId}?version=${draftVersion}` : api, {method:draftId ? 'PUT' : 'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(payload())});
        draftId = saved.id; dirty = false; await load(); draftVersion = paths.find(p => p.id === draftId).version;
        byId('gcStatus').textContent = 'Draft saved. Preview and publish it when ready.';
    }); };
    byId('gcBind').onclick = () => run(async () => {
        const id = Number(byId('gcVersion').value); if (!id) throw new Error('Select a published grouped version.');
        if (!(await ask({title:'Apply to request type?', message:`This grouped version will be used for new ${byId('gcType').selectedOptions[0].textContent} requests. Existing submitted requests keep their route.`, confirmText:'Apply'}))) return;
        await json(`${api}/request-types/${byId('gcType').value}`, {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({pathVersionId:id})});
        await load(); byId('gcStatus').textContent = 'Request type updated for new submissions.';
    });
    byId('gcEffectivePreview').onclick = () => run(async () => {
        const id = Number(byId('gcVersion').value); if (!id) throw new Error('Select a published grouped version.');
        const levels = await json(`${api}/${id}/preview?requesterId=${byId('gcRequester').value}`);
        byId('gcEffectivePreviewText').textContent = levels.map((l,i) => `Level ${i+1}: ${l.name} (${l.policy})\n` + l.employeeIds.map(id => people.find(p => p.value === id)?.label || `Employee #${id}`).join(', ')).join('\n\n');
    });
    byId('gcDiagnostics').onclick = () => run(async () => {
        const result = await json(api + '/diagnostics');
        byId('gcDiagnosticsText').textContent = JSON.stringify(result,null,2);
    });
    window.addEventListener('beforeunload', event => { if (dirty) { event.preventDefault(); event.returnValue = ''; } });
    run(async () => { await load(); reset(); });
})();