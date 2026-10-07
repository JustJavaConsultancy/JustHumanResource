/*
 * Finance loan screens (Step 12, Finance pass).
 * Used by templates/loan/finance-main.html and templates/loan/finance-detail.html.
 *
 *   FinanceLoans.initMain();    // /finance/loans
 *   FinanceLoans.initDetail();  // /finance/loans/{id}
 *
 * Finance/admin access is enforced by the services; this file renders what the API returns.
 * Finance approvals post { taskId, comment } to /api/finance/loans/approvals/{approve|reject|return}.
 */
const FinanceLoans = (function () {
    'use strict';

    const API = '/api/finance/loans';
    const PAGE = '/finance/loans';
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
        return json(url, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)});
    }

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

    // ------------------------------------------------------------------ Finance approval actions

    const ACTIONS = {
        approve: {
            title: 'Approve loan application',
            hint: 'Approving completes the approval route and activates the loan. Add an optional note for the audit trail.',
            required: false, label: 'Approve', danger: false, done: 'Loan approved.'
        },
        reject: {
            title: 'Reject loan application',
            hint: 'The employee will see your comment. This ends the application.',
            required: true, label: 'Reject', danger: true, done: 'Loan rejected.'
        },
        return: {
            title: 'Return for correction',
            hint: 'Tell the employee what to fix before resubmitting.',
            required: true, label: 'Return', danger: false, done: 'Loan returned to the employee.'
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

    /** Opens the comment dialog, then posts the decision. Returns true when it succeeded. */
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

    // ================================================================== MAIN PAGE  (/finance/loans)

    function initMain() {
        ensureShell();

        const state = {
            dashboard: null, tasks: [], missed: [],
            active: null, completed: null,        // loaded lazily when the tab is opened
            tab: 'pending', search: ''
        };

        // ---------- loading

        async function loadAll() {
            const results = await Promise.allSettled([
                json(`${API}/dashboard`), json(`${API}/tasks`), json(`${API}/missed-deductions`)
            ]);
            state.dashboard = results[0].status === 'fulfilled' ? results[0].value : null;
            state.tasks = results[1].status === 'fulfilled' ? (results[1].value || []) : [];
            state.missed = results[2].status === 'fulfilled' ? (results[2].value || []) : [];
            results.filter(r => r.status === 'rejected').forEach(r => toastError(r.reason));
            state.active = null;
            state.completed = null;
            renderMetrics();
            renderImpact();
            await renderTab();
        }

        async function ensureLoans(kind) {
            if (state[kind] !== null) return;
            try {
                state[kind] = await json(`${API}?status=${kind === 'active' ? 'ACTIVE' : 'COMPLETED'}`) || [];
            } catch (e) {
                state[kind] = [];
                toastError(e);
            }
        }

        // ---------- metrics

        function renderMetrics() {
            const d = state.dashboard || {};
            const set = (id, v) => { $(id).textContent = v; };
            set('mPending', d.pendingFinanceApprovalCount ?? state.tasks.length);
            set('mActive', d.activeLoanCount ?? 0);
            set('mCompleted', d.completedLoanCount ?? 0);
            set('mPrincipal', money(d.totalPrincipalApproved ?? 0));
            set('mOutstanding', money(d.totalOutstandingBalance ?? 0));
            set('mOutstandingSplit', d.outstandingPrincipal != null
                ? `Principal ${money(d.outstandingPrincipal)} · Interest ${money(d.outstandingInterest)}` : '');
            set('mRepaid', money(d.totalRepaid ?? 0));
            set('mMissed', d.missedDeductionCount ?? 0);
            set('mMissedAmount', d.missedDeductionAmount != null ? 'Amount ' + money(d.missedDeductionAmount) : '');
            const next = (d.payrollImpact || [])[0];
            set('mNext', next ? money(next.expectedDeductionAmount) : '0.00');
            set('mNextMonth', next ? `${monthLabel(next.month)} · ${next.employeeCount} employees` : 'No upcoming deductions');
            $('mMissedCard').classList.toggle('loan-metric-danger', (d.missedDeductionCount || 0) > 0);
            $('mPendingCard').classList.toggle('loan-metric-alert', (d.pendingFinanceApprovalCount || 0) > 0);

            const statuses = Object.entries(d.applicationsByStatus || {}).filter(([, n]) => n > 0);
            $('statusChips').innerHTML = statuses.length ? statuses.map(([s, n]) =>
                `<span class="inline-flex items-center gap-2 rounded-full border border-gray-200 px-3 py-1 text-xs dark:border-gray-700">${statusBadge(s)}<b>${n}</b></span>`).join('') : '';
        }

        // ---------- tabs

        async function renderTab() {
            document.querySelectorAll('[data-tab]').forEach(btn =>
                btn.classList.toggle('loan-tab-active', btn.dataset.tab === state.tab));
            ['pending', 'active', 'completed', 'missed', 'impact'].forEach(name =>
                $(name + 'Panel').classList.toggle('hidden', name !== state.tab));
            $('searchBar').classList.toggle('hidden', state.tab === 'impact');
            $('tabPendingCount').textContent = state.tasks.length;
            $('tabMissedCount').textContent = state.missed.length;
            if (state.tab === 'pending') renderPending();
            else if (state.tab === 'missed') renderMissed();
            else if (state.tab === 'active' || state.tab === 'completed') {
                await ensureLoans(state.tab);
                renderLoans(state.tab);
            }
        }

        const matches = (q, ...fields) => !q || fields.some(f => (f || '').toLowerCase().includes(q));

        function renderPending() {
            const q = state.search.trim().toLowerCase();
            const rows = state.tasks.filter(t => matches(q, t.applicationNumber, t.employeeName, t.departmentName, t.loanProductName));
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
                : `<tr><td colspan="7">${empty(state.tasks.length ? 'No approvals match your search.' : 'No loan applications are waiting for Finance approval.')}</td></tr>`;
        }

        function renderLoans(kind) {
            const q = state.search.trim().toLowerCase();
            const rows = (state[kind] || []).filter(a => matches(q, a.applicationNumber, a.employeeName, a.departmentName, a.loanProductName));
            $(kind + 'Rows').innerHTML = rows.length ? rows.map(a => `<tr class="loan-row">
                <td class="px-4 py-3 font-semibold"><a class="text-primary-600 hover:underline" href="${PAGE}/${a.id}">${esc(a.applicationNumber)}</a></td>
                <td class="px-4 py-3">${esc(a.employeeName)}<div class="text-xs text-gray-500">${esc(a.departmentName || '')}</div></td>
                <td class="px-4 py-3">${esc(a.loanProductName)}<div class="mt-1">${routeBadge(a.approvalRouteType, a.approvalRouteLabel)}</div></td>
                <td class="px-4 py-3 text-right">${money(a.requestedAmount)}</td>
                <td class="px-4 py-3 text-right">${money(a.repaymentAmount)}<div class="text-xs text-gray-500">${a.tenorMonths || '-'} mo</div></td>
                <td class="px-4 py-3 text-right font-semibold">${money(a.outstandingBalance)}</td>
                <td class="px-4 py-3">${date(a.activatedAt)}</td>
                <td class="px-4 py-3">${statusBadge(a.status)}${a.hasMissedDeductions ? '<span class="loan-badge loan-badge-error ml-1">Missed</span>' : ''}</td>
                <td class="px-4 py-3"><a class="loan-mini-btn" href="${PAGE}/${a.id}">View</a></td></tr>`).join('')
                : `<tr><td colspan="9">${empty(kind === 'active' ? 'No active loans.' : 'No completed loans yet.')}</td></tr>`;
        }

        function renderMissed() {
            const q = state.search.trim().toLowerCase();
            const rows = state.missed.filter(m => matches(q, m.applicationNumber, m.employeeName, m.departmentName, m.loanProductName));
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
                : `<tr><td colspan="9">${empty(state.missed.length ? 'No missed deductions match your search.' : 'No missed deductions. All scheduled deductions have been collected.')}</td></tr>`;
        }

        // ---------- payroll impact tab

        function renderImpact() {
            const d = state.dashboard || {};
            const impact = d.payrollImpact || [];
            const max = Math.max(1, ...impact.map(i => Number(i.expectedDeductionAmount || 0)));
            $('impactRows').innerHTML = impact.length ? impact.map(i => {
                const pct = Math.round(Number(i.expectedDeductionAmount || 0) / max * 100);
                return `<tr class="loan-row">
                    <td class="px-4 py-3 font-semibold">${monthLabel(i.month)}</td>
                    <td class="px-4 py-3 text-right">${money(i.expectedDeductionAmount)}</td>
                    <td class="px-4 py-3 text-right">${i.loanCount}</td>
                    <td class="px-4 py-3 text-right">${i.employeeCount}</td>
                    <td class="w-1/3 px-4 py-3"><div class="loan-progress"><div style="width:${pct}%;background:#2563eb"></div></div></td></tr>`;
            }).join('') : `<tr><td colspan="5">${empty('No upcoming loan deductions are scheduled.')}</td></tr>`;

            const top = d.topExposures || [];
            $('exposureRows').innerHTML = top.length ? top.map(x => `<tr class="loan-row">
                <td class="px-4 py-3 font-semibold">${esc(x.employeeName)}</td>
                <td class="px-4 py-3 text-right">${x.activeLoanCount}</td>
                <td class="px-4 py-3 text-right">${x.pendingApplicationCount}</td>
                <td class="px-4 py-3 text-right">${x.missedDeductionCount > 0 ? `<span class="font-semibold text-red-600">${x.missedDeductionCount}</span>` : '0'}</td>
                <td class="px-4 py-3 text-right">${money(x.totalMonthlyDeduction)}</td>
                <td class="px-4 py-3 text-right font-semibold">${money(x.totalOutstandingBalance)}</td></tr>`).join('')
                : `<tr><td colspan="6">${empty('No employee exposure to show.')}</td></tr>`;
        }

        // ---------- wiring

        document.addEventListener('click', async ev => {
            const el = ev.target.closest('[data-action], [data-tab]');
            if (!el) return;
            if (el.dataset.tab) {
                state.tab = el.dataset.tab;
                await renderTab();
                return;
            }
            if (el.dataset.action === 'refresh') return loadAll();
            if (ACTIONS[el.dataset.action] && await decide(el.dataset.action, el.dataset.task)) await loadAll();
        });

        $('listSearch').addEventListener('input', async e => {
            state.search = e.target.value;
            await renderTab();
        });

        loadAll();
    }

    // ================================================================== DETAIL PAGE  (/finance/loans/{id})

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
            renderHrResult();
            renderContext();
            renderImpact();
            renderSchedule();
            renderRoute();
            renderAccount();
            renderAttachments();
            renderActivity();
        }

        // ---------- Finance action panel

        function renderActionPanel() {
            const box = $('financePanel');
            const show = detail.canAct && detail.currentTaskId;
            box.classList.toggle('hidden', !show);
            if (!show) return;
            box.innerHTML = `<h2 class="flex items-center gap-2 text-base font-semibold"><span class="material-icons-round text-primary-600">fact_check</span>Finance decision</h2>
                <p class="mt-1 text-sm text-gray-500">This application is waiting on Finance. A comment is required to reject or return it.</p>
                <textarea id="financeComment" rows="3" maxlength="2000" class="loan-control mt-3" placeholder="Comment"></textarea>
                <p id="financeCommentError" class="mt-1 hidden text-sm text-red-600"></p>
                <div class="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-3">
                    <button type="button" class="loan-success-btn justify-center" data-action="approve">Approve</button>
                    <button type="button" class="loan-warning-btn justify-center" data-action="return">Return</button>
                    <button type="button" class="loan-danger-solid-btn justify-center" data-action="reject">Reject</button>
                </div>`;
        }

        async function act(action) {
            const cfg = ACTIONS[action];
            const textarea = $('financeComment');
            const comment = (textarea.value || '').trim();
            if (cfg.required && !comment) {
                $('financeCommentError').textContent = 'A comment is required for this action.';
                $('financeCommentError').classList.remove('hidden');
                textarea.focus();
                return;
            }
            $('financeCommentError').classList.add('hidden');
            const ok = await confirmAction(cfg);
            if (!ok) return;
            try {
                await postJson(`${API}/approvals/${action}`, {taskId: detail.currentTaskId, comment: comment || null});
                toast(cfg.done, 'success');
                await load();
            } catch (e) { toastError(e); }
        }

        /** Reuses the comment dialog only for its confirm shell: shows the hint and asks for confirmation. */
        function confirmAction(cfg) {
            return new Promise(resolve => {
                ensureShell();
                const modal = $('loanCommentModal');
                const ok = $('loanCommentOk');
                const cancel = $('loanCommentCancel');
                $('loanCommentTitle').textContent = cfg.title;
                $('loanCommentHint').textContent = cfg.hint;
                $('loanCommentText').classList.add('hidden');
                $('loanCommentError').classList.add('hidden');
                ok.textContent = cfg.label;
                ok.className = cfg.danger ? 'loan-danger-solid-btn' : 'loan-primary-btn';
                modal.classList.remove('hidden');

                function done(result) {
                    modal.classList.add('hidden');
                    $('loanCommentText').classList.remove('hidden');
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

        /** HR's result for role-based loans, shown ahead of the Finance decision. */
        function renderHrResult() {
            const card = $('hrCard');
            const steps = (detail.approvalRoute && detail.approvalRoute.steps) || [];
            const hr = detail.application.approvalRouteType === 'ROLE_BASED'
                ? steps.filter(s => s.approvalStage === 'HR').pop() : null;
            card.classList.toggle('hidden', !hr);
            if (!hr) return;
            $('hrResult').innerHTML = `<div class="flex flex-wrap items-center justify-between gap-2">
                <p class="font-semibold">${esc(hr.stageLabel || 'HR Approval')}</p>${decisionBadge(hr)}</div>
                <p class="mt-1 text-xs text-gray-500">${esc(hr.actedByName || hr.approverName || hr.approverGroup || '')}${hr.decisionAt ? ' · ' + dateTime(hr.decisionAt) : ''}</p>
                ${hr.comments ? `<p class="mt-2 rounded-lg bg-gray-50 p-2 text-sm dark:bg-gray-900">${esc(hr.comments)}</p>` : '<p class="mt-2 text-sm text-gray-500">No comment recorded.</p>'}`;
        }

        function renderContext() {
            const card = $('contextCard');
            const c = detail.approvalContext;
            card.classList.toggle('hidden', !c);
            if (!c) return;
            const x = c.exposure;
            $('context').innerHTML = [
                kv('Employee no.', esc(c.employeeNumber)),
                kv('Email', esc(c.email)),
                kv('Job title', esc(c.jobTitle)),
                kv('Grade / step', esc([c.jobGradeName, c.jobStepName].filter(Boolean).join(' / '))),
                kv('Employment status', pretty(c.employmentStatus)),
                kv('Employment date', date(c.employmentDate)),
                kv('Gross salary (current)', money(c.grossSalary)),
                kv('Gross salary (at submission)', money(c.grossSalarySnapshot))
            ].join('')
                + (x ? `<div class="mt-4 grid grid-cols-2 gap-3 md:grid-cols-4">
                    <div class="loan-mini-stat"><p>Active loans</p><b>${x.activeLoanCount}</b></div>
                    <div class="loan-mini-stat"><p>Pending apps</p><b>${x.pendingApplicationCount}</b></div>
                    <div class="loan-mini-stat"><p>Missed deductions</p><b>${x.missedDeductionCount}</b></div>
                    <div class="loan-mini-stat"><p>Outstanding</p><b>${money(x.totalOutstandingBalance)}</b></div></div>` : '')
                + ((c.warnings || []).length ? `<ul class="mt-3 space-y-1 text-sm text-amber-700">${c.warnings.map(w =>
                    `<li class="flex gap-2"><span class="material-icons-round text-base">warning</span><span>${esc(w)}</span></li>`).join('')}</ul>` : '');

            const loans = (x && x.activeLoans) || [];
            $('existingLoans').innerHTML = loans.length ? `<div class="overflow-x-auto"><table class="w-full min-w-[560px] text-sm">
                <thead class="loan-table-head"><tr><th class="px-3 py-2 text-left">Loan</th><th class="px-3 py-2 text-left">Product</th>
                    <th class="px-3 py-2 text-right">Monthly</th><th class="px-3 py-2 text-right">Outstanding</th><th class="px-3 py-2 text-left">Status</th></tr></thead>
                <tbody class="divide-y divide-gray-200 dark:divide-gray-700">${loans.map(l => `<tr class="loan-row">
                    <td class="px-3 py-2"><a class="text-primary-600 hover:underline" href="${PAGE}/${l.loanApplicationId}">${esc(l.applicationNumber)}</a></td>
                    <td class="px-3 py-2">${esc(l.loanProductName)}</td>
                    <td class="px-3 py-2 text-right">${money(l.repaymentAmount)}</td>
                    <td class="px-3 py-2 text-right">${money(l.outstandingBalance)}</td>
                    <td class="px-3 py-2">${statusBadge(l.status)}${l.missedDeductionCount ? '<span class="loan-badge loan-badge-error ml-1">Missed</span>' : ''}</td></tr>`).join('')}
                </tbody></table></div>` : '';
        }

        /** Payroll deduction impact of this loan, computed from the application, context and schedule. */
        function renderImpact() {
            const a = detail.application;
            const c = detail.approvalContext;
            const lines = detail.repaymentSchedule || [];
            const projected = c && c.projectedTotalMonthlyLoanDeduction != null ? Number(c.projectedTotalMonthlyLoanDeduction) : null;
            const existing = projected != null ? projected - Number(a.repaymentAmount || 0) : null;
            const last = lines.length ? lines[lines.length - 1] : null;
            $('impact').innerHTML = `<div class="grid grid-cols-2 gap-3 md:grid-cols-3">
                <div class="loan-mini-stat"><p>This loan / month</p><b>${money(a.repaymentAmount)}</b></div>
                <div class="loan-mini-stat"><p>Other loans / month</p><b>${existing != null ? money(existing) : '-'}</b></div>
                <div class="loan-mini-stat"><p>Total loan deduction</p><b>${projected != null ? money(projected) : '-'}</b></div>
                <div class="loan-mini-stat"><p>% of gross salary</p><b>${c && c.repaymentToGrossPercent != null ? Number(c.repaymentToGrossPercent).toFixed(1) + '%' : '-'}</b></div>
                <div class="loan-mini-stat"><p>First deduction</p><b>${monthLabel(a.repaymentStartMonth)}</b></div>
                <div class="loan-mini-stat"><p>Last deduction</p><b>${last ? monthLabel(last.dueMonth) : '-'}</b></div></div>
                <p class="mt-3 text-xs text-gray-500">Deductions are taken through payroll each month from the first deduction month until the loan is cleared.
                    ${detail.scheduleLocked ? '' : 'Figures are based on the preview schedule until the loan is fully approved.'}</p>`;
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
                    <th class="px-3 py-2 text-right">Amount</th><th class="px-3 py-2 text-left">Payroll run</th><th class="px-3 py-2 text-left">Recorded</th></tr></thead>
                <tbody class="divide-y divide-gray-200 dark:divide-gray-700">${hist.map(t => `<tr class="loan-row">
                    <td class="px-3 py-2">${monthLabel(t.transactionMonth)}</td><td class="px-3 py-2">#${t.scheduleSequenceNumber ?? '-'}</td>
                    <td class="px-3 py-2 text-right">${money(t.amount)}</td><td class="px-3 py-2">${t.payrollRunId ?? '-'}</td>
                    <td class="px-3 py-2">${dateTime(t.createdAt)}</td></tr>`).join('')}</tbody></table></div>` : '';

            const missed = detail.missedDeductions || [];
            $('missedCard').classList.toggle('hidden', !missed.length);
            $('missed').innerHTML = missed.map(m => `<li class="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
                <span class="material-icons-round text-base">error</span>
                <div><p class="font-semibold">Installment #${m.sequenceNumber} for ${monthLabel(m.dueMonth)} was not deducted</p>
                <p class="text-xs">Expected ${money(m.expectedAmount)} · paid ${money(m.paidAmount)} · outstanding ${money(m.outstandingAmount)}</p></div></li>`).join('');
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
                ${route.customApprovalPathName ? `<p class="mt-1 text-xs text-gray-500">Path: ${esc(route.customApprovalPathName)}</p>` : ''}
                ${route.currentOwnerLabel ? `<p class="mt-2 text-sm">Waiting on <b>${esc(route.currentOwnerLabel)}</b></p>` : ''}
                <p class="mt-1 text-xs text-gray-500">${route.completedSteps} of ${route.totalSteps} steps complete</p>`;
            const steps = route.steps || [];
            $('steps').innerHTML = steps.length ? steps.map(s => `<li class="loan-step ${s.current ? 'loan-step-current' : ''}">
                <div class="flex flex-wrap items-center justify-between gap-2"><p class="font-semibold">${esc(s.stageLabel)}</p>${decisionBadge(s)}</div>
                <p class="mt-1 text-xs text-gray-500">${esc(s.actedByName || s.approverName || s.approverGroup || '')}${s.decisionAt ? ' · ' + dateTime(s.decisionAt) : ''}</p>
                ${s.comments ? `<p class="mt-2 rounded-lg bg-gray-50 p-2 text-sm dark:bg-gray-900">${esc(s.comments)}</p>` : ''}</li>`).join('')
                : '<li class="text-sm text-gray-500">No approval steps yet.</li>';
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