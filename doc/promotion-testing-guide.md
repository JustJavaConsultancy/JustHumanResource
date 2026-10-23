# Promotion Module End-to-End Testing Guide

## 1. Purpose

This guide helps QA testers validate the Promotion module end to end, from request creation through eligibility checks, approval workflow, implementation, documents, reports, payroll handoff, security, and audit history.

Use this guide for manual functional testing, UAT support, and regression testing after promotion-related changes.

Last aligned with the implemented HR page redesign: 14 October 2026.

The instructions below use the HR experience unless a different role is named. The separate pages, request search/pagination, and local vertical navigation apply to `/hr/promotions`. Admin, Finance, Executive, Manager, and Employee retain their role-owned shared-page experiences.

## 2. Scope

Covered areas:

- Promotion dashboard and navigation.
- Promotion request creation and draft update.
- Eligibility checks and warning override.
- Required document enforcement.
- Approval route preview and approval workflow.
- Approval reassignment and delegation.
- Reminder and escalation behavior.
- Request return, rejection, cancellation, and implementation.
- Promotion documents and generated letters.
- Payroll handoff lifecycle.
- Reports and CSV exports.
- Configuration management.
- Employee/requester visibility.
- Role-based authorization and salary masking.
- Audit activity and notification records.

Out of scope for manual UI confirmation unless test access exists:

- Actual email inbox delivery. Verify activity records and configured email side effects where possible.
- Non-email notification channels. Email is the implemented channel.
- Flowable-native reminder/escalation timers. The module currently uses scheduled promotion services.

## 3. Main URLs And Role Navigation

### 3.1 HR Promotion Submenu

Sign in with a Human Resource account and expand **Promotions** in the application sidebar.

| Submenu or action | URL | Expected screen |
| --- | --- | --- |
| Overview | `/hr/promotions` | Four summary cards, three work-area shortcuts, and up to five recent requests. |
| Requests | `/hr/promotions/requests` | Status filter, search, request table, result count, and pagination. |
| New Promotion | `/hr/promotions/requests/new` | Dedicated draft creation form. |
| View | `/hr/promotions/requests/{id}` | Dedicated request detail with local section navigation and status-dependent actions. |
| Edit / Edit draft | `/hr/promotions/requests/{id}/edit` | Dedicated draft editing form. |
| Approval Tasks | `/hr/promotions/approvals` | My Approval Tasks table. |
| Payroll Handoffs | `/hr/promotions/payroll-handoffs` | Handoff status filter and processing actions. |
| Reports | `/hr/promotions/reports` | Summary reports, detail rows, and CSV links. |
| Settings | `/hr/promotions/settings` | Policy configuration with local section navigation. |

Replace `{id}` with a real numeric request ID. New, detail, and edit are child screens of Requests, not separate sidebar items. The Requests submenu stays active on those screens.

### 3.2 Other Role-Owned Pages

| Persona | Entry URL | Available sections and default |
| --- | --- | --- |
| Admin | `/admin/promotions` | Dashboard (default), Requests, Approval Tasks, Reports, Configuration, Payroll Handoffs. |
| Finance | `/finance/promotions` | Approval Tasks (default), Dashboard, Requests, Reports, Payroll Handoffs. |
| Executive | `/executive/promotions` | Approval Tasks (default), Requests. |
| Hiring Manager / Department Head | `/manager/promotions` | Approval Tasks (default), Requests. |
| Employee | `/employee/promotions` | Requests only, using employee/requester-scoped data. |

These pages use section buttons and `?section=...` to focus panels within their shared role page. Do not expect HR's separate subpage routes or vertical navigation on them. Section keys are `dashboard`, `requests`, `approvals`, `reports`, `configuration`, and `payrollHandoffs`, subject to role access.

An Admin-only account does not automatically have access to `/hr/promotions`; use its Admin page. Users with multiple capabilities should test each authorized role URL explicitly.

### 3.3 Legacy Links

The `/promotion`, `/promotion/requests`, `/promotion/approvals`, `/promotion/reports`, `/promotion/configuration`, and `/promotion/payroll-handoffs` URLs are compatibility entry points. They redirect to the first permitted role in this order: Admin, HR, Finance, Executive, Manager, Employee. Unsupported sections fall back to that role's permitted default.

For HR, verify these old section links resolve to the new pages:

| Old HR link | Destination |
| --- | --- |
| `/hr/promotions?section=dashboard` | `/hr/promotions` |
| `/hr/promotions?section=requests` | `/hr/promotions/requests` |
| `/hr/promotions?section=approvals` | `/hr/promotions/approvals` |
| `/hr/promotions?section=reports` | `/hr/promotions/reports` |
| `/hr/promotions?section=configuration` | `/hr/promotions/settings` |
| `/hr/promotions?section=payrollHandoffs` | `/hr/promotions/payroll-handoffs` |

Blank or unrecognized HR section values resolve to Overview. New HR redirects should not append internal model flags to the destination URL.

### 3.4 API Groups

The page routes above are distinct from the existing API routes:

```text
/api/promotions
/api/promotions/{id}
/api/promotions/my
/api/promotions/dashboard
/api/promotions/my/dashboard
/api/promotions/tasks
/api/promotions/configuration
/api/promotions/configuration/history
/api/promotions/reports/*
/api/promotions/payroll-handoffs
/api/promotions/reference/*
```

Use authenticated API tooling with the application's normal security/CSRF requirements for API-only tests. Do not infer an API operation's authorization from the presence of a page button.

## 4. Test Personas

Prepare users with these access profiles:

| Persona | Expected access |
| --- | --- |
| HR/Admin | Create, update, submit, cancel, configure, upload documents, generate letters, implement, reassign/delegate approvals, view salary. |
| Line Manager | View assigned approval tasks, approve/reject/return assigned task, cannot self-approve. |
| Finance | View operational dashboard/list, view salary where allowed, approve finance step, manage payroll handoffs where allowed. |
| Executive | View details and approve executive step when routed. |
| Promoted Employee | View own promotion records through scoped access, salary visibility depends on authorization rules. |
| Requester | View promotion records they submitted through scoped access. |
| Ordinary Employee | May access the Employee page and their scoped records; must not access HR pages, operational lists, configuration, references, salary data, or unrelated details without the required capability. |
| Excluded role | Job HR, Restricted HR, Asset Manager, and Auditor profiles are explicitly excluded by PromotionSecurity, including when another promotion role is present. |

## 5. Test Data Checklist

Create or identify:

- One active or confirmed employee eligible for promotion.
- One employee on probation, if probation validation will be tested.
- One employee with current job/department/manager/pay group.
- One employee with latest finalized appraisal score above the configured minimum.
- One employee with appraisal score below the configured minimum.
- At least two job steps or roles with different grade/salary values.
- At least one current position history record for time-in-role checks.
- HR, Finance, Executive, Line Manager, and ordinary Employee users.
- Open payroll period for the promoted employee's company/department when testing payroll handoff apply.
- Promotion configuration with:
  - minimum appraisal score,
  - minimum months in role,
  - probation setting,
  - finance/executive approval settings,
  - required documents,
  - optional routing rules,
  - reminder/escalation settings.

## 6. Navigation And Presentation Tests

### 6.1 HR Sidebar And Overview

1. Sign in as Human Resource and open `/hr/promotions`.
2. Confirm Promotions is expanded and Overview is active.
3. Verify Pending Approvals, Approved, Implemented, and Monthly Salary Impact cards.
4. Verify Manage requests, Review approval tasks, and Track payroll handoffs shortcuts reach the matching pages.
5. Verify Recent requests shows at most five records from the dashboard response. With no requests, expect an explanatory empty state.
6. Use a recent request's View button and confirm its dedicated detail page opens.
7. Visit every submenu and confirm its active state, heading, and content match the URL. Refresh each URL and open it directly in a new tab.

Expected result: HR pages render only the selected work area. Reports, Settings, approval tasks, and payroll tables must not be stacked beneath Requests or Overview. Navigation changes pages rather than scrolling through all panels.

### 6.2 Requests Search, Filter, And Pagination

Prepare at least 31 requests, including several statuses and distinct employee names, request numbers, and departments.

1. Open Promotions > Requests.
2. Verify a maximum of 15 rows per page, result count, page label, and Previous/Next controls.
3. Move through three pages; verify Previous is disabled on the first and Next on the last.
4. Search by employee name, request number, and department, including mixed case. Search is case-insensitive and matches these fields.
5. Search for a value with no matches; verify the empty state, zero results, and disabled page controls. Clear the search.
6. Select a status and combine it with search. Changing either filter should return to the first applicable page.
7. Verify the URL reflects `status`, `q`, and the one-based `page` where applicable. Refresh or bookmark a filtered URL and verify the state restores.
8. Click View, then Back to requests, Close, or browser Back. Verify the originating list state restores. Open Edit and verify returning preserves its URL query state too.
9. Check status-dependent View, Edit, Submit, Implement, and Actions controls. Expand Actions for review/cancellation operations where applicable.

Request search and 15-row pagination run in the browser over the list returned by the existing API. The status filter is sent to the list API. This is separate from the server-paginated report detail table, which uses ten rows per request.

### 6.3 Request Detail Navigation

1. Open `/hr/promotions/requests/{id}` through View or a direct link.
2. Verify the request number and employee name, then switch between Summary, Eligibility, Approvals, Route Preview, Documents, and Activity.
3. Confirm only the selected detail section is displayed and the selected navigation button is indicated.
4. Inspect Summary for status, current/proposed roles, salary/increase where authorized, and justification.
5. Inspect the status-dependent action area for Edit draft/Submit, review actions, Implement, or Cancel as appropriate. Backend permissions and workflow state remain authoritative.
6. Complete an action, reopen the request, and verify the updated state. Document operations may refresh the detail and reset the selected local section; reselect Documents if needed.

Department names, complete approval audit metadata, and full activity metadata may be available in API responses without being rendered in the detail UI. Verify those fields through authorized API/service checks rather than expecting extra visible fields.

### 6.4 Settings Navigation

Open Promotions > Settings. Verify these local navigation groups:

| Group | Fields or content |
| --- | --- |
| Policy rules | Policy Name, Minimum Months In Role, Minimum Appraisal Score, Executive Threshold %, Allow Probation Promotion. |
| Approval requirements | Require Finance Approval, Require Executive Approval. |
| Required documents | Required Before Submit, Required Before Implementation. |
| Reminders & escalation | Reminder After Hours, Escalation After Hours, Escalation Approver, Escalation Group. |
| Configuration history | History table and Refresh History. |

Change values across several groups without saving between switches. Verify values remain in the form, inactive groups are hidden, and Save Configuration persists the complete policy. Clear a required field in an inactive group and attempt to save; validation should reveal that group. Refresh/reopen to confirm persistence. Local group selection is not a separate URL or bookmark state.

### 6.5 Loading, Responsive Layout, And Accessibility

- Use browser Network tools to verify Overview loads dashboard data, Requests loads its list, Approval Tasks loads tasks, Reports loads report/reference data, Payroll Handoffs loads handoffs, and Settings loads configuration/history/reference data. Shared reference lookups may include employees/job steps on form and Settings screens.
- Confirm unrelated work-area API calls do not run merely because the user can access those areas. References and detail/route-preview requests should load on the screens that use them.
- Test desktop and narrow/mobile viewports: the application sidebar can be toggled, local vertical navigation wraps, forms remain usable, and wide tables scroll within their panels.
- Tab through submenu links, local section buttons, inputs, pagination, and action dialogs. Verify visible focus, selected state, and readable labels.
- Verify loading and empty states. Simulate request failure and confirm an error or unavailable state is visible; record any silent failure as a defect. Retry by refreshing the appropriate page.
- Open an action dialog, cancel it, and verify no mutation occurs. Attempt required-comment and missing-approver validation, then correct the inputs and confirm.

### 6.6 Other Roles And Compatibility

Repeat permitted navigation with Admin, Finance, Executive, Manager, and Employee accounts using section 3.2. Verify disallowed sections fall back to a permitted section and no unauthorized controls appear. Execute every legacy-link case in section 3.3 separately from HR submenu tests.

## 7. Happy Path: HR Creates, Submits, Approves, Implements

### 7.1 Create Draft

1. Sign in as Human Resource.
2. Open Promotions > Requests (`/hr/promotions/requests`).
3. Click `New Promotion`; verify `/hr/promotions/requests/new` opens.
4. Select the employee.
5. Select proposed job step or role.
6. Enter effective date.
7. Enter justification and, optionally, a non-negative proposed salary.
8. Click Save Draft; verify return to Requests. Use Cancel to test returning without saving in a separate attempt.

Expected result:

- Request is created with status `DRAFT`.
- Request number is generated.
- Click View to open the dedicated detail page. Verify Summary, Eligibility, Approvals, Route Preview, Documents, and Activity as described in section 6.3.
- Activity log records request creation.

### 7.2 Update Draft

1. On Requests, click Edit for a `DRAFT`, or open its detail and click Edit draft.
2. Verify `/hr/promotions/requests/{id}/edit` and the existing employee, proposed role, effective date, salary, and justification values.
3. Change proposed role, effective date, salary, or justification and click Save Draft.
4. Reopen the request and verify the changes. Cancel a second edit attempt and verify it is not persisted.
5. Attempt a direct edit URL for a non-draft request; verify editing is blocked. Test a nonexistent or inaccessible ID and verify no blank edit can be saved as a new request.

Expected result:

- Draft updates successfully.
- Existing proposed department, proposed manager, and request source are preserved when fields not exposed by the form are unchanged. Verify via the detail API. The edit must update the same request ID rather than create another request.
- Invalid fields show validation errors.

### 7.3 Attach Required Documents

1. Open the request detail and select Documents.
2. Select the document type and file, then click Upload for each required submission document.
3. Refresh or reopen the detail view.

Expected result:

- Uploaded documents appear in the document list.
- Missing required document indicators update.
- Documents can be downloaded.
- Authorized HR/Admin can remove uploaded documents.

### 7.4 Submit Request

1. Click Submit on a draft in Requests or in the request detail action area.
2. Confirm eligibility checks run.

Expected result:

- If all blocker checks pass, status moves to the first approval stage, typically `LINE_MANAGER_REVIEW` or `HR_REVIEW`.
- Approval route is created.
- Flowable process instance is started.
- Approval steps are visible in order.
- Activity log records submission.
- Assignment notification activity is recorded.

### 7.5 Approve Each Stage

1. Sign in as the assigned line manager.
2. Open the assigned role page: `/manager/promotions?section=approvals`, `/hr/promotions/approvals`, `/finance/promotions?section=approvals`, or `/executive/promotions?section=approvals`. Admin uses `/admin/promotions?section=approvals`.
3. Confirm the task appears in `My Approval Tasks`.
4. Approve with a comment.
5. Repeat as HR, Finance, and Executive where configured.

Expected result:

- Each approval step moves from `PENDING` to `APPROVED`.
- Request status advances to the next stage.
- Final approval changes request status to `APPROVED`.
- Verify approval comments and acted timestamps through the authorized detail API; the current Approvals UI primarily displays stage, decision, and approver name.
- Activity log records each decision.

### 7.6 Implement Approved Promotion

1. Sign in as HR/Admin.
2. Open the approved request.
3. Select Documents and upload any documents required before implementation.
4. Click Implement in the detail action area or approved request row.
5. Enter implementation comment if required.

Expected result:

- Request status changes to `IMPLEMENTED`.
- Employee current job/role and department update.
- Existing current position history is closed.
- New current position history is created.
- Salary-changing requests create a payroll handoff record.
- Activity log records implementation.

## 8. Eligibility Testing

Test these checks during submission:

| Scenario | Expected result |
| --- | --- |
| Employee active or confirmed | Eligible to proceed. |
| Employee inactive or invalid employment status | Blocker eligibility failure. |
| Employee on probation and probation promotion disabled | Eligibility failure based on configuration. |
| Proposed role same as current role | Submission blocked. |
| Existing active promotion request for employee | Submission blocked. |
| Appraisal score below configured minimum | Eligibility failure. |
| Time in role below configured minimum | Eligibility failure. |
| Effective date before today | Submission blocked. |
| Missing required submit document | Submission blocked before workflow creation. |

Warning-level check override:

1. Create a request with a warning-level eligibility failure.
2. Sign in as HR/Admin.
3. Use authenticated API tooling to POST `/api/promotions/{id}/eligibility-checks/{checkId}/override` with the command reason. The current Eligibility UI has no override button.
4. Submit again.

Expected result:

- Override reason is saved.
- Activity log records override.
- Submission can proceed if no blocker failures remain.

## 9. Approval Routing Tests

### 9.1 Default Route

1. Configure finance approval enabled.
2. Configure executive approval enabled with salary threshold.
3. Create a salary-changing promotion.
4. Submit and inspect approval steps.

Expected result:

- HR step is always present.
- Finance step appears when finance approval is enabled and salary increase amount is greater than zero.
- Executive step appears when salary increase percent meets or exceeds the configured threshold.

### 9.2 Finance Skip

1. Disable finance approval or create no salary increase.
2. Submit request.

Expected result:

- Finance step is skipped.
- Workflow proceeds to the next required stage.

### 9.3 Executive Threshold

1. Set executive threshold to a known value.
2. Submit one request below threshold.
3. Submit one request at or above threshold.

Expected result:

- Below threshold skips executive approval.
- At or above threshold includes executive approval.

### 9.4 Advanced Routing Rules (API / Seeded Configuration)

The Settings UI does not provide an advanced routing-rule editor. Use a controlled test fixture or authenticated configuration API with `routingRules`, and inspect the result in the detail page's Route Preview section. Preserve the full intended configuration when preparing API updates. Current UI saves omit `routingRules`, while the configuration command defaults them to an empty list and the service replaces the stored rules. Consequently, a UI save can clear API-seeded advanced rules. Use isolated fixtures, record this limitation, and verify it explicitly rather than expecting an advanced-rule editor or guaranteed rule preservation.

Test routing rules by:

- Proposed department.
- Proposed job grade.
- Employee group/category.
- Request source.
- Priority ordering.

Expected result:

- First matching lower-priority-number rule controls finance/executive inclusion.
- Route preview shows matched rule names.

## 10. Approval Decision Variants

### 10.1 Reject

1. Submit a request to an approver.
2. Approver rejects with a comment.

Expected result:

- Request status becomes `REJECTED`.
- Rejection reason/comment is visible.
- Workflow does not continue to later approval stages.
- Activity and notification hooks are recorded.

### 10.2 Return For Correction

1. Submit a request to an approver.
2. Approver returns it with a comment.

Expected result:

- Request returns to `DRAFT`.
- HR/Admin can edit and resubmit.
- Previous approval activity remains visible.

### 10.3 Unauthorized Approval

1. Sign in as a user who is not the assigned approver or valid candidate group member.
2. Attempt to approve the task.

Expected result:

- Action is blocked.
- Flowable task remains open.
- No approval decision is persisted.

### 10.4 Self-Approval

1. Create a request for an employee who is also in an approver role.
2. Attempt to approve as that promoted employee.

Expected result:

- Self-approval is blocked where workflow authorization rules apply.

## 11. Reassignment And Delegation

### 11.1 Reassign Active Step

1. Sign in as HR/Admin.
2. Open a request with an active pending approval step and select Approvals.
3. Click Reassign, select the new approver in the dialog, add an optional comment, and Confirm.
4. Sign in as the new approver.
5. Approve the task.

Expected result:

- Verify original approver and reassignment metadata through the detail API/activity records; the UI shows the current approver and decision.
- Active Flowable task assignee changes.
- Only the named reassigned approver can complete the task.
- Activity and assignment notification hooks are recorded.

### 11.2 Delegate Active Step

Repeat the same process using delegation.

Expected result:

- Delegation metadata is shown.
- Named delegate can complete the task.
- Candidate-group ownership is cleared where applicable.

### 11.3 Invalid Reassignment

Attempt to reassign:

- a completed step,
- a non-active step,
- a request without an active task,
- as a non-HR/Admin user.

Expected result:

- Action is blocked.
- Approval ownership is unchanged.

## 12. Reminder And Escalation

Reminder tests usually require controlled timestamps or scheduler execution.

### 12.1 Reminder

1. Open HR Settings > Reminders & escalation and configure reminder timing.
2. Create a pending approval older than the reminder threshold.
3. Run or wait for scheduled reminder processing.

Expected result:

- Reminder count increments.
- Last reminder timestamp is set.
- Notification activity is recorded.
- Duplicate reminder is suppressed until due again.

### 12.2 Escalation To Employee

1. Open HR Settings > Reminders & escalation and configure escalation timing and Escalation Approver.
2. Create overdue pending approval.
3. Run or wait for escalation processing.

Expected result:

- Pending approval step moves to escalation employee.
- Active Flowable task assignee updates.
- Escalation metadata and activity are recorded.

### 12.3 Escalation To Group

1. Open HR Settings > Reminders & escalation and select an Escalation Group; verify actual group membership against the test identity configuration.
2. Run escalation processing.

Expected result:

- Approval step candidate group updates.
- Active Flowable task candidate group updates.
- Group notification activity is recorded.

## 13. Cancellation

Test cancellation for:

- `DRAFT`
- `SUBMITTED`
- `ELIGIBILITY_REVIEW`
- approval-review statuses

Expected result:

- HR/Admin can cancel cancellable requests.
- Status becomes `CANCELLED`.
- Active Flowable process instance is deleted when present.
- Activity and notification hooks are recorded.

Negative cases:

- `APPROVED`, `IMPLEMENTED`, `REJECTED`, and already `CANCELLED` requests cannot be cancelled.
- Non-HR/Admin user cannot cancel.

## 14. Implementation Edge Cases

### 14.1 Missing Implementation Documents

1. Configure required documents before implementation.
2. Approve a request.
3. Attempt implementation without required documents.

Expected result:

- Implementation is blocked.
- Missing document names are shown.
- Employee job/position records remain unchanged.

### 14.2 Baseline Conflict

1. Create and approve a request.
2. Change employee current job or department outside the promotion flow.
3. Attempt implementation.

Expected result:

- Implementation is blocked because employee baseline no longer matches request.

### 14.3 Retry Idempotency

1. Implement a promotion.
2. Retry implementation for the same request.

Expected result:

- Duplicate same-date position history is not created.
- Already implemented state is handled safely.
- Missing salary-change payroll handoff is rechecked for already implemented salary-changing requests.

## 15. Document And Letter Testing

### 15.1 Upload Attachment

1. In request detail > Documents, select a valid document type and file, then click Upload.
2. Confirm attachment appears in request detail.
3. Download it.

Expected result:

- Attachment metadata is correct.
- File download works.
- Attachment is synced best-effort to employee document library.
- Sync id, timestamp, or sync error is visible where exposed.

### 15.2 Invalid Attachment

Upload unsupported content type or invalid file.

Expected result:

- Upload is rejected with a validation message.

### 15.3 Generate Promotion Letter

1. Open a request detail and select Documents.
2. Click Generate Promotion Letter.
3. Download generated PDF.

Expected result:

- PDF attachment is created.
- Template key, name, and version metadata are stored.
- Generated document is linked to employee document library when sync succeeds.

### 15.4 Generate Salary Adjustment Letter

Repeat for salary-adjustment letter.

Expected result:

- Correct salary-adjustment content/template metadata is used.

## 16. Payroll Handoff Testing

Salary-changing implemented promotions create payroll handoffs.

### 16.1 List Handoffs

1. Sign in as authorized Finance/HR/Admin user.
2. HR: open Promotions > Payroll Handoffs (`/hr/promotions/payroll-handoffs`). Finance/Admin: use their role page with `?section=payrollHandoffs`.
3. Verify Pending is the default filter; test Acknowledged, Applied, Cancelled, All statuses, and Refresh.

Expected result:

- Pending handoffs are visible.
- Employee, request, effective date, salary change, and status are shown.

### 16.2 Acknowledge

1. Click Acknowledge on a pending handoff, enter an optional note, and Confirm in the dialog.

Expected result:

- Handoff status changes as expected.
- Activity is recorded.

### 16.3 Apply To Payroll

Preconditions:

- Employee has current job-step salary matching the handoff proposed salary.
- Effective date is inside the current open payroll period.
- Payroll company can be resolved.

Steps:

1. Click Apply to Payroll for a pending or acknowledged handoff, enter an optional note, and Confirm.

Expected result:

- Payroll recalculation is triggered through `PayrollChangeOrchestrator.recalculateForEmployee`.
- Handoff status becomes applied.
- Applied metadata is recorded.
- Retry of already applied handoff is idempotent.

Negative cases:

- Closed/out-of-period effective date blocks apply.
- Stale employee salary blocks apply.
- Missing payroll company blocks apply.

### 16.4 Cancel Handoff

1. Click Cancel for a pending or acknowledged handoff, enter an optional reason, and Confirm.

Expected result:

- Handoff status becomes cancelled.
- Cancel activity is recorded.

## 17. Reports And CSV Exports

HR: open Promotions > Reports (`/hr/promotions/reports`). Admin/Finance: use their role page with `?section=reports`.

1. Verify Status Summary, Department Summary, Salary Impact, Approval Duration, and Report Detail.
2. Set Start Date and End Date, then click Refresh. Verify summary data and detail rows against the test dataset.
3. Set Detail Status and Department and click Load Details. These filters apply to report detail, not all summary cards.
4. Use detail Previous/Next. API requests use zero-based `page` and `size=10`; the displayed page number is one-based. Verify an empty result page shows an explanatory message.
5. Test each Export CSV link. Current links point to the corresponding unfiltered summary `.csv` endpoint and do not automatically inherit on-screen date/status/department filters. Compare these downloads with the unfiltered API data.
6. To test filtered CSV exports, call the CSV endpoint explicitly with its supported `startDate` and `endDate` parameters using authenticated API tooling. Do not claim the UI export is filtered when those parameters are absent.
7. Test report API parameters separately: `startDate`, `endDate`, `status`, `departmentId`, `approvalStartDate`, `approvalEndDate`, `page`, and `size`, where supported. The UI does not expose separate approval-date inputs or a report page-size selector. Verify the server's maximum detail page-size clamp through API tests.

Request-list search and 15-row client pagination are independent of report detail filtering and pagination.

## 18. Configuration Testing

HR: open Promotions > Settings (`/hr/promotions/settings`). Admin: open `/admin/promotions?section=configuration`; its configuration remains on the shared role page.

1. Test every group and field listed in section 6.4.
2. Save changes across multiple groups using Save Configuration. Verify the Saved message, refresh/reopen, and compare persisted values.
3. Use Refresh to reload the active configuration. Unsaved edits are not expected to survive a full reload or page navigation.
4. Open Configuration history and click Refresh History. Verify policy name, active status, updated time, and updated-by data where available.
5. Test Deactivate on a known active policy and accept its confirmation. Verify the Deactivated feedback/history and active-policy behavior through API/service checks.
6. Confirm new requests use the active policy and existing workflow behavior follows the service rules.
7. Verify non-HR/Admin accounts cannot access HR Settings or mutate configuration through the API.
8. Test advanced `routingRules` through API/fixtures as described in section 9.4; there is no UI routing-rule editor.

## 19. Security And Visibility Testing

### 19.1 Operational Access

Verify:

- HR/Finance/Admin can access operational dashboard/list where allowed.
- Unauthorized employee cannot access operational list/dashboard.
- Reference employee/job-step endpoints require HR, Finance, or Admin authorization.

### 19.2 Scoped Employee/Requester Access

1. Sign in as promoted employee.
2. Open `/employee/promotions` (Requests).
3. Verify own promotion records are discoverable.
4. Sign in as requester and verify submitted records are discoverable.

Expected result:

- User sees only records where they are promoted employee or requester.
- Direct link to unrelated promotion detail is blocked.

### 19.3 Salary Visibility

Verify with:

- HR/Admin/Finance user.
- Assigned approver without salary permission.
- Promoted employee/requester.

Expected result:

- Salary and payroll fields are visible only when `canViewSalary` rules allow.
- Restricted users see null, hidden, or restricted salary fields.

## 20. Notification And Activity Testing

Check activity logs for:

- Draft creation.
- Submit.
- Eligibility override.
- Approval assignment.
- Approval decision.
- Rejection.
- Return.
- Cancellation.
- Reassignment/delegation.
- Reminder.
- Escalation.
- Implementation.
- Payroll handoff actions.
- Document upload and generated letters where activity is expected.

Expected result:

- Activity entries follow the service-provided order; compare timestamps and event sequence with the expected workflow history.
- Verify actor and complete audit metadata through authorized API/service records; verify description/type and timestamps in the Activity UI.
- Verify request IP, user agent, and device metadata through authorized API/service records when request context exists; the Activity UI renders timestamp and description/type, not every audit field.
- System/background activities still record without request context.

Notification hook checks:

- Submission assignment notification activity.
- Assigned approver email scheduling.
- Candidate-group email distribution.
- Missing email address skip behavior.
- Disabled channel behavior, if configurable in test environment.

## 21. End-To-End Scenario Matrix

| Scenario | Expected final state |
| --- | --- |
| Eligible promotion, all approvals, implementation | `IMPLEMENTED`, employee position updated, payroll handoff created if salary changed. |
| Missing submit document | Remains `DRAFT`, no workflow starts. |
| Warning eligibility override then submit | Proceeds after override if no blocker remains. |
| Blocker eligibility failure | Submission blocked. |
| Rejected by approver | `REJECTED`, no later approvals. |
| Returned by approver | Back to `DRAFT`, editable/resubmittable. |
| Cancel in-flight request | `CANCELLED`, Flowable process deleted. |
| Finance skipped by config | No finance step. |
| Executive required by threshold | Executive step included. |
| Routing rule overrides default route | Matched rule controls finance/executive steps. |
| Reassigned approval | Named approver must complete. |
| Overdue approval reminder | Reminder metadata and notification activity recorded. |
| Overdue approval escalation | Active approver/candidate group changes. |
| Implementation missing required doc | Implementation blocked. |
| Payroll handoff apply success | Handoff applied, payroll recalculated. |
| Payroll handoff stale salary | Apply blocked. |
| Unauthorized user access | Request blocked or sensitive fields hidden. |

## 22. Regression Commands

Focused navigation/UI regression (run from the repository root in PowerShell):

```powershell
.\mvnw.cmd "-Dtest=PromotionControllerTest,PromotionControllerSecurityTest,PromotionMainPageBrowserTest,PromotionMapperTest" test
```

This covers role routes and access denial, rendered HR page isolation, legacy redirects, existing workflow/document actions, page-specific loading, Settings group visibility/value retention, request search/pagination, and identifiers needed for draft editing. Browser tests use Playwright with stubbed API data; manual or integration testing is still needed for real backend workflows and visual review in the complete application.

Promotion-wide automated regression:

```text
.\mvnw.cmd "-Dtest=Promotion*Test" test
```

Full suite:

```text
.\mvnw.cmd test
```

Last recorded verification for the HR redesign (14 October 2026): the focused four-class command above passed **28 tests, 0 failures, 0 errors, 0 skipped**. This records that run, not a guaranteed count for later changes. The full suite was not rerun for the redesign; run it when signing off and attach the actual results rather than relying on an older full-suite baseline.

## 23. Tester Sign-Off Checklist

- [ ] HR/Admin can create and update draft promotion.
- [ ] Submit runs eligibility checks.
- [ ] Required submit documents are enforced.
- [ ] Warning eligibility override works with reason.
- [ ] Duplicate active promotion is blocked.
- [ ] Approval route matches configuration and routing rules.
- [ ] Line manager, HR, Finance, and Executive decisions work where applicable.
- [ ] Reject and return flows work.
- [ ] Cancel flow works for allowed statuses and blocks disallowed statuses.
- [ ] Reassignment and delegation work only for active pending step.
- [ ] Reminder and escalation metadata update when due.
- [ ] Approved request can be implemented.
- [ ] Required implementation documents are enforced.
- [ ] Employee current position and position history update after implementation.
- [ ] Salary-changing implementation creates payroll handoff.
- [ ] Payroll handoff acknowledge/apply/cancel works.
- [ ] Documents upload, download, remove, and generated PDF letters work.
- [ ] Reports and CSV exports match expected data.
- [ ] Configuration updates affect new requests.
- [ ] Employee/requester scoped visibility works.
- [ ] Unauthorized users are blocked.
- [ ] Salary masking works.
- [ ] Activity log and audit metadata are captured.
- [ ] HR submenu, active states, direct links, refresh, and child Requests routes work.
- [ ] Overview shortcuts, recent requests, and empty state work.
- [ ] HR pages exclude unrelated work-area panels and API loads.
- [ ] Request status/search combinations, 15-row pages, counts, and URL restoration work.
- [ ] Create, edit, cancel editing, and non-draft edit rejection work.
- [ ] Detail local sections and status-dependent action area work.
- [ ] Settings local groups hide inactive fields and preserve values while switching.
- [ ] Other roles retain their documented shared-page navigation and permissions.
- [ ] Legacy HR section links and `/promotion/...` entry points resolve correctly.
- [ ] Desktop/mobile layouts, table scrolling, keyboard focus, and dialog validation are usable.
- [ ] API-only override/routing/audit checks are recorded separately from UI checks.
- [ ] CSV results are compared with the filters actually sent by the link/API request.
- [ ] Automated results and outstanding defects are attached to sign-off.
