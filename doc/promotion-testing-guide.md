# Promotion Module End-to-End Testing Guide

## 1. Purpose

This guide helps QA testers validate the Promotion module end to end, from request creation through eligibility checks, approval workflow, implementation, documents, reports, payroll handoff, security, and audit history.

Use this guide for manual functional testing, UAT support, and regression testing after promotion-related changes.

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

## 3. Main URLs

Promotion pages:

```text
/promotion
/promotion/requests
/promotion/approvals
/promotion/reports
/promotion/configuration
/promotion/payroll-handoffs
```

Key API groups used by the UI:

```text
/api/promotions
/api/promotions/my
/api/promotions/dashboard
/api/promotions/my/dashboard
/api/promotions/tasks
/api/promotions/configuration
/api/promotions/reports/*
/api/promotions/payroll-handoffs
/api/promotions/reference/employees
/api/promotions/reference/job-steps
```

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
| Unauthorized Employee | Must not access operational promotion lists, configuration, references, salary data, or unrelated details. |

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

## 6. Navigation Smoke Test

1. Sign in as HR/Admin.
2. Open `/promotion`.
3. Confirm dashboard cards load:
   - Pending Approvals.
   - Approved.
   - Implemented.
   - Monthly Salary Impact.
4. Open each dedicated route:
   - `/promotion/requests`
   - `/promotion/approvals`
   - `/promotion/reports`
   - `/promotion/configuration`
   - `/promotion/payroll-handoffs`
5. Confirm each route focuses or scrolls to the expected panel.
6. Confirm tables, filters, action buttons, and forms render without layout breakage.

Expected result:

- No page error.
- Current user sees only panels/actions allowed by their role.
- Operational users can move between dashboard, requests, approvals, reports, configuration, and payroll handoffs.

## 7. Happy Path: HR Creates, Submits, Approves, Implements

### 7.1 Create Draft

1. Sign in as HR/Admin.
2. Open `/promotion/requests`.
3. Click `New Promotion`.
4. Select the employee.
5. Select proposed job step or role.
6. Enter effective date.
7. Enter justification.
8. Save the draft.

Expected result:

- Request is created with status `DRAFT`.
- Request number is generated.
- Detail view shows current role, proposed role, current/proposed department, salary impact, eligibility section, approval section, documents, and activity.
- Activity log records request creation.

### 7.2 Update Draft

1. Open the draft request.
2. Edit proposed role, effective date, salary, or justification.
3. Save changes.

Expected result:

- Draft updates successfully.
- Historical current values remain available on the request.
- Invalid fields show validation errors.

### 7.3 Attach Required Documents

1. Open the request detail.
2. Upload each document required before submission.
3. Refresh or reopen the detail view.

Expected result:

- Uploaded documents appear in the document list.
- Missing required document indicators update.
- Documents can be downloaded.
- Authorized HR/Admin can remove uploaded documents.

### 7.4 Submit Request

1. Click submit.
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
2. Open `/promotion/approvals`.
3. Confirm the task appears in `My Approval Tasks`.
4. Approve with a comment.
5. Repeat as HR, Finance, and Executive where configured.

Expected result:

- Each approval step moves from `PENDING` to `APPROVED`.
- Request status advances to the next stage.
- Final approval changes request status to `APPROVED`.
- Approval comments and acted timestamps are visible.
- Activity log records each decision.

### 7.6 Implement Approved Promotion

1. Sign in as HR/Admin.
2. Open the approved request.
3. Upload any documents required before implementation.
4. Click implement.
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
3. Override the failed check with a reason.
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

### 9.4 Advanced Routing Rules

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

1. Create a request where the requester is also in an approver role.
2. Attempt to approve as the requester.

Expected result:

- Self-approval is blocked where workflow authorization rules apply.

## 11. Reassignment And Delegation

### 11.1 Reassign Active Step

1. Sign in as HR/Admin.
2. Open a request with an active pending approval step.
3. Reassign the active step to another employee.
4. Sign in as the new approver.
5. Approve the task.

Expected result:

- Approval step shows original approver and reassignment metadata.
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

1. Configure reminder timing.
2. Create a pending approval older than the reminder threshold.
3. Run or wait for scheduled reminder processing.

Expected result:

- Reminder count increments.
- Last reminder timestamp is set.
- Notification activity is recorded.
- Duplicate reminder is suppressed until due again.

### 12.2 Escalation To Employee

1. Configure escalation timing and escalation employee.
2. Create overdue pending approval.
3. Run or wait for escalation processing.

Expected result:

- Pending approval step moves to escalation employee.
- Active Flowable task assignee updates.
- Escalation metadata and activity are recorded.

### 12.3 Escalation To Group

1. Configure escalation group.
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

1. Upload valid document type and file.
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

1. Open a request detail.
2. Generate promotion letter.
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
2. Open `/promotion/payroll-handoffs`.

Expected result:

- Pending handoffs are visible.
- Employee, request, effective date, salary change, and status are shown.

### 16.2 Acknowledge

1. Click acknowledge on a pending handoff.

Expected result:

- Handoff status changes as expected.
- Activity is recorded.

### 16.3 Apply To Payroll

Preconditions:

- Employee has current job-step salary matching the handoff proposed salary.
- Effective date is inside the current open payroll period.
- Payroll company can be resolved.

Steps:

1. Click `Apply to Payroll`.

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

1. Cancel a pending handoff.

Expected result:

- Handoff status becomes cancelled.
- Cancel activity is recorded.

## 17. Reports And CSV Exports

Open `/promotion/reports` and verify:

- Status summary.
- Department summary.
- Salary impact.
- Approval duration.
- Report detail drill-down and pagination.

Test filters:

```text
startDate=YYYY-MM-DD
endDate=YYYY-MM-DD
status
departmentId
approvalStartDate=YYYY-MM-DD
approvalEndDate=YYYY-MM-DD
page
size
```

Expected result:

- Report counts and totals match test data.
- Date filters restrict results correctly.
- CSV exports download and match filtered data.
- Detail report page size is clamped to maximum allowed size.

## 18. Configuration Testing

Sign in as HR/Admin and open `/promotion/configuration`.

Validate:

- Minimum appraisal score.
- Minimum months in role.
- Probation promotion setting.
- Finance approval enable/disable.
- Executive approval enable/disable.
- Executive salary threshold.
- Required documents before submit.
- Required documents before implementation.
- Routing rules.
- Reminder timing.
- Escalation timing and target employee/group.
- Configuration history.
- Deactivate configuration where available.

Expected result:

- Save persists values.
- New requests use the active configuration.
- Non-HR/Admin users cannot update configuration.

## 19. Security And Visibility Testing

### 19.1 Operational Access

Verify:

- HR/Finance/Admin can access operational dashboard/list where allowed.
- Unauthorized employee cannot access operational list/dashboard.
- Reference employee/job-step endpoints require HR, Finance, or Admin authorization.

### 19.2 Scoped Employee/Requester Access

1. Sign in as promoted employee.
2. Open Promotion module.
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

- Activity entries are chronological.
- Actor, description, timestamps, and relevant metadata are visible.
- Request IP, user agent, and device metadata appear when request context exists.
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

Promotion-focused automated test command:

```text
mvnw.cmd "-Dtest=PromotionBpmnDefinitionTest,PromotionControllerTest,PromotionMainPageBrowserTest,PromotionApiControllerTest,PromotionMapperTest,PromotionApprovalDecisionServiceTest,PromotionApprovalReassignmentServiceTest,PromotionApprovalReminderServiceTest,PromotionApprovalRouteServiceTest,PromotionAuthorizationServiceTest,PromotionConfigurationServiceTest,PromotionEligibilityServiceTest,PromotionEligibilityOverrideServiceTest,PromotionImplementationServiceTest,PromotionAttachmentServiceTest,PromotionDocumentIntegrationServiceTest,PromotionCancellationServiceTest,PromotionWorkflowCancellationServiceTest,PromotionPayrollHandoffServiceTest,PromotionSalaryImpactServiceTest,PromotionReportServiceTest,PromotionWorkflowRuntimeTest,PromotionWorkflowServiceAuthorizationTest,PromotionSubmissionServiceTest,PromotionNotificationServiceTest,PromotionEmailServiceTest,PromotionRequiredDocumentServiceTest,PromotionRequesterVisibilityServiceTest,PromotionSecurityTest,PromotionActivityServiceTest" test
```

Full suite:

```text
mvnw.cmd test
```

Expected current baseline:

```text
Promotion targeted suite: PASS
Full suite: PASS, 329 tests, 0 failures, 0 errors, 1 skipped
```

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
- [ ] Promotion page navigation is usable across all dedicated routes.
