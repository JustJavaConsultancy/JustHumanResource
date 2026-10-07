/*
 * HR loan screens (Step 12, HR pass).
 * Used by templates/loan/hr-main.html and templates/loan/hr-detail.html.
 *
 *   HrLoans.initMain();    // /loans and /loans/products
 *   HrLoans.initDetail();  // /loans/{id}
 *
 * HR/admin access is enforced by the services; this file renders what the API returns.
 * HR approvals post { taskId, comment } to /api/hr/loans/approvals/{approve|reject|return}.
 * Loan product setup uses /api/loans/products; custom approval paths come from /api/approval-paths.
 */
const HrLoans = (function () {
    'use strict';

    const API = '/api/hr/loans';
    const PRODUCT_API = '/api/loans/products';
    const PATH_API = '/api/approval-paths';
    const PAGE = '/loans';
    // Attachment bytes are served by the shared attachment endpoints (the service decides who may read).
    const ATTACHMENT_API = '/api/employee/loans';

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

    function parseDate(value) {
        return new Date(value.length <= 10 ? value + 'T00:00:00' : value);
    }

    function date(value) {
        if (!value) return '-';
        const d = parseDate(value);
        return isNaN(d) ? esc(value) : d.toLocaleDateString('en-GB', {day: '2-digit', month: 'short', year: 'numeric'});
    }

    function dateTime(value) {
        if (!value) return '-';
        const d = parseDate(value);
        return isNaN(d) ? esc(value)
            : d.toLocaleDateString('en-GB', {day: '2-digit', month: 'short', year: 'numeric'})
            + ', ' + d.toLocaleTimeString('en-GB', {hour: '2-digit', minute: '2-digit'});
    }

    function monthLabel(value) {
        if (!value) return '-';
        const d = parseDate(value);
        return isNaN(d) ? esc(value) : d.toLocaleDateString('en-GB', {month: 'short', year: 'numeric'});
    }

    function pretty(value) {
        return esc(String(value || '').replaceAll('_', ' ').toLowerCase().replace(/^\w/, c => c.toUpperCase()));
    }

    const STATUS_LABELS = {
        DRAFT: 'Draft', SUBMITTED: 'Submitted', PENDING_HR_APPROVAL: 'Pending HR',
        PENDING_CUSTOM_APPROVAL: 'Pending approver', PENDING_FINANCE_APPROVAL: 'Pending Finance',
        RETURNED_FOR_CORRECTION: 'Returned', HR_APPROVED: 'HR approved', FINANCE_APPROVED: 'Finance approved',
        CUSTOM_APPROVED: 'Approved', ACTIVE: 'Active', REJECTED: 'Rejected', CANCELLED: 'Cancelled',
        COMPLETED: 'Completed', CLOSED: 'Closed'
    };

    function statusBadge(status) {
        const cls = {
            ACTIVE: 'loan-badge-success', COMPLETED: 'loan-badge-success', REJECTED: 'loan-badge-error',
            RETURNED_FOR_CORRECTION: 'loan-badge-warning', PENDING_HR_APPROVAL: 'loan-badge-warning',
            PENDING_CUSTOM_APPROVAL: 'loan-badge-warning', PENDING_FINANCE_APPROVAL: 'loan-badge-warning',
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

    function disbursementBadge(method, label) {
        const outside = method === 'OUTSIDE_PAYROLL';
        return `<span class="loan-badge ${outside ? 'loan-badge-warning' : 'loan-badge-blue'}" title="${esc(label || '')}">`
            + `<span class="material-icons-round" style="font-size:13px;margin-right:4px">${outside ? 'account_balance' : 'payments'}</span>`
            + `${outside ? 'Outside payroll' : 'In payroll'}</span>`;
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

    function sendJson(url, method, body) {
        return json(url, {method: method, headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)});
    }

    const postJson = (url, body) => sendJson(url, 'POST', body);

    function fileSize(bytes) {
        if (bytes == null) return '';
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / 1048576).toFixed(1) + ' MB';
    }

    // ------------------------------------------------------------------ shell: toast, confirm, comment dialog

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
                        <textarea id="loanCommentText" rows="4" maxlength="2000" class="loan-control mt-4"></textarea>
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

    // ------------------------------------------------------------------ HR approval actions

    const ACTIONS = {
        approve: {
            title: 'Approve loan application',
            hint: 'For role-based loans this sends the application on to Finance. Add an optional note for the audit trail.',
            required: false, label: 'Approve', danger: false, done: 'Application approved.'
        },
        reject: {
            title: 'Reject loan application',
            hint: 'The employee will see your comment. This ends the application.',
            required: true, label: 'Reject', danger: true, done: 'Application rejected.'
        },
        return: {
            title: 'Return for correction',
            hint: 'Tell the employee what to fix before resubmitting.',
            required: true, label: 'Return', danger: false, done: 'Application returned to the employee.'
        }
    };

    /** Resolves with the comment string ('' allowed when optional) or null when cancelled. */
    function commentDialog(cfg) {
        ensureShell();
        return new Promise(resolve => {
            const modal = $('loanCommentModal');
            const ok = $('loanCommentOk');
            const cancel = $('loanCommentCancel');
            const text = $('loanCommentText');
            const err = $('loanCommentError');
            $('loanCommentTitle').textContent = cfg.title;
            $('loanCommentHint').textContent = cfg.hint || '';
            text.classList.remove('hidden');
            text.value = '';
            text.placeholder = cfg.required ? 'Comment (required)' : 'Comment (optional)';
            err.classList.add('hidden');
            ok.textContent = cfg.label;
            ok.className = cfg.danger ? 'loan-danger-solid-btn' : 'loan-primary-btn';
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
                if (cfg.required && !value) {
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

    /** Opens the comment dialog, then posts the HR decision. Returns true when it succeeded. */
    async function decide(action, taskId) {
        const cfg = ACTIONS[action];
        const comment = await commentDialog(cfg);
        if (comment === null) return false;
        try {
            await postJson(`${API}/approvals/${action}`, {taskId: taskId, comment: comment || null});
            toast(cfg.done, 'success');
            return true;
        } catch (e) {
            toastError(e);
            return false;
        }
    }

    // ================================================================== MAIN PAGE  (/loans, /loans/products)

    function initMain() {
        ensureShell();

        const IN_FLIGHT_CUSTOM = ['PENDING_CUSTOM_APPROVAL'];
        const TABS = ['pending', 'custom', 'applications', 'active', 'missed', 'products'];

        const root = $('pageRoot');
        const initial = (root && root.dataset.initialTab) || 'applications';

        const state = {
            dashboard: null, tasks: [], all: [], missed: [], products: [], paths: [],
            tab: TABS.includes(initial) ? initial : 'applications',
            search: '', status: '', route: ''
        };

        // ---------- loading

        async function loadAll() {
            const results = await Promise.allSettled([
                json(`${API}/dashboard`), json(`${API}/tasks`), json(API), json(`${API}/missed-deductions`),
                json(PRODUCT_API), json(PATH_API)
            ]);
            const val = (i, fallback) => results[i].status === 'fulfilled' ? (results[i].value ?? fallback) : fallback;
            state.dashboard = val(0, null);
            state.tasks = val(1, []);
            state.all = val(2, []);
            state.missed = val(3, []);
            state.products = val(4, []);
            state.paths = val(5, []);
            results.filter(r => r.status === 'rejected').forEach(r => toastError(r.reason));
            renderMetrics();
            renderTab();
        }

        // ---------- metrics

        function renderMetrics() {
            const d = state.dashboard || {};
            const set = (id, v) => { $(id).textContent = v; };
            set('mPendingHr', d.pendingHrApprovalCount ?? state.tasks.length);
            set('mPendingCustom', d.pendingCustomApprovalCount ?? 0);
            set('mPendingFinance', d.pendingFinanceApprovalCount ?? 0);
            set('mDisbursement', d.pendingDisbursementCount ?? 0);
            set('mDisbursementAmount', d.pendingDisbursementAmount != null ? 'Amount ' + money(d.pendingDisbursementAmount) : '');
            $('mDisbursementCard').classList.toggle('loan-metric-alert', (d.pendingDisbursementCount || 0) > 0);
            set('mActive', d.activeLoanCount ?? 0);
            set('mActiveSub', `${d.completedLoanCount ?? 0} completed`);
            set('mOutstanding', money(d.totalOutstandingBalance ?? 0));
            set('mMonthly', money(d.totalMonthlyDeduction ?? 0));
            set('mMissed', d.missedDeductionCount ?? 0);
            set('mMissedAmount', d.missedDeductionAmount != null ? 'Amount ' + money(d.missedDeductionAmount) : '');
            set('mProducts', `${d.activeProducts ?? 0} / ${d.totalProducts ?? 0}`);
            $('mPendingHrCard').classList.toggle('loan-metric-alert', (d.pendingHrApprovalCount || 0) > 0);
            $('mMissedCard').classList.toggle('loan-metric-danger', (d.missedDeductionCount || 0) > 0);

            const statuses = Object.entries(d.applicationsByStatus || {}).filter(([, n]) => n > 0);
            $('statusChips').innerHTML = statuses.map(([s, n]) =>
                `<span class="inline-flex items-center gap-2 rounded-full border border-gray-200 px-3 py-1 text-xs dark:border-gray-700">${statusBadge(s)}<b>${n}</b></span>`).join('');
        }

        // ---------- tabs

        const matches = (q, ...fields) => !q || fields.some(f => (f || '').toLowerCase().includes(q));
        const customWaiting = () => state.all.filter(a => a.approvalRouteType === 'CUSTOM' && IN_FLIGHT_CUSTOM.includes(a.status));

        function renderTab() {
            document.querySelectorAll('[data-tab]').forEach(btn =>
                btn.classList.toggle('loan-tab-active', btn.dataset.tab === state.tab));
            TABS.forEach(name => $(name + 'Panel').classList.toggle('hidden', name !== state.tab));
            $('searchBar').classList.toggle('hidden', state.tab === 'products');
            $('applicationFilters').classList.toggle('hidden', state.tab !== 'applications');
            $('tabPendingCount').textContent = state.tasks.length;
            $('tabCustomCount').textContent = customWaiting().length;
            $('tabMissedCount').textContent = state.missed.length;
            ({pending: renderPending, custom: renderCustom, applications: renderApplications,
                active: renderActive, missed: renderMissed, products: renderProducts})[state.tab]();
        }

        function selectTab(tab) {
            state.tab = tab;
            if (tab === 'products' || (tab === 'applications' && location.pathname.endsWith('/products'))) {
                history.replaceState(null, '', tab === 'products' ? PAGE + '/products' : PAGE);
            }
            renderTab();
        }

        function query() { return state.search.trim().toLowerCase(); }

        function renderPending() {
            const rows = state.tasks.filter(t => matches(query(), t.applicationNumber, t.employeeName, t.departmentName, t.loanProductName));
            $('pendingRows').innerHTML = rows.length ? rows.map(t => `<tr class="loan-row">
                <td class="px-4 py-3 font-semibold"><a class="text-primary-600 hover:underline" href="${PAGE}/${t.loanApplicationId}">${esc(t.applicationNumber)}</a>
                    <div class="text-xs font-normal text-gray-500">${dateTime(t.submittedAt)}</div></td>
                <td class="px-4 py-3">${esc(t.employeeName)}<div class="text-xs text-gray-500">${esc(t.departmentName || '')}</div></td>
                <td class="px-4 py-3">${esc(t.loanProductName)}</td>
                <td class="px-4 py-3 text-right">${money(t.requestedAmount)}</td>
                <td class="px-4 py-3 text-right">${money(t.repaymentAmount)}<div class="text-xs text-gray-500">${t.tenorMonths || '-'} mo from ${monthLabel(t.repaymentStartMonth)}</div></td>
                <td class="px-4 py-3">${dateTime(t.taskCreatedAt)}</td>
                <td class="px-4 py-3"><div class="flex flex-wrap gap-1">
                    <a class="loan-mini-btn" href="${PAGE}/${t.loanApplicationId}">Review</a>
                    <button type="button" class="loan-mini-btn loan-mini-success" data-action="approve" data-task="${esc(t.taskId)}">Approve</button>
                    <button type="button" class="loan-mini-btn loan-mini-warning" data-action="return" data-task="${esc(t.taskId)}">Return</button>
                    <button type="button" class="loan-mini-btn loan-mini-danger" data-action="reject" data-task="${esc(t.taskId)}">Reject</button>
                </div></td></tr>`).join('')
                : `<tr><td colspan="7">${empty(state.tasks.length ? 'No approvals match your search.' : 'No loan applications are waiting for HR approval.')}</td></tr>`;
        }

        function renderCustom() {
            const rows = customWaiting().filter(a => matches(query(), a.applicationNumber, a.employeeName, a.departmentName, a.loanProductName, a.currentApprovalOwner));
            $('customRows').innerHTML = rows.length ? rows.map(a => `<tr class="loan-row">
                <td class="px-4 py-3 font-semibold"><a class="text-primary-600 hover:underline" href="${PAGE}/${a.id}">${esc(a.applicationNumber)}</a>
                    <div class="text-xs font-normal text-gray-500">Submitted ${dateTime(a.submittedAt)}</div></td>
                <td class="px-4 py-3">${esc(a.employeeName)}<div class="text-xs text-gray-500">${esc(a.departmentName || '')}</div></td>
                <td class="px-4 py-3">${esc(a.loanProductName)}<div class="mt-1">${routeBadge(a.approvalRouteType, a.approvalRouteLabel)}</div></td>
                <td class="px-4 py-3 text-right">${money(a.requestedAmount)}</td>
                <td class="px-4 py-3 text-right">${money(a.repaymentAmount)}</td>
                <td class="px-4 py-3 font-semibold">${esc(a.currentApprovalOwner || 'Not assigned')}</td>
                <td class="px-4 py-3"><a class="loan-mini-btn" href="${PAGE}/${a.id}">View</a></td></tr>`).join('')
                : `<tr><td colspan="7">${empty('No applications are currently waiting on custom approvers.')}</td></tr>`;
        }

        function applicationRow(a, withOwner) {
            return `<tr class="loan-row">
                <td class="px-4 py-3 font-semibold"><a class="text-primary-600 hover:underline" href="${PAGE}/${a.id}">${esc(a.applicationNumber)}</a>
                    <div class="text-xs font-normal text-gray-500">${date(a.submittedAt || a.createdAt)}</div></td>
                <td class="px-4 py-3">${esc(a.employeeName)}<div class="text-xs text-gray-500">${esc(a.departmentName || '')}</div></td>
                <td class="px-4 py-3">${esc(a.loanProductName)}</td>
                <td class="px-4 py-3 text-right">${money(a.requestedAmount)}</td>
                <td class="px-4 py-3 text-right">${money(a.repaymentAmount)}<div class="text-xs text-gray-500">${a.tenorMonths || '-'} mo</div></td>
                <td class="px-4 py-3">${routeBadge(a.approvalRouteType, a.approvalRouteLabel)}</td>
                <td class="px-4 py-3">${statusBadge(a.status)}${withOwner && a.currentApprovalOwner ? `<div class="mt-1 text-xs text-gray-500">With ${esc(a.currentApprovalOwner)}</div>` : ''}</td>
                <td class="px-4 py-3"><a class="loan-mini-btn" href="${PAGE}/${a.id}">View</a></td></tr>`;
        }

        function renderApplications() {
            const rows = state.all.filter(a => (!state.status || a.status === state.status)
                && (!state.route || a.approvalRouteType === state.route)
                && matches(query(), a.applicationNumber, a.employeeName, a.departmentName, a.loanProductName));
            $('applicationRows').innerHTML = rows.length ? rows.map(a => applicationRow(a, true)).join('')
                : `<tr><td colspan="8">${empty(state.all.length ? 'No applications match your filters.' : 'No loan applications yet.')}</td></tr>`;
        }

        function renderActive() {
            const rows = state.all.filter(a => a.status === 'ACTIVE'
                && matches(query(), a.applicationNumber, a.employeeName, a.departmentName, a.loanProductName));
            $('activeRows').innerHTML = rows.length ? rows.map(a => `<tr class="loan-row">
                <td class="px-4 py-3 font-semibold"><a class="text-primary-600 hover:underline" href="${PAGE}/${a.id}">${esc(a.applicationNumber)}</a></td>
                <td class="px-4 py-3">${esc(a.employeeName)}<div class="text-xs text-gray-500">${esc(a.departmentName || '')}</div></td>
                <td class="px-4 py-3">${esc(a.loanProductName)}<div class="mt-1">${routeBadge(a.approvalRouteType, a.approvalRouteLabel)}</div></td>
                <td class="px-4 py-3 text-right">${money(a.requestedAmount)}</td>
                <td class="px-4 py-3 text-right">${money(a.repaymentAmount)}</td>
                <td class="px-4 py-3 text-right font-semibold">${money(a.outstandingBalance)}</td>
                <td class="px-4 py-3">${date(a.activatedAt)}</td>
                <td class="px-4 py-3">${statusBadge(a.status)}${a.hasMissedDeductions ? '<span class="loan-badge loan-badge-error ml-1">Missed</span>' : ''}</td>
                <td class="px-4 py-3"><a class="loan-mini-btn" href="${PAGE}/${a.id}">View</a></td></tr>`).join('')
                : `<tr><td colspan="9">${empty('No active loans.')}</td></tr>`;

            const top = (state.dashboard && state.dashboard.topExposures) || [];
            $('exposureRows').innerHTML = top.length ? top.map(x => `<tr class="loan-row">
                <td class="px-4 py-3 font-semibold">${esc(x.employeeName)}</td>
                <td class="px-4 py-3 text-right">${x.activeLoanCount}</td>
                <td class="px-4 py-3 text-right">${x.pendingApplicationCount}</td>
                <td class="px-4 py-3 text-right">${x.missedDeductionCount > 0 ? `<span class="font-semibold text-red-600">${x.missedDeductionCount}</span>` : '0'}</td>
                <td class="px-4 py-3 text-right">${money(x.totalMonthlyDeduction)}</td>
                <td class="px-4 py-3 text-right font-semibold">${money(x.totalOutstandingBalance)}</td></tr>`).join('')
                : `<tr><td colspan="6">${empty('No employee exposure to show.')}</td></tr>`;
        }

        function renderMissed() {
            const rows = state.missed.filter(m => matches(query(), m.applicationNumber, m.employeeName, m.departmentName, m.loanProductName));
            $('missedRows').innerHTML = rows.length ? rows.map(m => `<tr class="loan-row">
                <td class="px-4 py-3 font-semibold"><a class="text-primary-600 hover:underline" href="${PAGE}/${m.loanApplicationId}">${esc(m.applicationNumber)}</a></td>
                <td class="px-4 py-3">${esc(m.employeeName)}<div class="text-xs text-gray-500">${esc(m.departmentName || '')}</div></td>
                <td class="px-4 py-3">${esc(m.loanProductName)}</td>
                <td class="px-4 py-3">#${m.sequenceNumber} · ${monthLabel(m.dueMonth)}</td>
                <td class="px-4 py-3 text-right">${money(m.expectedAmount)}</td>
                <td class="px-4 py-3 text-right">${money(m.paidAmount)}</td>
                <td class="px-4 py-3 text-right font-semibold text-red-600">${money(m.outstandingAmount)}</td>
                <td class="px-4 py-3 text-right">${money(m.loanOutstandingBalance)}</td>
                <td class="px-4 py-3">${dateTime(m.missedAt)}</td></tr>`).join('')
                : `<tr><td colspan="9">${empty(state.missed.length ? 'No missed deductions match your search.' : 'No missed deductions.')}</td></tr>`;
        }

        // ---------- loan products

        function renderProducts() {
            $('productRows').innerHTML = state.products.length ? state.products.map(p => `<tr class="loan-row">
                <td class="px-4 py-3 font-semibold">${esc(p.name)}<div class="text-xs font-normal text-gray-500">${esc(p.code)}</div></td>
                <td class="px-4 py-3">${money(p.minimumAmount)} – ${money(p.maximumAmount)}
                    <div class="text-xs text-gray-500">Min. repayment ${money(p.minimumRepaymentAmount)} · up to ${p.maximumTenorMonths} mo</div></td>
                <td class="px-4 py-3">${p.interestType === 'INTEREST_BEARING' ? esc(p.interestRate) + '% p.a.' : 'Interest-free'}</td>
                <td class="px-4 py-3">${routeBadge(p.approvalRouteType, p.approvalRouteLabel)}
                    ${p.approvalRouteType === 'CUSTOM' ? `<div class="mt-1 text-xs text-gray-500">${esc(p.customApprovalPathName || 'No path selected')}</div>` : ''}</td>
                <td class="px-4 py-3">${disbursementBadge(p.disbursementMethod, p.disbursementMethodLabel)}</td>
                <td class="px-4 py-3">${p.requiresAttachment ? 'Required' : 'Optional'}</td>
                <td class="px-4 py-3">${p.applicationCount} applications<div class="text-xs text-gray-500">${p.activeLoanCount} active loans</div></td>
                <td class="px-4 py-3">${p.active ? '<span class="loan-badge loan-badge-success">Active</span>' : '<span class="loan-badge loan-badge-neutral">Inactive</span>'}</td>
                <td class="px-4 py-3"><div class="flex flex-wrap gap-1">
                    <button type="button" class="loan-mini-btn" data-action="edit-product" data-id="${p.id}">Edit</button>
                    ${p.active
                        ? `<button type="button" class="loan-mini-btn loan-mini-warning" data-action="deactivate-product" data-id="${p.id}">Deactivate</button>`
                        : `<button type="button" class="loan-mini-btn loan-mini-success" data-action="reactivate-product" data-id="${p.id}">Reactivate</button>`}
                    ${p.deletable ? `<button type="button" class="loan-mini-btn loan-mini-danger" data-action="delete-product" data-id="${p.id}">Delete</button>` : ''}
                </div></td></tr>`).join('')
                : `<tr><td colspan="9">${empty('No loan products yet. Create one to let employees apply.')}</td></tr>`;
        }

        const P = id => $(id);
        const FINANCIAL_FIELDS = ['pCode', 'pMin', 'pMax', 'pMinRepay', 'pTenor', 'pInterestType', 'pRate', 'pRoute', 'pPath', 'pDisbursement'];

        function pathOptions(selectedId) {
            const usable = state.paths.filter(p => p.enabled || p.id === selectedId);
            return '<option value="">Select a custom approval path</option>' + usable.map(p =>
                `<option value="${p.id}">${esc(p.name)}${p.enabled ? '' : ' (disabled)'} – ${(p.steps || []).length} approver(s)</option>`).join('');
        }

        function syncProductForm() {
            const bearing = P('pInterestType').value === 'INTEREST_BEARING';
            P('pRateWrap').classList.toggle('hidden', !bearing);
            const custom = P('pRoute').value === 'CUSTOM';
            P('pPathWrap').classList.toggle('hidden', !custom);
            P('pRouteHint').textContent = custom
                ? 'Named employees from the chosen path approve in order. Finance only gets a task if the path includes a Finance employee.'
                : 'HR approves first, then Finance. Any HR user or Finance officer can act on the task.';
            P('pNoPaths').classList.toggle('hidden', !custom || state.paths.some(p => p.enabled));
            P('pDisbursementHint').textContent = P('pDisbursement').value === 'OUTSIDE_PAYROLL'
                ? 'After final approval the loan waits in the Finance disbursement queue. It becomes active, and repayments are scheduled, only after Finance confirms payment. Employees need complete bank details to apply.'
                : 'After final approval the loan becomes active and the approved amount is added to the employee\'s payroll. Repayment never starts in the same month as disbursement.';
        }

        function openProductForm(id) {
            const p = id ? state.products.find(x => x.id === Number(id)) : null;
            P('productForm').reset();
            P('pError').classList.add('hidden');
            P('pId').value = p ? p.id : '';
            P('productModalTitle').textContent = p ? 'Edit loan product' : 'New loan product';
            P('pPath').innerHTML = pathOptions(p ? p.customApprovalPathId : null);
            if (p) {
                P('pCode').value = p.code || '';
                P('pName').value = p.name || '';
                P('pDescription').value = p.description || '';
                P('pMin').value = p.minimumAmount ?? '';
                P('pMax').value = p.maximumAmount ?? '';
                P('pMinRepay').value = p.minimumRepaymentAmount ?? '';
                P('pTenor').value = p.maximumTenorMonths ?? '';
                P('pInterestType').value = p.interestType || 'INTEREST_FREE';
                P('pRate').value = p.interestRate ?? '';
                P('pRoute').value = p.approvalRouteType || 'ROLE_BASED';
                P('pPath').value = p.customApprovalPathId ? String(p.customApprovalPathId) : '';
                P('pDisbursement').value = p.disbursementMethod || 'PAYROLL_PERIOD';
                P('pAttach').checked = !!p.requiresAttachment;
            } else {
                P('pInterestType').value = 'INTEREST_FREE';
                P('pRoute').value = 'ROLE_BASED';
                P('pDisbursement').value = 'PAYROLL_PERIOD';
            }
            const locked = !!p && !p.financialTermsEditable;
            FINANCIAL_FIELDS.forEach(f => { P(f).disabled = locked; });
            P('pLockNote').classList.toggle('hidden', !locked);
            syncProductForm();
            P('productModal').classList.remove('hidden');
        }

        function closeProductForm() { P('productModal').classList.add('hidden'); }

        function readProduct() {
            const num = id => P(id).value === '' ? null : Number(P(id).value);
            const bearing = P('pInterestType').value === 'INTEREST_BEARING';
            const custom = P('pRoute').value === 'CUSTOM';
            return {
                code: P('pCode').value.trim(),
                name: P('pName').value.trim(),
                description: P('pDescription').value.trim() || null,
                minimumAmount: num('pMin'),
                maximumAmount: num('pMax'),
                minimumRepaymentAmount: num('pMinRepay'),
                maximumTenorMonths: num('pTenor'),
                interestType: P('pInterestType').value,
                interestRate: bearing ? num('pRate') : null,
                repaymentFrequency: 'MONTHLY',
                approvalRouteType: P('pRoute').value,
                customApprovalPathId: custom ? num('pPath') : null,
                disbursementMethod: P('pDisbursement').value || null,
                requiresAttachment: P('pAttach').checked
            };
        }

        function validateProduct(c) {
            if (!c.code) return 'Enter a product code.';
            if (!c.name) return 'Enter a product name.';
            if (c.minimumAmount == null || c.maximumAmount == null) return 'Enter the minimum and maximum amounts.';
            if (c.maximumAmount < c.minimumAmount) return 'The maximum amount must be at least the minimum amount.';
            if (c.minimumRepaymentAmount == null) return 'Enter the minimum monthly repayment.';
            if (!c.maximumTenorMonths || c.maximumTenorMonths < 1) return 'Enter the maximum tenor in months.';
            if (c.interestType === 'INTEREST_BEARING' && (c.interestRate == null || c.interestRate < 0 || c.interestRate > 100)) {
                return 'Enter an annual interest rate between 0 and 100.';
            }
            if (c.approvalRouteType === 'CUSTOM' && !c.customApprovalPathId) return 'Select a custom approval path.';
            if (c.disbursementMethod !== 'PAYROLL_PERIOD' && c.disbursementMethod !== 'OUTSIDE_PAYROLL') return 'Select a disbursement method.';
            return null;
        }

        async function saveProduct() {
            const c = readProduct();
            const problem = validateProduct(c);
            if (problem) {
                P('pError').textContent = problem;
                P('pError').classList.remove('hidden');
                return;
            }
            const id = P('pId').value;
            try {
                await sendJson(id ? `${PRODUCT_API}/${id}` : PRODUCT_API, id ? 'PUT' : 'POST', c);
                toast(id ? 'Loan product updated.' : 'Loan product created.', 'success');
                closeProductForm();
                await loadAll();
            } catch (e) {
                P('pError').textContent = e.message;
                P('pError').classList.remove('hidden');
            }
        }

        async function productAction(kind, id) {
            const p = state.products.find(x => x.id === Number(id));
            const cfg = {
                deactivate: {title: 'Deactivate product', message: `Employees will no longer be able to apply for "${p ? p.name : 'this product'}". Existing applications and loans are not affected.`, label: 'Deactivate', danger: false, done: 'Product deactivated.'},
                reactivate: {title: 'Reactivate product', message: `"${p ? p.name : 'This product'}" will be available to employees again.`, label: 'Reactivate', danger: false, done: 'Product reactivated.'},
                delete: {title: 'Delete product', message: `Delete "${p ? p.name : 'this product'}"? This cannot be undone.`, label: 'Delete', danger: true, done: 'Product deleted.'}
            }[kind];
            if (!await confirmDialog({title: cfg.title, message: cfg.message, confirmLabel: cfg.label, danger: cfg.danger})) return;
            try {
                if (kind === 'delete') await json(`${PRODUCT_API}/${id}`, {method: 'DELETE'});
                else await postJson(`${PRODUCT_API}/${id}/${kind}`);
                toast(cfg.done, 'success');
                await loadAll();
            } catch (e) { toastError(e); }
        }

        // ---------- wiring

        document.addEventListener('click', async ev => {
            const el = ev.target.closest('[data-action], [data-tab]');
            if (!el) return;
            if (el.dataset.tab) return selectTab(el.dataset.tab);
            const action = el.dataset.action;
            if (ACTIONS[action]) {
                if (await decide(action, el.dataset.task)) await loadAll();
                return;
            }
            switch (action) {
                case 'refresh': return loadAll();
                case 'new-product': return openProductForm();
                case 'edit-product': return openProductForm(el.dataset.id);
                case 'close-product': return closeProductForm();
                case 'save-product': return saveProduct();
                case 'deactivate-product': return productAction('deactivate', el.dataset.id);
                case 'reactivate-product': return productAction('reactivate', el.dataset.id);
                case 'delete-product': return productAction('delete', el.dataset.id);
                default:
            }
        });

        $('listSearch').addEventListener('input', e => { state.search = e.target.value; renderTab(); });
        $('statusFilter').addEventListener('change', e => { state.status = e.target.value; renderTab(); });
        $('routeFilter').addEventListener('change', e => { state.route = e.target.value; renderTab(); });
        P('pInterestType').addEventListener('change', syncProductForm);
        P('pRoute').addEventListener('change', syncProductForm);
        P('pDisbursement').addEventListener('change', syncProductForm);

        loadAll();
    }

    // ================================================================== DETAIL PAGE  (/loans/{id})

    function initDetail() {
        ensureShell();

        const appId = Number(location.pathname.split('/').filter(Boolean).pop());
        let detail = null;

        async function load() {
            try {
                detail = await json(`${API}/${appId}`);
                render();
            } catch (e) {
                $('detailRoot').innerHTML = `<div class="rounded-lg border border-red-200 bg-red-50 p-4 text-red-700">${esc(e.message)}</div>`;
            }
        }

        function render() {
            const a = detail.application;
            $('loanNumber').textContent = a.applicationNumber;
            $('loanMeta').textContent = `${a.employeeName || ''} · ${a.loanProductName || ''}`;
            $('loanBadges').innerHTML = statusBadge(a.status) + ' ' + routeBadge(a.approvalRouteType, a.approvalRouteLabel);
            document.title = a.applicationNumber + ' | Loan Detail';
            renderActionPanel();
            renderTerms();
            renderContext();
            renderExposure();
            renderRoute();
            renderSchedule();
            renderAccount();
            renderAttachments();
            renderActivity();
        }

        // ---------- HR action panel

        function renderActionPanel() {
            const box = $('hrPanel');
            const show = detail.canAct && detail.currentTaskId;
            box.classList.toggle('hidden', !show);
            if (!show) return;
            box.innerHTML = `<h2 class="flex items-center gap-2 text-base font-semibold"><span class="material-icons-round text-primary-600">fact_check</span>HR decision</h2>
                <p class="mt-1 text-sm text-gray-500">This application is waiting on HR. A comment is required to reject or return it.</p>
                <textarea id="hrComment" rows="3" maxlength="2000" class="loan-control mt-3" placeholder="Comment"></textarea>
                <p id="hrCommentError" class="mt-1 hidden text-sm text-red-600"></p>
                <div class="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-3">
                    <button type="button" class="loan-success-btn justify-center" data-action="approve">Approve</button>
                    <button type="button" class="loan-warning-btn justify-center" data-action="return">Return</button>
                    <button type="button" class="loan-danger-solid-btn justify-center" data-action="reject">Reject</button>
                </div>`;
        }

        async function act(action) {
            const cfg = ACTIONS[action];
            const comment = ($('hrComment').value || '').trim();
            if (cfg.required && !comment) {
                $('hrCommentError').textContent = 'A comment is required for this action.';
                $('hrCommentError').classList.remove('hidden');
                $('hrComment').focus();
                return;
            }
            $('hrCommentError').classList.add('hidden');
            if (!await confirmDialog({title: cfg.title, message: cfg.hint, confirmLabel: cfg.label, danger: cfg.danger})) return;
            try {
                await postJson(`${API}/approvals/${action}`, {taskId: detail.currentTaskId, comment: comment || null});
                toast(cfg.done, 'success');
                await load();
            } catch (e) { toastError(e); }
        }

        // ---------- cards

        function renderTerms() {
            const a = detail.application;
            const p = detail.loanProduct;
            $('terms').innerHTML = [
                kv('Employee', esc(a.employeeName)),
                kv('Department', esc(a.departmentName)),
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
            $('context').innerHTML = [
                kv('Employee no.', esc(c.employeeNumber)),
                kv('Email', esc(c.email)),
                kv('Job title', esc(c.jobTitle)),
                kv('Grade / step', esc([c.jobGradeName, c.jobStepName].filter(Boolean).join(' / '))),
                kv('Employment status', pretty(c.employmentStatus)),
                kv('Employment date', date(c.employmentDate)),
                kv('Gross salary (current)', money(c.grossSalary)),
                kv('Gross salary (at submission)', money(c.grossSalarySnapshot)),
                kv('Repayment as % of gross', c.repaymentToGrossPercent != null ? Number(c.repaymentToGrossPercent).toFixed(1) + '%' : null),
                kv('Projected monthly loan deduction', money(c.projectedTotalMonthlyLoanDeduction))
            ].join('') + ((c.warnings || []).length ? `<ul class="mt-3 space-y-1 text-sm text-amber-700">${c.warnings.map(w =>
                `<li class="flex gap-2"><span class="material-icons-round text-base">warning</span><span>${esc(w)}</span></li>`).join('')}</ul>` : '');
        }

        /** Existing and pending loans plus outstanding balances (decision support only). */
        function renderExposure() {
            const card = $('exposureCard');
            const x = detail.approvalContext && detail.approvalContext.exposure;
            card.classList.toggle('hidden', !x);
            if (!x) return;
            const active = x.activeLoans || [];
            const pending = x.pendingApplications || [];
            $('exposure').innerHTML = `<div class="grid grid-cols-2 gap-3 md:grid-cols-4">
                <div class="loan-mini-stat"><p>Active loans</p><b>${x.activeLoanCount}</b></div>
                <div class="loan-mini-stat"><p>Pending applications</p><b>${x.pendingApplicationCount}</b></div>
                <div class="loan-mini-stat"><p>Monthly deductions</p><b>${money(x.totalMonthlyDeduction)}</b></div>
                <div class="loan-mini-stat"><p>Outstanding balance</p><b>${money(x.totalOutstandingBalance)}</b></div></div>
                ${x.missedDeductionCount ? `<p class="mt-3 text-sm font-semibold text-red-600">${x.missedDeductionCount} missed deduction(s) on existing loans.</p>` : ''}
                <h3 class="mb-2 mt-4 text-sm font-semibold">Active loans</h3>
                ${active.length ? `<div class="overflow-x-auto"><table class="w-full min-w-[620px] text-sm">
                    <thead class="loan-table-head"><tr><th class="px-3 py-2 text-left">Loan</th><th class="px-3 py-2 text-left">Product</th>
                        <th class="px-3 py-2 text-right">Monthly</th><th class="px-3 py-2 text-right">Paid</th><th class="px-3 py-2 text-right">Outstanding</th><th class="px-3 py-2 text-left">Status</th></tr></thead>
                    <tbody class="divide-y divide-gray-200 dark:divide-gray-700">${active.map(l => `<tr class="loan-row">
                        <td class="px-3 py-2"><a class="text-primary-600 hover:underline" href="${PAGE}/${l.loanApplicationId}">${esc(l.applicationNumber)}</a></td>
                        <td class="px-3 py-2">${esc(l.loanProductName)}</td>
                        <td class="px-3 py-2 text-right">${money(l.repaymentAmount)}</td>
                        <td class="px-3 py-2 text-right">${money(l.totalPaidAmount)}</td>
                        <td class="px-3 py-2 text-right">${money(l.outstandingBalance)}</td>
                        <td class="px-3 py-2">${statusBadge(l.status)}${l.missedDeductionCount ? '<span class="loan-badge loan-badge-error ml-1">Missed</span>' : ''}</td></tr>`).join('')}
                    </tbody></table></div>` : '<p class="text-sm text-gray-500">No active loans.</p>'}
                <h3 class="mb-2 mt-4 text-sm font-semibold">Other pending applications</h3>
                ${pending.length ? `<div class="overflow-x-auto"><table class="w-full min-w-[520px] text-sm">
                    <thead class="loan-table-head"><tr><th class="px-3 py-2 text-left">Application</th><th class="px-3 py-2 text-left">Product</th>
                        <th class="px-3 py-2 text-right">Amount</th><th class="px-3 py-2 text-right">Monthly</th><th class="px-3 py-2 text-left">Status</th></tr></thead>
                    <tbody class="divide-y divide-gray-200 dark:divide-gray-700">${pending.map(l => `<tr class="loan-row">
                        <td class="px-3 py-2"><a class="text-primary-600 hover:underline" href="${PAGE}/${l.loanApplicationId}">${esc(l.applicationNumber)}</a></td>
                        <td class="px-3 py-2">${esc(l.loanProductName)}</td>
                        <td class="px-3 py-2 text-right">${money(l.requestedAmount)}</td>
                        <td class="px-3 py-2 text-right">${money(l.repaymentAmount)}</td>
                        <td class="px-3 py-2">${statusBadge(l.status)}</td></tr>`).join('')}
                    </tbody></table></div>` : '<p class="text-sm text-gray-500">None.</p>'}`;
        }

        function renderRoute() {
            const route = detail.approvalRoute;
            if (!route) {
                $('routeSummary').innerHTML = '<p class="text-sm text-gray-500">The approval route is set when the application is submitted.</p>';
                $('steps').innerHTML = '';
                return;
            }
            $('routeSummary').innerHTML = `<div class="flex flex-wrap items-center gap-2">${routeBadge(route.routeType, route.routeLabel)}
                <span class="text-sm">${esc(route.routeLabel || '')}</span></div>
                ${route.customApprovalPathName ? `<p class="mt-1 text-xs text-gray-500">Path snapshot: ${esc(route.customApprovalPathName)}</p>` : ''}
                ${route.currentOwnerLabel ? `<p class="mt-2 text-sm">Waiting on <b>${esc(route.currentOwnerLabel)}</b></p>` : ''}
                <p class="mt-1 text-xs text-gray-500">${route.completedSteps} of ${route.totalSteps} steps complete</p>`;
            const steps = route.steps || [];
            $('steps').innerHTML = steps.length ? steps.map(s => `<li class="loan-step ${s.current ? 'loan-step-current' : ''}">
                <div class="flex flex-wrap items-center justify-between gap-2"><p class="font-semibold">${esc(s.stageLabel)}</p>${decisionBadge(s)}</div>
                <p class="mt-1 text-xs text-gray-500">${esc(s.actedByName || s.approverName || s.approverGroup || '')}${s.decisionAt ? ' · ' + dateTime(s.decisionAt) : ''}</p>
                ${s.comments ? `<p class="mt-2 rounded-lg bg-gray-50 p-2 text-sm dark:bg-gray-900">${esc(s.comments)}</p>` : ''}</li>`).join('')
                : '<li class="text-sm text-gray-500">No approval steps yet.</li>';
        }

        function renderSchedule() {
            $('scheduleBadge').innerHTML = detail.scheduleLocked
                ? '<span class="loan-badge loan-badge-success">Final schedule</span>'
                : '<span class="loan-badge loan-badge-neutral">Preview – not final until approved</span>';
            const lines = detail.repaymentSchedule || [];
            const withPayment = detail.scheduleLocked;
            $('schedule').innerHTML = lines.length ? `<div class="overflow-x-auto"><table class="w-full min-w-[560px] text-sm">
                <thead class="loan-table-head"><tr><th class="px-3 py-2 text-left">#</th><th class="px-3 py-2 text-left">Due month</th>
                    <th class="px-3 py-2 text-right">Installment</th><th class="px-3 py-2 text-right">Principal</th><th class="px-3 py-2 text-right">Interest</th>
                    ${withPayment ? '<th class="px-3 py-2 text-right">Paid</th><th class="px-3 py-2 text-right">Outstanding</th><th class="px-3 py-2 text-left">Status</th>' : ''}</tr></thead>
                <tbody class="divide-y divide-gray-200 dark:divide-gray-700">${lines.map(l => `<tr class="loan-row">
                    <td class="px-3 py-2">${l.sequenceNumber}</td><td class="px-3 py-2">${monthLabel(l.dueMonth)}</td>
                    <td class="px-3 py-2 text-right">${money(l.expectedAmount)}</td><td class="px-3 py-2 text-right">${money(l.principalPortion)}</td>
                    <td class="px-3 py-2 text-right">${money(l.interestPortion)}</td>
                    ${withPayment ? `<td class="px-3 py-2 text-right">${money(l.paidAmount)}</td><td class="px-3 py-2 text-right">${money(l.outstandingAmount)}</td><td class="px-3 py-2">${repaymentBadge(l.status)}</td>` : ''}
                </tr>`).join('')}</tbody></table></div>` : empty('No schedule available yet.');

            const hist = detail.repaymentHistory || [];
            $('historyCard').classList.toggle('hidden', !hist.length);
            $('history').innerHTML = hist.length ? `<div class="overflow-x-auto"><table class="w-full min-w-[420px] text-sm">
                <thead class="loan-table-head"><tr><th class="px-3 py-2 text-left">Month</th><th class="px-3 py-2 text-left">Installment</th>
                    <th class="px-3 py-2 text-right">Amount</th><th class="px-3 py-2 text-left">Recorded</th></tr></thead>
                <tbody class="divide-y divide-gray-200 dark:divide-gray-700">${hist.map(t => `<tr class="loan-row">
                    <td class="px-3 py-2">${monthLabel(t.transactionMonth)}</td><td class="px-3 py-2">#${t.scheduleSequenceNumber ?? '-'}</td>
                    <td class="px-3 py-2 text-right">${money(t.amount)}</td><td class="px-3 py-2">${dateTime(t.createdAt)}</td></tr>`).join('')}
                </tbody></table></div>` : '';

            const missed = detail.missedDeductions || [];
            $('missedCard').classList.toggle('hidden', !missed.length);
            $('missed').innerHTML = missed.map(m => `<li class="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
                <span class="material-icons-round text-base">error</span>
                <div><p class="font-semibold">Installment #${m.sequenceNumber} for ${monthLabel(m.dueMonth)} was not deducted</p>
                <p class="text-xs">Expected ${money(m.expectedAmount)} · paid ${money(m.paidAmount)} · outstanding ${money(m.outstandingAmount)}</p></div></li>`).join('');
        }

        function renderAccount() {
            const card = $('accountCard');
            card.classList.toggle('hidden', !detail.loanAccountId);
            if (!detail.loanAccountId) return;
            const total = Number(detail.application.totalRepayableAmount || 0);
            const paid = Number(detail.totalPaidAmount || 0);
            const pct = total > 0 ? Math.min(100, Math.round(paid / total * 100)) : 0;
            $('account').innerHTML = `<div class="mb-3 flex items-center justify-between"><span class="text-sm text-gray-500">Loan account</span>${statusBadge(detail.loanAccountStatus)}</div>
                <div class="loan-progress"><div style="width:${pct}%"></div></div><p class="mt-1 text-xs text-gray-500">${pct}% repaid</p>
                <dl class="mt-3">${kv('Total paid', money(detail.totalPaidAmount))}${kv('Outstanding balance', `<b>${money(detail.outstandingBalance)}</b>`)}</dl>`;
        }

        function renderAttachments() {
            const list = detail.attachments || [];
            $('attachments').innerHTML = list.length ? list.map(a => {
                const base = `${ATTACHMENT_API}/${appId}/attachments/${a.id}`;
                return `<li class="loan-attachment"><span class="material-icons-round text-gray-400">description</span>
                    <div class="min-w-0 flex-1"><p class="truncate text-sm font-semibold" title="${esc(a.originalFilename)}">${esc(a.originalFilename)}</p>
                    <p class="text-xs text-gray-500">${pretty(a.attachmentType)} · ${esc(fileSize(a.fileSize))} · ${esc(a.uploadedByName || '')} · ${dateTime(a.createdAt)}</p></div>
                    <a href="${base}/view" target="_blank" rel="noopener" class="loan-icon-btn" title="View"><span class="material-icons-round text-base">visibility</span></a>
                    <a href="${base}" class="loan-icon-btn" title="Download"><span class="material-icons-round text-base">download</span></a></li>`;
            }).join('') : '<li class="text-sm text-gray-500">No documents attached.</li>';
        }

        function renderActivity() {
            const list = detail.activities || [];
            $('activity').innerHTML = list.length ? list.map(x => `<li class="loan-activity">
                <p class="text-sm">${esc(x.description || pretty(x.activityType))}</p>
                <p class="text-xs text-gray-500">${esc(x.actorName || 'System')} · ${dateTime(x.createdAt)}</p></li>`).join('')
                : '<li class="text-sm text-gray-500">No activity yet.</li>';
        }

        document.addEventListener('click', ev => {
            const el = ev.target.closest('[data-action]');
            if (el && ACTIONS[el.dataset.action]) act(el.dataset.action);
        });

        load();
    }

    return {initMain, initDetail};
})();