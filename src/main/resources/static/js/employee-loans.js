/*
 * Employee loan screens (Step 12).
 * Used by templates/loan/employee-main.html and templates/loan/employee-detail.html.
 *
 *   EmployeeLoans.initMain();    // /employee/loans
 *   EmployeeLoans.initDetail();  // /employee/loans/{id}
 *
 * All access rules live in the services; this file only renders what the API returns and
 * calls the endpoints. Buttons use data-action attributes (event delegation), so there are
 * no inline onclick handlers.
 */
const EmployeeLoans = (function () {
    'use strict';

    const API = '/api/employee/loans';
    const PAGE = '/employee/loans';

    // Statuses in which the employee may still pull an application back / edit it.
    // The service is the real gate; these only decide which buttons are shown in the list.
    const EDITABLE_STATUSES = ['DRAFT', 'RETURNED_FOR_CORRECTION'];
    const CANCELLABLE_STATUSES = ['SUBMITTED', 'PENDING_HR_APPROVAL', 'PENDING_CUSTOM_APPROVAL',
        'PENDING_FINANCE_APPROVAL', 'HR_APPROVED', 'RETURNED_FOR_CORRECTION'];
    const LIVE_LOAN_STATUSES = ['ACTIVE', 'COMPLETED'];

    // ------------------------------------------------------------------ helpers

    function esc(value) {
        return String(value == null ? '' : value)
            .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;').replaceAll("'", '&#39;');
    }

    function money(value) {
        if (value == null || value === '') return '-';
        return Number(value).toLocaleString('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2});
    }

    function date(value) {
        if (!value) return '-';
        const d = new Date(value.length <= 10 ? value + 'T00:00:00' : value);
        return isNaN(d) ? esc(value) : d.toLocaleDateString('en-GB', {day: '2-digit', month: 'short', year: 'numeric'});
    }

    function dateTime(value) {
        if (!value) return '-';
        const d = new Date(value);
        return isNaN(d) ? esc(value)
            : d.toLocaleDateString('en-GB', {day: '2-digit', month: 'short', year: 'numeric'})
            + ', ' + d.toLocaleTimeString('en-GB', {hour: '2-digit', minute: '2-digit'});
    }

    function monthLabel(value) {
        if (!value) return '-';
        const d = new Date(value.length <= 10 ? value + 'T00:00:00' : value);
        return isNaN(d) ? esc(value) : d.toLocaleDateString('en-GB', {month: 'short', year: 'numeric'});
    }

    function pretty(value) {
        return esc(String(value || '').replaceAll('_', ' ').toLowerCase().replace(/^\w/, c => c.toUpperCase()));
    }

    const STATUS_LABELS = {
        DRAFT: 'Draft',
        SUBMITTED: 'Submitted',
        PENDING_HR_APPROVAL: 'Pending HR',
        PENDING_CUSTOM_APPROVAL: 'Pending approver',
        PENDING_FINANCE_APPROVAL: 'Pending Finance',
        RETURNED_FOR_CORRECTION: 'Returned',
        HR_APPROVED: 'HR approved',
        FINANCE_APPROVED: 'Finance approved',
        CUSTOM_APPROVED: 'Approved',
        ACTIVE: 'Active',
        REJECTED: 'Rejected',
        CANCELLED: 'Cancelled',
        COMPLETED: 'Completed',
        CLOSED: 'Closed'
    };

    function statusBadge(status) {
        const cls = {
            ACTIVE: 'loan-badge-success', COMPLETED: 'loan-badge-success',
            REJECTED: 'loan-badge-error',
            RETURNED_FOR_CORRECTION: 'loan-badge-warning',
            PENDING_HR_APPROVAL: 'loan-badge-warning', PENDING_CUSTOM_APPROVAL: 'loan-badge-warning',
            PENDING_FINANCE_APPROVAL: 'loan-badge-warning',
            SUBMITTED: 'loan-badge-info', HR_APPROVED: 'loan-badge-info',
            FINANCE_APPROVED: 'loan-badge-info', CUSTOM_APPROVED: 'loan-badge-info'
        }[status] || 'loan-badge-neutral';
        return `<span class="loan-badge ${cls}">${esc(STATUS_LABELS[status] || String(status || '').replaceAll('_', ' '))}</span>`;
    }

    function routeBadge(type, label) {
        const custom = type === 'CUSTOM';
        return `<span class="loan-badge ${custom ? 'loan-badge-purple' : 'loan-badge-blue'}" title="${esc(label || '')}">`
            + `<span class="material-icons-round" style="font-size:13px;margin-right:4px">${custom ? 'alt_route' : 'account_tree'}</span>`
            + `${custom ? 'Custom path' : 'Role-based'}</span>`;
    }

    function repaymentBadge(status) {
        const cls = {PAID: 'loan-badge-success', PARTIALLY_PAID: 'loan-badge-info', MISSED: 'loan-badge-error'}[status]
            || 'loan-badge-neutral';
        return `<span class="loan-badge ${cls}">${pretty(status)}</span>`;
    }

    function decisionBadge(step) {
        if (step.decision === 'APPROVE') return '<span class="loan-badge loan-badge-success">Approved</span>';
        if (step.decision === 'REJECT') return '<span class="loan-badge loan-badge-error">Rejected</span>';
        if (step.decision === 'RETURN') return '<span class="loan-badge loan-badge-warning">Returned</span>';
        if (step.current) return '<span class="loan-badge loan-badge-info">Waiting</span>';
        return '<span class="loan-badge loan-badge-neutral">Not started</span>';
    }

    function empty(text) {
        return `<div class="loan-empty">${esc(text)}</div>`;
    }

    function kv(label, value) {
        return `<div class="loan-kv-row"><dt>${esc(label)}</dt><dd>${value == null || value === '' ? '-' : value}</dd></div>`;
    }

    function $(id) {
        return document.getElementById(id);
    }

    function errorMessage(text) {
        if (!text) return 'Request failed';
        try {
            const parsed = JSON.parse(text);
            return parsed.message || parsed.detail || parsed.error || text;
        } catch (e) {
            // The server's HTML error page: show only its detail line, never the page source.
            if (/^\s*<(!doctype|html)/i.test(text)) {
                const detail = new DOMParser().parseFromString(text, 'text/html').querySelector('.error-message');
                const message = detail && detail.textContent.trim();
                return message || 'Something went wrong. Please try again.';
            }
            return text;
        }
    }

    async function json(url, options) {
        const response = await fetch(url, options);
        if (!response.ok) throw new Error(errorMessage(await response.text()));
        const contentType = response.headers.get('content-type') || '';
        return contentType.includes('application/json') ? response.json() : null;
    }

    function postJson(url, body) {
        return json(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: body === undefined ? undefined : JSON.stringify(body)
        });
    }

    const ATTACHMENT_REQUIRED_MESSAGE =
        'This loan requires a supporting document. Upload at least one document before you submit.';

    /**
     * Submits an application and verifies it really left the editable state.
     * Throws (instead of silently "succeeding") if the server answered OK but the status is unchanged.
     */
    async function submitRequest(id) {
        const result = await postJson(`${API}/${id}/submit`);
        if (result && result.status && EDITABLE_STATUSES.includes(result.status)) {
            throw new Error('The application could not be submitted. It is still ' + pretty(result.status).toLowerCase() + '.');
        }
        return result;
    }

    /** Loads the edit view of an application and reports whether a required document is still missing. */
    async function missingRequiredAttachment(id) {
        const e = await json(`${API}/${id}/edit`);
        const required = e.attachmentRequired === true || !!(e.loanProduct && e.loanProduct.requiresAttachment);
        return required && !(e.attachments || []).length;
    }

    // ------------------------------------------------------------------ shared shell (toast, confirm, comment modal)

    function ensureShell() {
        if ($('loanToastContainer')) return;
        document.body.insertAdjacentHTML('beforeend', `
            <div id="loanToastContainer" class="fixed bottom-4 right-4 z-[80] flex flex-col gap-2"></div>

            <div id="loanConfirmModal" class="fixed inset-0 z-[70] hidden">
                <div class="absolute inset-0 bg-black/50"></div>
                <div class="absolute inset-0 flex items-center justify-center p-4">
                    <div class="loan-modal-panel w-full max-w-md p-6">
                        <h3 id="loanConfirmTitle" class="text-lg font-bold">Confirm</h3>
                        <p id="loanConfirmMessage" class="mt-2 text-sm text-gray-600 dark:text-gray-300"></p>
                        <div class="mt-6 flex justify-end gap-2">
                            <button type="button" id="loanConfirmCancel" class="loan-secondary-btn">Cancel</button>
                            <button type="button" id="loanConfirmOk" class="loan-primary-btn">Confirm</button>
                        </div>
                    </div>
                </div>
            </div>

            <div id="loanCommentModal" class="fixed inset-0 z-[70] hidden">
                <div class="absolute inset-0 bg-black/50"></div>
                <div class="absolute inset-0 flex items-center justify-center p-4">
                    <div class="loan-modal-panel w-full max-w-lg p-6">
                        <h3 id="loanCommentTitle" class="text-lg font-bold">Comment</h3>
                        <p id="loanCommentHint" class="mt-1 text-sm text-gray-500"></p>
                        <textarea id="loanCommentText" rows="4" maxlength="2000" class="loan-control mt-4"
                                  placeholder="Comment"></textarea>
                        <p id="loanCommentError" class="mt-2 hidden text-sm text-red-600"></p>
                        <div class="mt-5 flex justify-end gap-2">
                            <button type="button" id="loanCommentCancel" class="loan-secondary-btn">Cancel</button>
                            <button type="button" id="loanCommentOk" class="loan-primary-btn">Submit</button>
                        </div>
                    </div>
                </div>
            </div>`);
    }

    function toast(message, type) {
        ensureShell();
        const styles = {error: 'bg-red-600 text-white', success: 'bg-green-600 text-white', info: 'bg-gray-800 text-white'};
        const icons = {success: 'check_circle', error: 'error', info: 'info'};
        const kind = type || 'info';
        const el = document.createElement('div');
        el.className = `loan-toast ${styles[kind] || styles.info}`;
        el.innerHTML = `<span class="material-icons-round text-base">${icons[kind] || 'info'}</span><span>${esc(message)}</span>`;
        $('loanToastContainer').appendChild(el);
        setTimeout(() => el.remove(), 4500);
    }

    const toastError = e => toast((e && e.message) || 'Request failed', 'error');

    function confirmDialog({title = 'Confirm', message = '', confirmLabel = 'Confirm', danger = false} = {}) {
        ensureShell();
        return new Promise(resolve => {
            const modal = $('loanConfirmModal');
            const ok = $('loanConfirmOk');
            const cancel = $('loanConfirmCancel');
            $('loanConfirmTitle').textContent = title;
            $('loanConfirmMessage').textContent = message;
            ok.textContent = confirmLabel;
            ok.className = danger ? 'loan-danger-solid-btn' : 'loan-primary-btn';
            modal.classList.remove('hidden');

            function done(result) {
                modal.classList.add('hidden');
                ok.removeEventListener('click', onOk);
                cancel.removeEventListener('click', onCancel);
                resolve(result);
            }

            function onOk() { done(true); }
            function onCancel() { done(false); }

            ok.addEventListener('click', onOk);
            cancel.addEventListener('click', onCancel);
        });
    }

    /** Resolves with the comment string, or null when cancelled. */
    function commentDialog({title, hint, required, confirmLabel, danger}) {
        ensureShell();
        return new Promise(resolve => {
            const modal = $('loanCommentModal');
            const ok = $('loanCommentOk');
            const cancel = $('loanCommentCancel');
            const text = $('loanCommentText');
            const err = $('loanCommentError');
            $('loanCommentTitle').textContent = title;
            $('loanCommentHint').textContent = hint || '';
            text.value = '';
            text.placeholder = required ? 'Comment (required)' : 'Comment (optional)';
            err.classList.add('hidden');
            ok.textContent = confirmLabel || 'Submit';
            ok.className = danger ? 'loan-danger-solid-btn' : 'loan-primary-btn';
            modal.classList.remove('hidden');
            text.focus();

            function done(result) {
                modal.classList.add('hidden');
                ok.removeEventListener('click', onOk);
                cancel.removeEventListener('click', onCancel);
                resolve(result);
            }

            function onOk() {
                const value = text.value.trim();
                if (required && !value) {
                    err.textContent = 'A comment is required for this action.';
                    err.classList.remove('hidden');
                    return;
                }
                done(value);
            }

            function onCancel() { done(null); }

            ok.addEventListener('click', onOk);
            cancel.addEventListener('click', onCancel);
        });
    }

    // ------------------------------------------------------------------ shared: custom-approver task actions

    const TASK_ACTIONS = {
        approve: {
            title: 'Approve loan application', hint: 'Add an optional note for the audit trail.',
            required: false, label: 'Approve', danger: false, done: 'Application approved.'
        },
        reject: {
            title: 'Reject loan application', hint: 'The employee will see your comment. This ends the application.',
            required: true, label: 'Reject', danger: true, done: 'Application rejected.'
        },
        return: {
            title: 'Return for correction', hint: 'Tell the employee what to fix before resubmitting.',
            required: true, label: 'Return', danger: false, done: 'Application returned to the employee.'
        }
    };

    /** Runs approve / reject / return for a custom-approver task. Returns true when the action succeeded. */
    async function runTaskAction(action, taskId, preset) {
        const cfg = TASK_ACTIONS[action];
        const comment = preset !== undefined ? preset : await commentDialog({
            title: cfg.title, hint: cfg.hint, required: cfg.required, confirmLabel: cfg.label, danger: cfg.danger
        });
        if (comment === null) return false;
        if (cfg.required && !comment) {
            toast('A comment is required for this action.', 'error');
            return false;
        }
        try {
            await postJson(`${API}/approval-tasks/${encodeURIComponent(taskId)}/${action}`, {comment: comment || null});
            toast(cfg.done, 'success');
            return true;
        } catch (e) {
            toastError(e);
            return false;
        }
    }

    // ------------------------------------------------------------------ shared: attachments

    function attachmentRow(a, appId, showDelete) {
        const base = `${API}/${appId}/attachments/${a.id}`;
        return `<li class="loan-attachment">
            <span class="material-icons-round text-gray-400">description</span>
            <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-semibold" title="${esc(a.originalFilename)}">${esc(a.originalFilename)}</p>
                <p class="text-xs text-gray-500">${pretty(a.attachmentType)} · ${esc(fileSize(a.fileSize))}
                    · ${esc(a.uploadedByName || '')} · ${dateTime(a.createdAt)}</p>
            </div>
            <a href="${base}/view" target="_blank" rel="noopener" class="loan-icon-btn" title="View">
                <span class="material-icons-round text-base">visibility</span></a>
            <a href="${base}" class="loan-icon-btn" title="Download">
                <span class="material-icons-round text-base">download</span></a>
            ${showDelete && a.deletable ? `<button type="button" class="loan-icon-btn text-red-600" title="Delete"
                data-action="delete-attachment" data-id="${a.id}">
                <span class="material-icons-round text-base">delete</span></button>` : ''}
        </li>`;
    }

    function fileSize(bytes) {
        if (bytes == null) return '';
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / 1048576).toFixed(1) + ' MB';
    }

    async function uploadAttachment(appId, fileInput, typeSelect) {
        const file = fileInput.files && fileInput.files[0];
        if (!file) {
            toast('Choose a file to upload.', 'error');
            return false;
        }
        const form = new FormData();
        form.append('file', file);
        try {
            await json(`${API}/${appId}/attachments?attachmentType=${encodeURIComponent(typeSelect.value)}`,
                {method: 'POST', body: form});
            fileInput.value = '';
            toast('Attachment uploaded.', 'success');
            return true;
        } catch (e) {
            toastError(e);
            return false;
        }
    }

    async function removeAttachment(appId, attachmentId) {
        const ok = await confirmDialog({
            title: 'Delete attachment', message: 'This file will be removed from the application.',
            confirmLabel: 'Delete', danger: true
        });
        if (!ok) return false;
        try {
            await json(`${API}/${appId}/attachments/${attachmentId}`, {method: 'DELETE'});
            toast('Attachment deleted.', 'success');
            return true;
        } catch (e) {
            toastError(e);
            return false;
        }
    }

    // ------------------------------------------------------------------ shared: schedule table

    function scheduleTable(lines, withPayment) {
        if (!lines || !lines.length) return empty('No schedule available yet.');
        return `<div class="overflow-x-auto"><table class="w-full min-w-[560px] text-sm">
            <thead class="loan-table-head"><tr>
                <th class="px-3 py-2 text-left">#</th>
                <th class="px-3 py-2 text-left">Due month</th>
                <th class="px-3 py-2 text-right">Installment</th>
                <th class="px-3 py-2 text-right">Principal</th>
                <th class="px-3 py-2 text-right">Interest</th>
                ${withPayment ? '<th class="px-3 py-2 text-right">Paid</th><th class="px-3 py-2 text-right">Outstanding</th><th class="px-3 py-2 text-left">Status</th>' : ''}
            </tr></thead>
            <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
            ${lines.map(l => `<tr class="loan-row">
                <td class="px-3 py-2">${l.sequenceNumber}</td>
                <td class="px-3 py-2">${monthLabel(l.dueMonth)}</td>
                <td class="px-3 py-2 text-right">${money(l.expectedAmount)}</td>
                <td class="px-3 py-2 text-right">${money(l.principalPortion)}</td>
                <td class="px-3 py-2 text-right">${money(l.interestPortion)}</td>
                ${withPayment ? `<td class="px-3 py-2 text-right">${money(l.paidAmount)}</td>
                    <td class="px-3 py-2 text-right">${money(l.outstandingAmount)}</td>
                    <td class="px-3 py-2">${repaymentBadge(l.status)}</td>` : ''}
            </tr>`).join('')}
            </tbody></table></div>`;
    }

    // ================================================================== MAIN PAGE  (/employee/loans)

    function initMain() {
        ensureShell();

        const state = {
            dashboard: null, products: [], applications: [], tasks: [],
            tab: 'applications', search: '', status: '',
            // application form
            editingId: null, editingProduct: null, previewTimer: null, previewSeq: 0
        };

        // ---------- loading

        async function loadAll() {
            const results = await Promise.allSettled([
                json(`${API}/dashboard`), json(`${API}/products`), json(API), json(`${API}/approval-tasks`)
            ]);
            const [dash, prods, apps, tasks] = results;
            state.dashboard = dash.status === 'fulfilled' ? dash.value : null;
            state.products = prods.status === 'fulfilled' ? (prods.value || []) : [];
            state.applications = apps.status === 'fulfilled' ? (apps.value || []) : [];
            state.tasks = tasks.status === 'fulfilled' ? (tasks.value || []) : [];
            results.filter(r => r.status === 'rejected').forEach(r => toastError(r.reason));
            renderAll();
        }

        function renderAll() {
            renderMetrics();
            renderTasks();
            renderProducts();
            renderTab();
        }

        // ---------- metrics

        function renderMetrics() {
            const d = state.dashboard || {};
            const set = (id, v) => { $(id).textContent = v; };
            set('mPending', d.pendingCount ?? 0);
            set('mReturned', d.returnedCount ?? 0);
            set('mDraft', d.draftCount ?? 0);
            set('mActive', d.activeLoanCount ?? 0);
            set('mOutstanding', money(d.outstandingBalance ?? 0));
            set('mMonthly', money(d.monthlyExpectedDeduction ?? 0));
            set('mNextMonth', d.nextDeductionMonth ? 'Next deduction: ' + monthLabel(d.nextDeductionMonth) : 'No upcoming deduction');
            set('mMissed', d.missedDeductionCount ?? 0);
            set('mTasks', d.assignedApprovalTaskCount ?? 0);
            $('mOutstandingSplit').textContent = d.outstandingPrincipal != null
                ? `Principal ${money(d.outstandingPrincipal)} · Interest ${money(d.outstandingInterest)}` : '';
            $('mReturnedCard').classList.toggle('loan-metric-alert', (d.returnedCount || 0) > 0);
            $('mMissedCard').classList.toggle('loan-metric-danger', (d.missedDeductionCount || 0) > 0);
        }

        // ---------- assigned custom-approver tasks

        function renderTasks() {
            const section = $('tasksSection');
            section.classList.toggle('hidden', !state.tasks.length);
            $('taskCount').textContent = state.tasks.length;
            $('taskRows').innerHTML = state.tasks.map(t => `<tr class="loan-row">
                <td class="px-4 py-3 font-semibold">${esc(t.applicationNumber)}</td>
                <td class="px-4 py-3">${esc(t.employeeName)}<div class="text-xs text-gray-500">${esc(t.departmentName || '')}</div></td>
                <td class="px-4 py-3">${esc(t.loanProductName)}</td>
                <td class="px-4 py-3 text-right">${money(t.requestedAmount)}</td>
                <td class="px-4 py-3 text-right">${money(t.repaymentAmount)}<div class="text-xs text-gray-500">${t.tenorMonths || '-'} mo</div></td>
                <td class="px-4 py-3">${dateTime(t.submittedAt)}</td>
                <td class="px-4 py-3">
                    <div class="flex flex-wrap gap-1">
                        <a class="loan-mini-btn" href="${PAGE}/${t.loanApplicationId}?as=approver">Review</a>
                        <button type="button" class="loan-mini-btn loan-mini-success" data-action="task-approve" data-task="${esc(t.taskId)}">Approve</button>
                        <button type="button" class="loan-mini-btn loan-mini-warning" data-action="task-return" data-task="${esc(t.taskId)}">Return</button>
                        <button type="button" class="loan-mini-btn loan-mini-danger" data-action="task-reject" data-task="${esc(t.taskId)}">Reject</button>
                    </div>
                </td></tr>`).join('');
        }

        // ---------- products

        function renderProducts() {
            const box = $('productCards');
            if (!state.products.length) {
                box.innerHTML = `<div class="md:col-span-2 xl:col-span-3">${empty('No loan products are available right now.')}</div>`;
                return;
            }
            box.innerHTML = state.products.map(p => `<div class="loan-card flex flex-col p-5">
                <div class="flex items-start justify-between gap-3">
                    <h3 class="min-w-0 break-words text-base font-bold">${esc(p.name)}</h3>
                    ${routeBadge(p.approvalRouteType, p.approvalRouteLabel)}
                </div>
                <p class="mt-1 line-clamp-2 text-sm text-gray-500">${esc(p.description || 'No description provided.')}</p>
                <dl class="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                    <div><dt class="text-xs text-gray-500">Amount range</dt><dd class="font-semibold">${money(p.minimumAmount)} – ${money(p.maximumAmount)}</dd></div>
                    <div><dt class="text-xs text-gray-500">Max tenor</dt><dd class="font-semibold">${p.maximumTenorMonths} months</dd></div>
                    <div><dt class="text-xs text-gray-500">Min. repayment</dt><dd class="font-semibold">${money(p.minimumRepaymentAmount)}</dd></div>
                    <div><dt class="text-xs text-gray-500">Interest</dt><dd class="font-semibold">${p.interestType === 'INTEREST_BEARING' ? esc(p.interestRate) + '% p.a.' : 'Interest-free'}</dd></div>
                </dl>
                ${p.requiresAttachment ? '<p class="mt-3 flex items-center gap-1 text-xs font-semibold text-amber-700"><span class="material-icons-round text-sm">attach_file</span>Supporting document required</p>' : ''}
                <div class="mt-auto pt-4">
                    <button type="button" class="loan-primary-btn w-full justify-center" data-action="apply" data-product="${p.id}">
                        <span class="material-icons-round text-base">add_circle</span>Apply</button>
                </div>
            </div>`).join('');
        }

        // ---------- application / active tables

        function filteredApplications() {
            const q = state.search.trim().toLowerCase();
            return state.applications.filter(a =>
                (!state.status || a.status === state.status)
                && (!q || (a.applicationNumber || '').toLowerCase().includes(q)
                    || (a.loanProductName || '').toLowerCase().includes(q)));
        }

        function renderTab() {
            document.querySelectorAll('[data-tab]').forEach(btn =>
                btn.classList.toggle('loan-tab-active', btn.dataset.tab === state.tab));
            $('applicationsPanel').classList.toggle('hidden', state.tab !== 'applications');
            $('activePanel').classList.toggle('hidden', state.tab !== 'active');
            if (state.tab === 'applications') renderApplications(); else renderActive();
        }

        function rowActions(a) {
            const out = [`<a class="loan-mini-btn" href="${PAGE}/${a.id}">View</a>`];
            if (EDITABLE_STATUSES.includes(a.status)) {
                out.push(`<button type="button" class="loan-mini-btn" data-action="edit" data-id="${a.id}">${a.status === 'DRAFT' ? 'Edit' : 'Revise'}</button>`);
                out.push(`<button type="button" class="loan-mini-btn loan-mini-success" data-action="submit" data-id="${a.id}" data-resubmit="${a.status !== 'DRAFT'}">${a.status === 'DRAFT' ? 'Submit' : 'Resubmit'}</button>`);
            }
            if (a.status === 'DRAFT') {
                out.push(`<button type="button" class="loan-mini-btn loan-mini-danger" data-action="delete" data-id="${a.id}">Delete</button>`);
            }
            if (CANCELLABLE_STATUSES.includes(a.status)) {
                out.push(`<button type="button" class="loan-mini-btn loan-mini-danger" data-action="cancel" data-id="${a.id}">Cancel</button>`);
            }
            return `<div class="flex flex-wrap gap-1">${out.join('')}</div>`;
        }

        function renderApplications() {
            const rows = filteredApplications();
            $('applicationRows').innerHTML = rows.length ? rows.map(a => `<tr class="loan-row">
                <td class="px-4 py-3 font-semibold"><a class="text-primary-600 hover:underline" href="${PAGE}/${a.id}">${esc(a.applicationNumber)}</a>
                    <div class="text-xs font-normal text-gray-500">${date(a.submittedAt || a.createdAt)}</div></td>
                <td class="px-4 py-3">${esc(a.loanProductName)}</td>
                <td class="px-4 py-3 text-right">${money(a.requestedAmount)}</td>
                <td class="px-4 py-3 text-right">${money(a.repaymentAmount)}<div class="text-xs text-gray-500">${a.tenorMonths || '-'} mo from ${monthLabel(a.repaymentStartMonth)}</div></td>
                <td class="px-4 py-3">${routeBadge(a.approvalRouteType, a.approvalRouteLabel)}</td>
                <td class="px-4 py-3">${statusBadge(a.status)}${a.currentApprovalOwner ? `<div class="mt-1 text-xs text-gray-500">With ${esc(a.currentApprovalOwner)}</div>` : ''}</td>
                <td class="px-4 py-3">${rowActions(a)}</td></tr>`).join('')
                : `<tr><td colspan="7">${empty(state.applications.length ? 'No applications match your filters.' : 'You have not applied for a loan yet. Pick a product above to get started.')}</td></tr>`;
        }

        function renderActive() {
            const loans = state.applications.filter(a => LIVE_LOAN_STATUSES.includes(a.status) && a.loanAccountId);
            $('activeRows').innerHTML = loans.length ? loans.map(a => `<tr class="loan-row">
                <td class="px-4 py-3 font-semibold"><a class="text-primary-600 hover:underline" href="${PAGE}/${a.id}">${esc(a.applicationNumber)}</a></td>
                <td class="px-4 py-3">${esc(a.loanProductName)}</td>
                <td class="px-4 py-3 text-right">${money(a.requestedAmount)}</td>
                <td class="px-4 py-3 text-right">${money(a.repaymentAmount)}</td>
                <td class="px-4 py-3 text-right font-semibold">${money(a.outstandingBalance)}</td>
                <td class="px-4 py-3">${date(a.activatedAt)}</td>
                <td class="px-4 py-3">${statusBadge(a.status)}${a.hasMissedDeductions ? '<span class="loan-badge loan-badge-error ml-1">Missed deduction</span>' : ''}</td>
                <td class="px-4 py-3"><a class="loan-mini-btn" href="${PAGE}/${a.id}">View</a></td></tr>`).join('')
                : `<tr><td colspan="8">${empty('You have no active or completed loans.')}</td></tr>`;
        }

        // ---------- row / task actions

        async function submitApplication(id, resubmit) {
            try {
                if (await missingRequiredAttachment(id)) {
                    // Take the employee straight to the upload area with the reason spelled out.
                    await openEdit(id);
                    formError(ATTACHMENT_REQUIRED_MESSAGE);
                    return;
                }
            } catch (e) { toastError(e); return; }
            const ok = await confirmDialog({
                title: resubmit ? 'Resubmit application' : 'Submit application',
                message: 'Your application will be sent for approval. You will not be able to edit it while it is under review.',
                confirmLabel: resubmit ? 'Resubmit' : 'Submit'
            });
            if (!ok) return;
            try {
                await submitRequest(id);
                await loadAll();
            } catch (e) { toastError(e); }
        }

        async function cancelApplication(id) {
            const ok = await confirmDialog({
                title: 'Cancel application', message: 'This withdraws the application from approval. This cannot be undone.',
                confirmLabel: 'Cancel application', danger: true
            });
            if (!ok) return;
            try {
                await postJson(`${API}/${id}/cancel`);
                toast('Application cancelled.', 'success');
                await loadAll();
            } catch (e) { toastError(e); }
        }

        async function deleteDraft(id) {
            const ok = await confirmDialog({
                title: 'Delete draft', message: 'This draft and its attachments will be permanently deleted.',
                confirmLabel: 'Delete', danger: true
            });
            if (!ok) return;
            try {
                await json(`${API}/${id}`, {method: 'DELETE'});
                toast('Draft deleted.', 'success');
                await loadAll();
            } catch (e) { toastError(e); }
        }

        async function taskAction(action, taskId) {
            if (await runTaskAction(action, taskId)) await loadAll();
        }

        // ---------- application form modal

        const F = id => $(id);

        function openModal() { F('loanFormModal').classList.remove('hidden'); }
        function closeModal() {
            F('loanFormModal').classList.add('hidden');
            clearTimeout(state.previewTimer);
            if (new URLSearchParams(location.search).has('edit')) history.replaceState(null, '', PAGE);
        }

        function defaultStartMonth() {
            const d = new Date();
            d.setMonth(d.getMonth() + 1);
            return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        }

        function resetForm(productId) {
            state.editingId = null;
            state.editingProduct = null;
            F('loanForm').reset();
            F('loanFormTitle').textContent = 'New loan application';
            F('loanFormNumber').textContent = '';
            F('returnBanner').classList.add('hidden');
            F('formError').classList.add('hidden');
            F('attachmentPanel').classList.add('hidden');
            F('attachmentLocked').classList.remove('hidden');
            state.modalAttachmentCount = 0;
            F('fStart').value = defaultStartMonth();
            F('fProduct').innerHTML = '<option value="">Select a loan product</option>' + state.products.map(p =>
                `<option value="${p.id}">${esc(p.name)}</option>`).join('');
            if (productId) F('fProduct').value = String(productId);
            onProductChange();
            renderPreview(null);
        }

        function currentProduct() {
            const id = Number(F('fProduct').value);
            return state.editingProduct && state.editingProduct.id === id
                ? state.editingProduct : state.products.find(p => p.id === id) || null;
        }

        function onProductChange() {
            const p = currentProduct();
            const hint = F('productHint');
            if (!p) {
                hint.classList.add('hidden');
                return;
            }
            hint.classList.remove('hidden');
            hint.innerHTML = `<div class="flex flex-wrap items-center gap-2">${routeBadge(p.approvalRouteType, p.approvalRouteLabel)}
                <span class="text-xs text-gray-500">${esc(p.approvalRouteLabel || '')}</span></div>
                <ul class="mt-2 grid grid-cols-1 gap-1 text-sm sm:grid-cols-2">
                    <li>Amount: <b>${money(p.minimumAmount)}</b> to <b>${money(p.maximumAmount)}</b></li>
                    <li>Max tenor: <b>${p.maximumTenorMonths} months</b></li>
                    <li>Minimum monthly repayment: <b>${money(p.minimumRepaymentAmount)}</b></li>
                    <li>Interest: <b>${p.interestType === 'INTEREST_BEARING' ? esc(p.interestRate) + '% p.a.' : 'None (interest-free)'}</b></li>
                </ul>
                ${p.requiresAttachment ? '<p class="mt-2 flex items-center gap-1 text-xs font-semibold text-amber-700"><span class="material-icons-round text-sm">attach_file</span>A supporting document is required before you can submit.</p>' : ''}`;
            F('fAmount').min = p.minimumAmount ?? '';
            F('fAmount').max = p.maximumAmount ?? '';
            F('fTenor').max = p.maximumTenorMonths ?? '';
            F('fRepayment').min = p.minimumRepaymentAmount ?? '';
            updateAttachmentNotice();
            schedulePreview();
        }

        /** Shows (inside the form) that a document is mandatory and whether one has been attached yet. */
        function updateAttachmentNotice() {
            const p = currentProduct();
            const box = F('attachmentNotice');
            const locked = F('attachmentLocked');
            if (!p || !p.requiresAttachment) {
                box.classList.add('hidden');
                locked.textContent = 'Save the draft first, then you can attach documents here.';
                return;
            }
            const has = (state.modalAttachmentCount || 0) > 0;
            box.classList.remove('hidden');
            box.className = 'mb-3 flex items-start gap-2 rounded-lg border p-3 text-sm font-semibold ' + (has
                ? 'border-green-200 bg-green-50 text-green-800'
                : 'border-amber-300 bg-amber-50 text-amber-900');
            box.innerHTML = `<span class="material-icons-round text-base">${has ? 'check_circle' : 'warning'}</span>
                <span>${has ? 'Supporting document attached. You can submit now.'
                    : 'This loan requires a supporting document. Save the draft, upload at least one document below, then submit.'}</span>`;
            locked.textContent = 'A supporting document is required. Save the draft first, then upload it here before you submit.';
        }

        function readForm() {
            const num = id => F(id).value === '' ? null : Number(F(id).value);
            return {
                loanProductId: num('fProduct'),
                requestedAmount: num('fAmount'),
                repaymentAmount: num('fRepayment'),
                tenorMonths: num('fTenor'),
                repaymentStartMonth: F('fStart').value ? F('fStart').value + '-01' : null,
                purpose: F('fPurpose').value.trim() || null
            };
        }

        function termsComplete(c) {
            return c.loanProductId && c.requestedAmount > 0 && c.repaymentAmount > 0 && c.tenorMonths > 0 && c.repaymentStartMonth;
        }

        function schedulePreview() {
            clearTimeout(state.previewTimer);
            state.previewTimer = setTimeout(runPreview, 400);
        }

        async function runPreview() {
            const c = readForm();
            if (!termsComplete(c)) {
                renderPreview(null);
                return;
            }
            const seq = ++state.previewSeq;
            try {
                const result = await postJson(`${API}/preview`, {
                    loanProductId: c.loanProductId, requestedAmount: c.requestedAmount,
                    repaymentAmount: c.repaymentAmount, tenorMonths: c.tenorMonths,
                    repaymentStartMonth: c.repaymentStartMonth
                });
                if (seq === state.previewSeq) renderPreview(result);   // ignore out-of-order responses
            } catch (e) {
                if (seq === state.previewSeq) renderPreview({valid: false, errors: [e.message], warnings: [], lines: []});
            }
        }

        function renderPreview(p) {
            const box = F('previewBox');
            if (!p) {
                box.innerHTML = '<p class="text-sm text-gray-500">Fill in the amount, monthly repayment, tenor and start month to see your repayment plan.</p>';
                return;
            }
            const messages = (p.errors || []).map(m => `<li class="flex gap-2 text-red-700"><span class="material-icons-round text-base">error</span><span>${esc(m)}</span></li>`)
                .concat((p.warnings || []).map(m => `<li class="flex gap-2 text-amber-700"><span class="material-icons-round text-base">warning</span><span>${esc(m)}</span></li>`));
            box.innerHTML = `
                ${messages.length ? `<ul class="mb-3 space-y-1 text-sm">${messages.join('')}</ul>` : ''}
                ${p.valid || p.totalRepayableAmount != null ? `
                <div class="grid grid-cols-2 gap-3 md:grid-cols-4">
                    <div class="loan-mini-stat"><p>Total interest</p><b>${money(p.totalInterestAmount)}</b></div>
                    <div class="loan-mini-stat"><p>Total repayable</p><b>${money(p.totalRepayableAmount)}</b></div>
                    <div class="loan-mini-stat"><p>Installments</p><b>${p.numberOfInstallments ?? '-'}</b></div>
                    <div class="loan-mini-stat"><p>Ends</p><b>${monthLabel(p.expectedEndMonth)}</b></div>
                </div>
                ${p.finalInstallmentAmount != null ? `<p class="mt-2 text-xs text-gray-500">Final installment: ${money(p.finalInstallmentAmount)}</p>` : ''}` : ''}
                ${p.minimumRequiredRepaymentAmount != null && !p.valid ? `<p class="mt-2 text-sm">Minimum monthly repayment for this tenor:
                    <button type="button" class="font-bold text-primary-600 underline" data-action="use-minimum"
                            data-value="${esc(p.minimumRequiredRepaymentAmount)}">${money(p.minimumRequiredRepaymentAmount)} (use this)</button></p>` : ''}
                ${(p.lines || []).length ? `<details class="mt-3"><summary class="cursor-pointer text-sm font-semibold text-primary-600">View repayment schedule</summary>
                    <div class="mt-2 max-h-64 overflow-y-auto">${scheduleTable(p.lines, false)}</div></details>` : ''}`;
        }

        function formError(message) {
            const el = F('formError');
            el.textContent = message;
            el.classList.remove('hidden');
        }

        async function openNew(productId) {
            resetForm(productId);
            openModal();
        }

        async function openEdit(id) {
            try {
                const e = await json(`${API}/${id}/edit`);
                if (!e.editable) {
                    toast('This application can no longer be edited.', 'error');
                    return;
                }
                resetForm();
                state.editingId = e.id;
                state.editingProduct = e.loanProduct;
                F('loanFormTitle').textContent = e.status === 'RETURNED_FOR_CORRECTION' ? 'Revise application' : 'Edit draft';
                F('loanFormNumber').textContent = e.applicationNumber;
                // the product list only holds ACTIVE products; keep a deactivated product selectable while editing
                if (e.loanProduct && !state.products.some(p => p.id === e.loanProduct.id)) {
                    F('fProduct').insertAdjacentHTML('beforeend', `<option value="${e.loanProduct.id}">${esc(e.loanProduct.name)}</option>`);
                }
                F('fProduct').value = e.loanProduct ? String(e.loanProduct.id) : '';
                F('fAmount').value = e.requestedAmount ?? '';
                F('fRepayment').value = e.repaymentAmount ?? '';
                F('fTenor').value = e.tenorMonths ?? '';
                F('fStart').value = e.repaymentStartMonth ? e.repaymentStartMonth.substring(0, 7) : defaultStartMonth();
                F('fPurpose').value = e.purpose || '';
                if (e.latestReturnComment) {
                    F('returnBanner').classList.remove('hidden');
                    F('returnBannerText').textContent = (e.returnedByName ? e.returnedByName + ': ' : '') + e.latestReturnComment;
                }
                onProductChange();
                showAttachmentPanel(e.attachments || []);
                if (e.preview) renderPreview(e.preview);
                openModal();
            } catch (err) { toastError(err); }
        }

        /** Saves the form. Returns the saved application (with id) or null on failure. */
        async function save() {
            F('formError').classList.add('hidden');
            const c = readForm();
            if (!c.loanProductId) { formError('Choose a loan product.'); return null; }
            if (!termsComplete(c)) { formError('Enter the amount, monthly repayment, tenor and start month.'); return null; }
            try {
                const saved = state.editingId
                    ? await json(`${API}/${state.editingId}`, {
                        method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(c)})
                    : await postJson(`${API}/drafts`, c);
                if (!state.editingId) {
                    state.editingId = saved.id;
                    F('loanFormTitle').textContent = 'Edit draft';
                    F('loanFormNumber').textContent = saved.applicationNumber;
                    showAttachmentPanel([]);
                }
                return saved;
            } catch (err) {
                formError(err.message);
                return null;
            }
        }

        async function saveDraftClicked() {
            const saved = await save();
            if (!saved) return;
            toast('Draft saved.', 'success');
            await loadAll();
        }

        async function submitClicked() {
            const saved = await save();
            if (!saved) return;
            try {
                const p = currentProduct();
                if (p && p.requiresAttachment) {
                    const docs = await json(`${API}/${saved.id}/attachments`) || [];
                    renderModalAttachments(docs);
                    if (!docs.length) {
                        // The draft is saved; tell the employee exactly what is missing instead of submitting.
                        formError(ATTACHMENT_REQUIRED_MESSAGE);
                        F('attachmentPanel').scrollIntoView({behavior: 'smooth', block: 'center'});
                        await loadAll();
                        return;
                    }
                }
                await submitRequest(saved.id);
                closeModal();
                await loadAll();
            } catch (err) {
                // e.g. missing required attachment: the draft is saved, the user can fix it and retry
                formError(err.message);
                await loadAll();
            }
        }

        // ---------- form attachments

        function showAttachmentPanel(list) {
            F('attachmentLocked').classList.add('hidden');
            F('attachmentPanel').classList.remove('hidden');
            renderModalAttachments(list);
        }

        function renderModalAttachments(list) {
            state.modalAttachmentCount = list.length;
            updateAttachmentNotice();
            F('modalAttachments').innerHTML = list.length
                ? list.map(a => attachmentRow(a, state.editingId, true)).join('')
                : '<li class="text-sm text-gray-500">No documents attached yet.</li>';
        }

        async function reloadModalAttachments() {
            try {
                renderModalAttachments(await json(`${API}/${state.editingId}/attachments`) || []);
            } catch (e) { toastError(e); }
        }

        // ---------- wiring

        document.addEventListener('click', async ev => {
            const el = ev.target.closest('[data-action], [data-tab]');
            if (!el) return;
            if (el.dataset.tab) {
                state.tab = el.dataset.tab;
                renderTab();
                return;
            }
            const id = el.dataset.id;
            switch (el.dataset.action) {
                case 'apply': return openNew(el.dataset.product);
                case 'new': return openNew();
                case 'edit': return openEdit(id);
                case 'submit': return submitApplication(id, el.dataset.resubmit === 'true');
                case 'cancel': return cancelApplication(id);
                case 'delete': return deleteDraft(id);
                case 'task-approve': return taskAction('approve', el.dataset.task);
                case 'task-reject': return taskAction('reject', el.dataset.task);
                case 'task-return': return taskAction('return', el.dataset.task);
                case 'close-form': return closeModal();
                case 'save-draft': return saveDraftClicked();
                case 'submit-form': return submitClicked();
                case 'refresh': return loadAll();
                case 'use-minimum':
                    F('fRepayment').value = el.dataset.value;
                    return runPreview();
                case 'upload-attachment':
                    if (await uploadAttachment(state.editingId, F('attachmentFile'), F('attachmentType'))) {
                        await reloadModalAttachments();
                    }
                    return;
                case 'delete-attachment':
                    if (await removeAttachment(state.editingId, id)) await reloadModalAttachments();
                    return;
                default:
            }
        });

        F('fProduct').addEventListener('change', onProductChange);
        ['fAmount', 'fRepayment', 'fTenor', 'fStart'].forEach(id => F(id).addEventListener('input', schedulePreview));
        F('applicationSearch').addEventListener('input', e => { state.search = e.target.value; renderApplications(); });
        F('statusFilter').addEventListener('change', e => { state.status = e.target.value; renderApplications(); });

        resetForm();
        loadAll().then(() => {
            // deep links: /employee/loans?edit=12 (from the detail page) and ?apply=<productId>
            const params = new URLSearchParams(location.search);
            if (params.get('edit')) openEdit(params.get('edit'));
            else if (params.get('apply')) openNew(params.get('apply'));
        });
    }

    // ================================================================== DETAIL PAGE  (/employee/loans/{id})

    function initDetail() {
        ensureShell();

        const appId = Number(location.pathname.split('/').filter(Boolean).pop());
        const asApprover = new URLSearchParams(location.search).get('as') === 'approver';
        let detail = null;
        let viaApprover = false;

        async function load() {
            try {
                detail = await fetchDetail();
                render();
            } catch (e) {
                $('detailRoot').innerHTML = `<div class="rounded-lg border border-red-200 bg-red-50 p-4 text-red-700">${esc(e.message)}</div>`;
            }
        }

        async function fetchDetail() {
            if (asApprover) {
                viaApprover = true;
                return json(`${API}/${appId}/approver-detail`);
            }
            try {
                viaApprover = false;
                return await json(`${API}/${appId}`);
            } catch (ownerError) {
                // Not the applicant: an assigned custom approver gets the approver view instead.
                try {
                    viaApprover = true;
                    return await json(`${API}/${appId}/approver-detail`);
                } catch (approverError) {
                    throw ownerError;
                }
            }
        }

        function render() {
            const a = detail.application;
            const route = detail.approvalRoute;
            $('loanNumber').textContent = a.applicationNumber;
            $('loanMeta').innerHTML = `${esc(a.loanProductName)}${viaApprover ? ' · ' + esc(a.employeeName) : ''}`;
            $('loanBadges').innerHTML = statusBadge(a.status) + ' ' + routeBadge(a.approvalRouteType, a.approvalRouteLabel);
            document.title = a.applicationNumber + ' | Loan Detail';

            renderActions();
            renderReturnBanner();
            renderApproverPanel();
            renderTerms();
            renderContext();
            renderSchedule();
            renderHistory();
            renderMissed();
            renderRoute(route);
            renderAccount();
            renderAttachments();
            renderActivity();
        }

        // ---------- header actions

        function renderActions() {
            const a = detail.application;
            const out = [];
            if (detail.canEdit) out.push(`<a class="loan-secondary-btn" href="${PAGE}?edit=${a.id}"><span class="material-icons-round text-base">edit</span>${a.status === 'RETURNED_FOR_CORRECTION' ? 'Revise' : 'Edit'}</a>`);
            if (detail.canSubmit) out.push(`<button type="button" class="loan-primary-btn" data-action="submit"><span class="material-icons-round text-base">send</span>${a.status === 'RETURNED_FOR_CORRECTION' ? 'Resubmit' : 'Submit'}</button>`);
            if (detail.canCancel) out.push('<button type="button" class="loan-danger-btn" data-action="cancel"><span class="material-icons-round text-base">block</span>Cancel application</button>');
            if (detail.canDelete) out.push('<button type="button" class="loan-danger-btn" data-action="delete"><span class="material-icons-round text-base">delete</span>Delete draft</button>');
            $('primaryActions').innerHTML = out.join('');
        }

        function lastReturnStep() {
            const steps = (detail.approvalRoute && detail.approvalRoute.steps) || [];
            return steps.filter(s => s.decision === 'RETURN').sort((x, y) => (y.decisionAt || '').localeCompare(x.decisionAt || ''))[0];
        }

        function renderReturnBanner() {
            const box = $('returnBanner');
            const step = detail.application.status === 'RETURNED_FOR_CORRECTION' && !viaApprover ? lastReturnStep() : null;
            box.classList.toggle('hidden', detail.application.status !== 'RETURNED_FOR_CORRECTION' || viaApprover);
            if (detail.application.status === 'RETURNED_FOR_CORRECTION' && !viaApprover) {
                box.innerHTML = `<span class="material-icons-round text-amber-600">undo</span>
                    <div><p class="font-semibold">Returned for correction</p>
                    <p class="mt-1 text-sm">${step && step.comments ? esc(step.comments) : 'Update your application and resubmit it.'}</p>
                    ${step && step.actedByName ? `<p class="mt-1 text-xs text-gray-500">${esc(step.actedByName)} · ${dateTime(step.decisionAt)}</p>` : ''}</div>`;
            }
        }

        // ---------- custom approver action panel

        function renderApproverPanel() {
            const box = $('approverPanel');
            const show = detail.canAct && detail.currentTaskId && detail.viewerRole === 'CUSTOM_APPROVER';
            box.classList.toggle('hidden', !show);
            if (!show) return;
            box.innerHTML = `<h2 class="flex items-center gap-2 text-base font-semibold"><span class="material-icons-round text-primary-600">gavel</span>Your approval decision</h2>
                <p class="mt-1 text-sm text-gray-500">This application is waiting on you. A comment is required to reject or return it.</p>
                <textarea id="approverComment" rows="3" maxlength="2000" class="loan-control mt-3" placeholder="Comment"></textarea>
                <div class="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-3">
                    <button type="button" class="loan-success-btn justify-center" data-action="approver-approve">Approve</button>
                    <button type="button" class="loan-warning-btn justify-center" data-action="approver-return">Return</button>
                    <button type="button" class="loan-danger-solid-btn justify-center" data-action="approver-reject">Reject</button>
                </div>`;
        }

        async function approverAct(action) {
            const comment = ($('approverComment').value || '').trim();
            if (TASK_ACTIONS[action].required && !comment) {
                toast('A comment is required for this action.', 'error');
                $('approverComment').focus();
                return;
            }
            const ok = await confirmDialog({
                title: TASK_ACTIONS[action].title, message: 'Please confirm your decision.',
                confirmLabel: TASK_ACTIONS[action].label, danger: TASK_ACTIONS[action].danger
            });
            if (!ok) return;
            if (await runTaskAction(action, detail.currentTaskId, comment)) {
                // The task is gone after a decision; leave the approver view for the list.
                setTimeout(() => { location.href = PAGE; }, 900);
            }
        }

        // ---------- cards

        function renderTerms() {
            const a = detail.application;
            const p = detail.loanProduct;
            $('terms').innerHTML = [
                kv('Loan product', esc(a.loanProductName)),
                kv('Requested amount', money(a.requestedAmount)),
                kv('Monthly repayment', money(a.repaymentAmount)),
                kv('Tenor', a.tenorMonths ? a.tenorMonths + ' months' : null),
                kv('First deduction', monthLabel(a.repaymentStartMonth)),
                kv('Interest', a.interestType === 'INTEREST_BEARING' ? esc(a.interestRate) + '% p.a.' : 'Interest-free'),
                kv('Total interest', money(a.totalInterestAmount)),
                kv('Total repayable', money(a.totalRepayableAmount)),
                kv('Purpose', esc(a.purpose)),
                kv('Submitted', dateTime(a.submittedAt)),
                kv('Activated', dateTime(a.activatedAt)),
                p && p.requiresAttachment ? kv('Supporting document', 'Required') : ''
            ].join('');
        }

        function renderContext() {
            const card = $('contextCard');
            const c = detail.approvalContext;
            card.classList.toggle('hidden', !c);
            if (!c) return;
            const x = c.exposure;
            $('context').innerHTML = [
                kv('Employee', `${esc(c.employeeName)} (${esc(c.employeeNumber || '')})`),
                kv('Department', esc(c.departmentName)),
                kv('Job title', esc(c.jobTitle)),
                kv('Grade / step', esc([c.jobGradeName, c.jobStepName].filter(Boolean).join(' / '))),
                kv('Gross salary', money(c.grossSalary)),
                kv('Repayment as % of gross', c.repaymentToGrossPercent != null ? Number(c.repaymentToGrossPercent).toFixed(1) + '%' : null),
                kv('Projected monthly loan deduction', money(c.projectedTotalMonthlyLoanDeduction)),
                x ? kv('Active loans', `${x.activeLoanCount} (outstanding ${money(x.totalOutstandingBalance)})`) : '',
                x ? kv('Pending applications', x.pendingApplicationCount) : '',
                x ? kv('Missed deductions', x.missedDeductionCount) : ''
            ].join('') + ((c.warnings || []).length ? `<ul class="mt-3 space-y-1 text-sm text-amber-700">${c.warnings.map(w =>
                `<li class="flex gap-2"><span class="material-icons-round text-base">warning</span><span>${esc(w)}</span></li>`).join('')}</ul>` : '');
        }

        function renderSchedule() {
            $('scheduleBadge').innerHTML = detail.scheduleLocked
                ? '<span class="loan-badge loan-badge-success">Final schedule</span>'
                : '<span class="loan-badge loan-badge-neutral">Preview – not final until approved</span>';
            $('schedule').innerHTML = scheduleTable(detail.repaymentSchedule, detail.scheduleLocked);
        }

        function renderHistory() {
            const list = detail.repaymentHistory || [];
            $('historyCard').classList.toggle('hidden', !list.length && !detail.loanAccountId);
            $('history').innerHTML = list.length ? `<div class="overflow-x-auto"><table class="w-full min-w-[420px] text-sm">
                <thead class="loan-table-head"><tr><th class="px-3 py-2 text-left">Month</th><th class="px-3 py-2 text-left">Installment</th>
                    <th class="px-3 py-2 text-right">Amount</th><th class="px-3 py-2 text-left">Recorded</th></tr></thead>
                <tbody class="divide-y divide-gray-200 dark:divide-gray-700">${list.map(t => `<tr class="loan-row">
                    <td class="px-3 py-2">${monthLabel(t.transactionMonth)}</td><td class="px-3 py-2">#${t.scheduleSequenceNumber ?? '-'}</td>
                    <td class="px-3 py-2 text-right">${money(t.amount)}</td><td class="px-3 py-2">${dateTime(t.createdAt)}</td></tr>`).join('')}
                </tbody></table></div>` : empty('No payroll deductions recorded yet.');
        }

        function renderMissed() {
            const list = detail.missedDeductions || [];
            $('missedCard').classList.toggle('hidden', !list.length);
            $('missed').innerHTML = list.map(m => `<li class="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
                <span class="material-icons-round text-base">error</span>
                <div><p class="font-semibold">Installment #${m.sequenceNumber} for ${monthLabel(m.dueMonth)} was not deducted</p>
                <p class="text-xs">Expected ${money(m.expectedAmount)} · paid ${money(m.paidAmount)} · outstanding ${money(m.outstandingAmount)}</p></div></li>`).join('');
        }

        function renderRoute(route) {
            const a = detail.application;
            if (!route) {
                $('routeSummary').innerHTML = '<p class="text-sm text-gray-500">Approval route is set when you submit.</p>';
                $('steps').innerHTML = '';
                return;
            }
            $('routeSummary').innerHTML = `<div class="flex flex-wrap items-center gap-2">${routeBadge(route.routeType, route.routeLabel)}
                <span class="text-sm">${esc(route.routeLabel || '')}</span></div>
                ${route.customApprovalPathName ? `<p class="mt-1 text-xs text-gray-500">Path: ${esc(route.customApprovalPathName)}</p>` : ''}
                ${route.currentOwnerLabel ? `<p class="mt-2 text-sm">Waiting on <b>${esc(route.currentOwnerLabel)}</b></p>` : ''}
                <p class="mt-1 text-xs text-gray-500">${route.completedSteps} of ${route.totalSteps} steps complete</p>`;
            const steps = route.steps || [];
            $('steps').innerHTML = steps.length ? steps.map(s => `<li class="loan-step ${s.current ? 'loan-step-current' : ''}">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <p class="font-semibold">${esc(s.stageLabel)}</p>${decisionBadge(s)}</div>
                <p class="mt-1 text-xs text-gray-500">${esc(s.actedByName || s.approverName || s.approverGroup || '')}${s.decisionAt ? ' · ' + dateTime(s.decisionAt) : ''}</p>
                ${s.comments ? `<p class="mt-2 rounded-lg bg-gray-50 p-2 text-sm dark:bg-gray-900">${esc(s.comments)}</p>` : ''}
            </li>`).join('') : '<li class="text-sm text-gray-500">No approval steps yet.</li>';
        }

        function renderAccount() {
            const card = $('accountCard');
            card.classList.toggle('hidden', !detail.loanAccountId);
            if (!detail.loanAccountId) return;
            const total = Number(detail.application.totalRepayableAmount || 0);
            const paid = Number(detail.totalPaidAmount || 0);
            const pct = total > 0 ? Math.min(100, Math.round(paid / total * 100)) : 0;
            $('account').innerHTML = `<div class="mb-3 flex items-center justify-between"><span class="text-sm text-gray-500">Loan account</span>${statusBadge(detail.loanAccountStatus)}</div>
                <div class="loan-progress"><div style="width:${pct}%"></div></div>
                <p class="mt-1 text-xs text-gray-500">${pct}% repaid</p>
                <dl class="mt-3">${kv('Total paid', money(detail.totalPaidAmount))}${kv('Outstanding balance', `<b>${money(detail.outstandingBalance)}</b>`)}</dl>`;
        }

        function renderAttachments() {
            const list = detail.attachments || [];
            $('uploadForm').classList.toggle('hidden', !detail.canUploadAttachment);
            const notice = $('attachmentNotice');
            const needed = detail.canUploadAttachment && detail.loanProduct && detail.loanProduct.requiresAttachment;
            notice.classList.toggle('hidden', !needed);
            if (needed) {
                const has = list.length > 0;
                notice.className = 'mt-3 flex items-start gap-2 rounded-lg border p-3 text-sm font-semibold ' + (has
                    ? 'border-green-200 bg-green-50 text-green-800'
                    : 'border-amber-300 bg-amber-50 text-amber-900');
                notice.innerHTML = `<span class="material-icons-round text-base">${has ? 'check_circle' : 'warning'}</span>
                    <span>${has ? 'Supporting document attached. You can submit now.'
                        : 'This loan requires a supporting document. Upload at least one document before you submit.'}</span>`;
            }
            $('attachments').innerHTML = list.length
                ? list.map(a => attachmentRow(a, appId, true)).join('')
                : '<li class="text-sm text-gray-500">No documents attached.</li>';
        }

        function renderActivity() {
            const list = detail.activities || [];
            $('activity').innerHTML = list.length ? list.map(x => `<li class="loan-activity">
                <p class="text-sm">${esc(x.description || pretty(x.activityType))}</p>
                <p class="text-xs text-gray-500">${esc(x.actorName || 'System')} · ${dateTime(x.createdAt)}</p></li>`).join('')
                : '<li class="text-sm text-gray-500">No activity yet.</li>';
        }

        // ---------- actions

        function attachmentMissing() {
            const p = detail.loanProduct;
            return !!(p && p.requiresAttachment) && !(detail.attachments || []).length;
        }

        async function doSubmit() {
            const resubmit = detail.application.status === 'RETURNED_FOR_CORRECTION';
            if (attachmentMissing()) {
                toast(ATTACHMENT_REQUIRED_MESSAGE, 'error');
                $('attachmentNotice').scrollIntoView({behavior: 'smooth', block: 'center'});
                return;
            }
            const ok = await confirmDialog({
                title: resubmit ? 'Resubmit application' : 'Submit application',
                message: 'Your application will be sent for approval. You will not be able to edit it while it is under review.',
                confirmLabel: resubmit ? 'Resubmit' : 'Submit'
            });
            if (!ok) return;
            try {
                await submitRequest(appId);
                await load();
            } catch (e) { toastError(e); }
        }

        async function doCancel() {
            const ok = await confirmDialog({
                title: 'Cancel application', message: 'This withdraws the application from approval. This cannot be undone.',
                confirmLabel: 'Cancel application', danger: true
            });
            if (!ok) return;
            try {
                await postJson(`${API}/${appId}/cancel`);
                toast('Application cancelled.', 'success');
                await load();
            } catch (e) { toastError(e); }
        }

        async function doDelete() {
            const ok = await confirmDialog({
                title: 'Delete draft', message: 'This draft and its attachments will be permanently deleted.',
                confirmLabel: 'Delete', danger: true
            });
            if (!ok) return;
            try {
                await json(`${API}/${appId}`, {method: 'DELETE'});
                location.href = PAGE;
            } catch (e) { toastError(e); }
        }

        document.addEventListener('click', async ev => {
            const el = ev.target.closest('[data-action]');
            if (!el) return;
            switch (el.dataset.action) {
                case 'submit': return doSubmit();
                case 'cancel': return doCancel();
                case 'delete': return doDelete();
                case 'approver-approve': return approverAct('approve');
                case 'approver-reject': return approverAct('reject');
                case 'approver-return': return approverAct('return');
                case 'upload-attachment':
                    if (await uploadAttachment(appId, $('attachmentFile'), $('attachmentType'))) await load();
                    return;
                case 'delete-attachment':
                    if (await removeAttachment(appId, el.dataset.id)) await load();
                    return;
                default:
            }
        });

        load();
    }

    return {initMain, initDetail};
})();