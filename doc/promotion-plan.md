# Promotion Module Implementation Plan

## 1. Objective

The Promotion module manages employee promotion requests from HR draft creation through eligibility checks, approval workflow, implementation, dashboard visibility, and audit history.

The current codebase contains a functional Promotion MVP backed by database entities and a Flowable BPMN approval process. This document is aligned to the code currently present under:

```text
src/main/java/com/justjava/humanresource/promotion
src/main/resources/processes/promotion-approval-process.bpmn20.xml
src/test/java/com/justjava/humanresource/promotion
```

Overall status: the Promotion MVP is functional and backend-heavy implementation is largely complete. Core request
creation, eligibility, approval workflow, implementation, dashboard, configuration, authorization, attachments,
notification activity hooks, salary-impact preview, route preview, eligibility overrides, generated PDF letters, report APIs and CSV exports,
payroll handoff records and lifecycle actions,
date-ranged report filtering, runtime workflow tests, submit/duplicate-request coverage, after-commit promotion email delivery, dedicated
promotion page routes, required-document configuration, submit/implementation document enforcement, missing-document detail visibility, and
main-page document requirement controls, scoped requester/employee promotion lists, scoped requester/employee dashboard metrics,
browser-level promotion page coverage, local promotion letter template registry, generated-letter template metadata,
document-library sync for promotion attachments, controlled approval reassignment/delegation, scheduled approval
reminder/escalation processing, method-level permission guards, request-context audit metadata, and paged report drill-downs are implemented.
Implementation retries now also reconcile clearly partial implemented states by restoring employee assignment only
when the employee is still at the original promotion baseline, and by re-checking payroll handoff creation for
already-implemented salary-changing requests.

The remaining work is no longer foundational MVP work. It is mainly product hardening and expansion. Recent hardening
now covers promotion reference endpoint authorization, separate salary visibility rules, employee/requester own-record
detail visibility, date-ranged report filtering, guarded payroll handoff application through payroll recalculation, and
position-history-based time-in-role eligibility, controlled approval reassignment/delegation, scheduled approval
reminder/escalation behavior, candidate-group email distribution, a notification-channel abstraction, framework-level
method security around promotion controllers and sensitive service entry points, request-context audit metadata, and paged report detail filtering.
Remaining expansion items include concrete non-email notification channels and deeper task-focused UX polish. The previously
known non-promotion full-suite failures have been cleaned up.

## 2. Current Implementation Summary

Implemented:

- HR/admin can create promotion drafts.
- HR/admin can update draft requests.
- HR/admin can submit draft requests.
- Submission evaluates eligibility checks.
- HR/admin can override failed warning-level eligibility checks with a reason.
- Submission creates persisted approval steps.
- Submission starts Flowable process key `promotionApprovalProcess`.
- `PromotionRequest.processInstanceId` stores the Flowable process instance id.
- Flowable task decisions can approve, reject, or return a request.
- Direct request-id approval endpoints complete the active Flowable task internally.
- HR/admin can reassign or delegate the active pending approval step to a named employee.
- Reassignment/delegation updates the persisted approval step and active Flowable task assignee.
- Reassigned/delegated approval steps restrict task completion to the named approver.
- Scheduled approval reminder processing can notify overdue pending approvers.
- Scheduled approval escalation processing can move overdue pending approvals to a configured employee or candidate group.
- HR/admin can cancel draft and in-flight promotion requests.
- Cancellation deletes the active Flowable process instance when one exists.
- Approval decisions are persisted to `PromotionApprovalStep`.
- Approval activity is recorded in `PromotionActivityLog`.
- Approved requests can be implemented by HR/admin.
- Implementation updates employee current job step and department.
- Implementation closes existing current `EmployeePositionHistory` and creates a new current history row.
- Implementation now runs through `PromotionImplementationService`.
- Implementation checks for existing same-date promotion history to avoid duplicate history on retry.
- Implementation validates that employee role and department still match the request baseline before applying a new position history row.
- Already-implemented retry calls re-check salary-change payroll handoff creation and reconcile only baseline-still-current employee assignments.
- Dashboard, request list, request detail, salary-impact preview, promotion report, date-ranged report filtering, report CSV export, payroll handoff, reference employee lookup, reference job-step lookup, task inbox, and configuration APIs exist.
- Requesters and promoted employees can list and dashboard the promotion records they are allowed to discover without direct links.
- Finance and executive approval routing uses `PromotionConfiguration`, with default fallback behavior when no active configuration exists.
- Ordered promotion routing rules can override finance/executive approval routing by proposed department, proposed job grade,
  employee group/category, and request source.
- Promotion view/action rules are centralized in `PromotionAuthorizationService`.
- `AuthenticationManager` includes promotion-relevant group checks for HR, Finance, Executive, and Admin.
- The promotion page at `templates/promotion/main.html` is API-backed and includes a basic approval task queue, report panels with CSV export links, and a configuration panel.
- Dedicated promotion routes reuse that page and open the relevant panel for requests, approvals, reports, configuration, and payroll handoffs.
- Promotion document attachments can be uploaded, listed, downloaded, and removed.
- Promotion and salary-adjustment letters can be generated as downloadable PDF attachments.
- Generated promotion and salary-adjustment letters use a local template registry and store template key, name, and version metadata.
- Uploaded and generated promotion attachments are synced best-effort into the employee document library.
- Promotion attachment responses expose document-library sync id, sync timestamp, and sync error metadata.
- Promotion detail responses and the promotion page include uploaded documents.
- Notification hook activity is recorded for submission, approval assignment, final approval, rejection, return, cancellation, and implementation.
- Runtime Flowable integration tests cover approval routing, rejection, return, cancellation, finance-skip,
  executive-required, and unauthorized task-completion guards.
- Submit orchestration and duplicate active-request blocking are covered by focused service tests.
- Promotion notification hooks now schedule best-effort email delivery after transaction commit.
- Implementing a salary-changing promotion now creates a pending payroll handoff record.

Partially implemented:

- Configuration exists for finance/executive routing, advanced routing rules, eligibility checks, and required documents.
  Appraisal score, time-in-role, and probation eligibility now use the active configuration with default fallbacks.
- Approval route configuration supports finance enable/disable, executive enable/disable, executive salary-increase threshold,
  ordered routing-rule overrides for department, grade, employee group/category, request source, reminder timing, and
  escalation timing/target settings.
- Approval route preview is available for existing promotion requests and includes matched routing-rule names.
- Flowable task inbox exists, but the UX is still basic.
- Line-manager task assignment exists when a current manager is available.
- Approval authorization is enforced through `PromotionAuthorizationService`, including task-stage checks, self-approval prevention,
  and named approver enforcement after reassignment/delegation.
- Employee/requester scoped list and dashboard APIs use current employee context and preserve salary field masking through existing mapper rules.
- Implementation writes employee job/position data with retry and baseline-conflict guards, and creates a payroll handoff record for salary changes. Payroll handoffs can be acknowledged, applied to payroll through `PayrollChangeOrchestrator.recalculateForEmployee`, or cancelled.
- Promotion emails are sent for employee status notifications and specifically assigned approvers. Candidate-group
  approval notifications are sent to active employees in the relevant approver group.
- Promotion notification delivery now uses a channel abstraction, with email as the implemented channel.
- Reference employee and job-step endpoints require HR, Finance, or Admin authorization before returning employee,
  job, and salary reference data.
- Salary values are included only when `PromotionAuthorizationService.canViewSalary()` allows salary visibility. Users
  who can view a promotion only as assigned approvers receive null salary/payroll fields, and the page renders those
  fields as restricted.

Not implemented:

- Flowable-native reminder and escalation delegates. Reminder/escalation currently runs through the scheduled promotion service.

## 3. Implemented Endpoints

REST API:

```text
GET    /api/promotions/dashboard
GET    /api/promotions/my/dashboard
GET    /api/promotions/reports/status-summary
GET    /api/promotions/reports/department-summary
GET    /api/promotions/reports/salary-impact
GET    /api/promotions/reports/approval-duration
GET    /api/promotions/reports/details
GET    /api/promotions/reports/status-summary.csv
GET    /api/promotions/reports/department-summary.csv
GET    /api/promotions/reports/salary-impact.csv
GET    /api/promotions/reports/approval-duration.csv
GET    /api/promotions/payroll-handoffs
POST   /api/promotions/payroll-handoffs/{handoffId}/acknowledge
POST   /api/promotions/payroll-handoffs/{handoffId}/apply
POST   /api/promotions/payroll-handoffs/{handoffId}/cancel
GET    /api/promotions
GET    /api/promotions/my
POST   /api/promotions
POST   /api/promotions/salary-impact
GET    /api/promotions/{id}
GET    /api/promotions/{id}/approval-route-preview
PUT    /api/promotions/{id}
POST   /api/promotions/{id}/submit
POST   /api/promotions/{id}/approve
POST   /api/promotions/{id}/reject
POST   /api/promotions/{id}/return
POST   /api/promotions/{id}/eligibility-checks/{checkId}/override
POST   /api/promotions/{id}/cancel
POST   /api/promotions/{id}/implement
GET    /api/promotions/{id}/attachments
POST   /api/promotions/{id}/attachments
POST   /api/promotions/{id}/attachments/generate-letter
GET    /api/promotions/{id}/attachments/{attachmentId}
DELETE /api/promotions/{id}/attachments/{attachmentId}
GET    /api/promotions/tasks
POST   /api/promotions/tasks/{taskId}/approve
POST   /api/promotions/tasks/{taskId}/reject
POST   /api/promotions/tasks/{taskId}/return
POST   /api/promotions/{id}/approval-steps/{stepId}/reassign
POST   /api/promotions/{id}/approval-steps/{stepId}/delegate
GET    /api/promotions/configuration
GET    /api/promotions/configuration/history
PUT    /api/promotions/configuration
POST   /api/promotions/configuration/{configurationId}/deactivate
GET    /api/promotions/reference/employees
GET    /api/promotions/reference/job-steps
```

Report endpoints and CSV exports accept optional ISO date query parameters:

```text
startDate=YYYY-MM-DD
endDate=YYYY-MM-DD
```

The report detail endpoint also accepts:

```text
status
departmentId
approvalStartDate=YYYY-MM-DD
approvalEndDate=YYYY-MM-DD
page
size
```

Page controller:

```text
GET /promotion
GET /promotion/requests
GET /promotion/approvals
GET /promotion/reports
GET /promotion/configuration
GET /promotion/payroll-handoffs
```

These routes share `templates/promotion/main.html` and focus the relevant panel on page load.

## 4. Current Verification Status

Targeted promotion tests currently pass:

```text
mvnw.cmd "-Dtest=PromotionBpmnDefinitionTest,PromotionControllerTest,PromotionMainPageBrowserTest,PromotionApiControllerTest,PromotionMapperTest,PromotionApprovalDecisionServiceTest,PromotionApprovalReassignmentServiceTest,PromotionApprovalReminderServiceTest,PromotionApprovalRouteServiceTest,PromotionAuthorizationServiceTest,PromotionConfigurationServiceTest,PromotionEligibilityServiceTest,PromotionEligibilityOverrideServiceTest,PromotionImplementationServiceTest,PromotionAttachmentServiceTest,PromotionDocumentIntegrationServiceTest,PromotionCancellationServiceTest,PromotionWorkflowCancellationServiceTest,PromotionPayrollHandoffServiceTest,PromotionSalaryImpactServiceTest,PromotionReportServiceTest,PromotionWorkflowRuntimeTest,PromotionWorkflowServiceAuthorizationTest,PromotionSubmissionServiceTest,PromotionNotificationServiceTest,PromotionEmailServiceTest,PromotionRequiredDocumentServiceTest,PromotionRequesterVisibilityServiceTest,PromotionSecurityTest,PromotionActivityServiceTest" test
```

Current targeted result:

```text
PASS: 139 tests, 0 failures
```

The broader full-suite status is now passing after the non-promotion cleanup milestone:

```text
PASS: mvnw.cmd test
PASS: 329 tests, 0 failures, 0 errors, 1 skipped
```

Previously failing unrelated areas now pass:

- `DocumentLibraryServiceTest`
- `EmployeeExitAuthorizationServiceTest`
- `FamAssetLookupServiceTest`
- `PayrollPeriodServiceImplTest`

## 5. Domain Model

### 5.1 PromotionRequest

Implemented entity: `PromotionRequest`

Current fields:

```text
id
requestNumber
processInstanceId
employee
requestedBy
requestSource
currentRole
proposedRole
currentDepartment
proposedDepartment
currentManager
proposedManager
currentSalary
proposedSalary
salaryIncreaseAmount
salaryIncreasePercent
monthlyPayrollImpact
annualPayrollImpact
appraisal
justification
appraisalScore
status
effectiveDate
lastApprovalComment
rejectionReason
implementationComment
submittedAt
requestedAt
approvedAt
rejectedAt
cancelledAt
implementedAt
```

Current gaps:

- No `createdAt`, `updatedAt`, `createdBy`, or `updatedBy` fields on this entity.
- No separate `appraisalCycle` field.
- No separate `hrComment` or `financeComment` fields.
- Salary is captured on the request, but implementation does not yet apply payroll-specific salary changes outside employee job/position updates.
- Salary-changing implementations create `PromotionPayrollHandoff` records for payroll follow-up.

### 5.2 PromotionStatus

Implemented statuses:

```text
DRAFT
SUBMITTED
ELIGIBILITY_REVIEW
LINE_MANAGER_REVIEW
HR_REVIEW
FINANCE_REVIEW
EXECUTIVE_REVIEW
APPROVED
REJECTED
CANCELLED
IMPLEMENTED
```

`APPROVED` and `IMPLEMENTED` are intentionally separate:

- `APPROVED` means the approval workflow has completed.
- `IMPLEMENTED` means HR/admin has applied the promotion to employee job/position records.

### 5.3 PromotionApprovalStep

Implemented entity: `PromotionApprovalStep`

Current fields:

```text
id
promotionRequest
stage
decision
approver
approverGroup
sequenceNumber
assignedAt
actedAt
flowableTaskId
comment
originalApprover
reassignedBy
delegatedBy
escalatedToApprover
escalatedToGroup
reassignedAt
delegatedAt
lastReminderSentAt
reminderCount
escalatedAt
reassignmentComment
delegationComment
escalationComment
```

Implemented stages:

```text
LINE_MANAGER
HR
FINANCE
EXECUTIVE
```

Implemented decisions:

```text
PENDING
APPROVED
REJECTED
RETURNED
```

Current behavior:

- HR/admin can reassign the active pending step to a named approver.
- HR/admin can delegate the active pending step to a named approver.
- The active Flowable task assignee is updated with the named approver employee id.
- Reassigned/delegated approval steps preserve original approver metadata and expose reassignment/delegation metadata in detail responses.
- Named approver enforcement prevents another user in the candidate group from completing a reassigned/delegated step.
- Reminder processing stores the last reminder timestamp and reminder count on the pending step.
- Escalation processing stores the escalated employee/group, escalation timestamp, and escalation comment on the pending step.

### 5.4 PromotionEligibilityCheck

Implemented entity: `PromotionEligibilityCheck`

Current checks from `PromotionEligibilityService`:

```text
EMPLOYEE_ACTIVE
NOT_PROBATION
ROLE_CHANGED
NO_DUPLICATE_ACTIVE_REQUEST
APPRAISAL_SCORE
TIME_IN_ROLE
EFFECTIVE_DATE
```

Implemented severities:

```text
INFO
WARNING
BLOCKER
```

Current behavior:

- Blocker failures prevent submission.
- Warning failures are recorded and marked override-allowed.
- HR/admin can persist an override reason for failed override-allowed checks.
- Minimum appraisal score comes from `PromotionConfiguration.minimumAppraisalScore`, defaulting to `70.00`.
- Minimum months in role comes from `PromotionConfiguration.minimumMonthsInCurrentRole`, defaulting to `6`.
- Probation eligibility comes from `PromotionConfiguration.allowProbationPromotion`, defaulting to `false`.

### 5.5 PromotionConfiguration

Implemented entity: `PromotionConfiguration`

Current fields:

```text
id
name
minimumMonthsInCurrentRole
minimumAppraisalScore
allowProbationPromotion
requireFinanceApproval
requireExecutiveApproval
maximumSalaryIncreasePercentWithoutExecutiveApproval
approvalReminderAfterHours
approvalEscalationAfterHours
escalationApproverId
escalationApproverGroup
requiredDocumentsBeforeSubmit
requiredDocumentsBeforeImplementation
routingRules
active
createdAt
updatedAt
createdByName
updatedByName
deactivatedAt
deactivatedByName
```

Current behavior:

- `PromotionApprovalRouteService` reads the active configuration.
- If no active configuration exists, a default in-memory configuration is used.
- Default executive approval threshold is `30.00`.
- Finance approval is required only when enabled and salary increase amount is greater than zero.
- Executive approval is required only when enabled and salary increase percent is greater than or equal to the configured threshold.
- Eligibility evaluation uses minimum appraisal score, minimum months in role, and probation promotion settings from the active configuration.
- Required document lists define which attachment types must exist before submission and implementation.
- Routing rules define ordered finance/executive approval overrides for matching department, grade, employee group/category, and source criteria.
- Reminder and escalation timing settings define when pending approval steps are notified and escalated.
- Escalation can target either a named employee id or a candidate group.
- HR/admin can read and update active configuration through `/api/promotions/configuration`.
- HR/admin can review configuration history through `/api/promotions/configuration/history`.
- HR/admin can deactivate a configuration through `/api/promotions/configuration/{configurationId}/deactivate`.

Current gaps:

- No report configuration fields.
- No configuration audit activity entry beyond configuration metadata.

### 5.6 PromotionActivityLog

Implemented entity: `PromotionActivityLog`

Current fields:

```text
id
promotionRequest
actor
activityType
description
ipAddress
userAgent
deviceMetadata
createdAt
```

Implemented activity types:

```text
CREATED
UPDATED
SUBMITTED
ELIGIBILITY_CHECKED
ELIGIBILITY_OVERRIDDEN
APPROVED
REJECTED
RETURNED
CANCELLED
IMPLEMENTED
PAYROLL_HANDOFF_CREATED
PAYROLL_HANDOFF_ACKNOWLEDGED
PAYROLL_HANDOFF_APPLIED
PAYROLL_HANDOFF_CANCELLED
DOCUMENT_UPLOADED
DOCUMENT_REMOVED
APPROVAL_REASSIGNED
APPROVAL_DELEGATED
APPROVAL_REMINDER_SENT
APPROVAL_ESCALATED
NOTIFICATION_SENT
```

Current behavior:

- Request-backed activity records capture IP address, user agent, and coarse device metadata.
- Background and system activity records remain supported with null request metadata.
- Promotion detail activity responses expose the audit metadata.

### 5.7 PromotionNumberCounter

Implemented entity: `PromotionNumberCounter`

Current use:

- `PromotionNumberServiceImpl` generates promotion request numbers.

### 5.8 PromotionAttachment

Implemented entity: `PromotionAttachment`

Current fields:

```text
id
promotionRequest
documentType
fileName
contentType
storageReference
uploadedBy
uploadedAt
required
fileSize
generatedTemplateKey
generatedTemplateName
generatedTemplateVersion
documentLibraryDocumentId
documentLibrarySyncedAt
documentLibrarySyncError
```

### 5.9 PromotionPayrollHandoff

Implemented entity: `PromotionPayrollHandoff`

Current fields:

```text
id
promotionRequest
employee
payGroup
effectiveDate
currentSalary
proposedSalary
monthlyPayrollImpact
annualPayrollImpact
status
createdBy
appliedBy
createdAt
acknowledgedAt
appliedAt
note
```

Implemented statuses:

```text
PENDING
ACKNOWLEDGED
APPLIED
CANCELLED
```

Current behavior:

- Salary-changing implemented promotions create one pending payroll handoff record.
- Duplicate handoff creation is skipped when a handoff already exists for the promotion request.
- Handoffs can be listed through `/api/promotions/payroll-handoffs`.
- Pending handoffs can be acknowledged.
- Pending or acknowledged handoffs can be externally confirmed as applied.
- Pending or acknowledged handoffs can be cancelled.
- Apply confirmation validates the handoff effective date against the employee department company's open payroll period.
- Apply confirmation stores the employee who confirmed application in `appliedBy`.

Current gaps:

- Direct payroll application uses `PayrollChangeOrchestrator.recalculateForEmployee` after open-period and salary-target validation. Response payloads expose `externalApplicationOnly=false`.

## 6. Repository Layer

Implemented repositories:

```text
PromotionRequestRepository
PromotionApprovalStepRepository
PromotionEligibilityCheckRepository
PromotionActivityLogRepository
PromotionNumberCounterRepository
PromotionConfigurationRepository
PromotionAttachmentRepository
PromotionPayrollHandoffRepository
```

Not implemented:

```text
PromotionWorkflowTaskRepository
```

`PromotionWorkflowTaskRepository` should only be added if the product needs a local task projection. Current task lookup uses Flowable directly.

## 7. Service Layer

### 7.1 PromotionService

Implemented interface:

```text
createDraft(command)
updateDraft(id, command)
submit(id)
approve(id, command)
reject(id, command)
returnForCorrection(id, command)
cancel(id, command)
implement(id, command)
salaryImpact(command)
getDetail(id)
list(status)
myPromotions(status)
dashboard()
myDashboard()
```

Implemented by `PromotionServiceImpl`.

Current responsibilities:

- Create and update drafts.
- Load employee current role, department, manager, salary, and latest finalized appraisal.
- Calculate salary impact on draft/update and through a preview endpoint.
- Run eligibility evaluation.
- Submit requests.
- Create approval routes through `PromotionApprovalRouteService`.
- Start Flowable approval process through `PromotionWorkflowService`.
- Complete direct request-id approval actions by resolving the active Flowable task.
- Cancel draft and in-flight requests.
- Implement approved promotions.
- Return request detail, list, and dashboard summaries.
- Return current employee/requester scoped list and dashboard summaries.
- Enforce HR/admin access for creation, submission, updates, and implementation.
- Enforce broader HR/Finance/Admin access for operational list and dashboard.
- Require current employee context for scoped requester/employee list and dashboard.
- Allow pending assigned line-manager approvers to view request detail.

Current gaps:

- Attachment methods are handled by `PromotionAttachmentService`, not the core `PromotionService`.
- Promotion report methods are handled by `PromotionReportService`, not the core `PromotionService`.
- Implementation orchestration delegates to `PromotionImplementationService`.

### 7.2 PromotionEligibilityService

Implemented methods:

```text
evaluate(request)
assertCanSubmit(request)
```

Current gaps:

- Does not check disciplinary status.
- Does not check active exit process.
- Does not validate role hierarchy transitions beyond requiring a different role.
- Time-in-role uses the current `EmployeePositionHistory.effectiveFrom` date, with employee hire date only as a
  fallback when no current position history exists.

### 7.3 PromotionApprovalRouteService

Implemented method:

```text
createApprovalRoute(request)
```

Current route:

```text
optional LINE_MANAGER -> HR -> optional FINANCE -> optional EXECUTIVE
```

Current route rules:

- Line manager step is created when current manager exists and is not the promoted employee.
- HR step is always created.
- Finance step is created when active configuration requires finance approval and salary increase amount is greater than zero.
- Executive step is created when active configuration requires executive approval and salary increase percent meets the configured threshold.
- Matched ordered routing rules can override finance/executive step inclusion.
- Routing rules can match proposed department, proposed job grade, employee group/category, and request source.
- Lower priority numbers are evaluated first for finance/executive overrides.

Current gaps:

- No escalation rules.

### 7.4 PromotionWorkflowService

Implemented interface:

```text
startApproval(request)
getMyTasks()
approve(taskId, command)
reject(taskId, command)
returnForCorrection(taskId, command)
cancelApproval(request, reason)
activeTaskId(request)
assignActiveTask(request, approverId)
assignActiveTaskToGroup(request, approverGroup)
```

Implemented by `PromotionWorkflowServiceImpl`.

Current responsibilities:

- Start `promotionApprovalProcess`.
- Build process variables.
- Resolve the current user's Flowable task list.
- Complete Flowable tasks with approval variables.
- Enforce workflow task authorization.
- Prevent unauthorized stage decisions and self-approval cases.
- Resolve active task for direct request-id endpoints.
- Assign the active Flowable task to a reassigned/delegated named approver.
- Assign the active Flowable task to an escalated candidate group.
- Delete an active approval process when a request is cancelled.

Current gaps:

- Reminder/escalation is scheduled service-based rather than BPMN timer-based.
- Runtime process tests cover full process progression with in-memory Flowable/H2 execution.
- No local task projection.

### 7.5 PromotionApprovalDecisionService

Implemented responsibilities:

- Record the current pending `PromotionApprovalStep`.
- Persist decision, comment, acted timestamp, and Flowable task id.
- Update request status to the next pending approval stage.
- Finalize approval.
- Finalize rejection.
- Return requests to draft for correction.
- Record approval/rejection/return activity.

### 7.6 PromotionConfigurationService

Implemented methods:

```text
getActiveConfiguration()
history()
saveActiveConfiguration(command)
deactivate(id)
```

Current responsibilities:

- Enforce HR/admin access.
- Return active configuration or default fallback.
- Return configuration history.
- Update the active configuration.
- Deactivate a configuration.
- Apply default executive threshold when omitted.

Current gaps:

- No audit activity entry when configuration changes.

### 7.7 Other Promotion Services

Implemented:

```text
PromotionActivityService
PromotionApprovalDecisionService
PromotionApprovalReassignmentService
PromotionApprovalReminderService
PromotionApprovalRouteService
PromotionAttachmentService
PromotionAuthorizationService
PromotionConfigurationService
PromotionCurrentUserService
PromotionDocumentIntegrationService
PromotionEligibilityService
PromotionImplementationService
PromotionLetterTemplateService
PromotionMapper
PromotionEmailService
PromotionEmailNotificationChannel
PromotionNotificationService
PromotionNotificationChannel
PromotionNumberService
PromotionNumberServiceImpl
PromotionPayrollHandoffService
PromotionReportService
PromotionService
PromotionServiceImpl
PromotionWorkflowService
PromotionWorkflowServiceImpl
```

## 8. Flowable BPMN Workflow

Implemented process file:

```text
src/main/resources/processes/promotion-approval-process.bpmn20.xml
```

Implemented process key:

```text
promotionApprovalProcess
```

Current process variables include:

```text
promotionRequestId
requestNumber
employeeId
employeeName
salaryIncreaseAmount
salaryIncreasePercent
approvalDecision
approvalComment
approvalActorId
approvalStage
flowableTaskId
requiresLineManagerApproval
lineManagerEmployeeId
requiresFinanceApproval
requiresExecutiveApproval
```

Implemented delegates:

```text
InitializePromotionApprovalDelegate
EvaluatePromotionEligibilityDelegate
ProcessPromotionApprovalDecisionDelegate
FinalizePromotionApprovalDelegate
FinalizePromotionRejectionDelegate
ReturnPromotionForCorrectionDelegate
```

Not currently implemented as Flowable delegates:

```text
EscalatePromotionApprovalDelegate
NotifyPromotionApproverDelegate
```

Notification hooks are implemented in `PromotionNotificationService` as persisted `NOTIFICATION_SENT` activity log
entries plus after-commit email delivery through `PromotionEmailService`. Candidate-group approval email delivery is
implemented through the notification-channel abstraction. Flowable notification delegates and concrete non-email
channels remain future work.

Promotion implementation is intentionally outside BPMN for the MVP:

- Flowable owns the approval process through `APPROVED`.
- HR/admin implementation is a separate explicit action through `POST /api/promotions/{id}/implement`.

Current workflow:

```text
Start
  -> Initialize Promotion Approval
  -> Evaluate Promotion Eligibility
  -> optional Line Manager User Task
  -> HR User Task
  -> optional Finance User Task
  -> optional Executive User Task
  -> Finalize Approval, Finalize Rejection, or Return For Correction
  -> End
```

Implementation remains outside BPMN:

- Flowable handles approval through `APPROVED`.
- `POST /api/promotions/{id}/implement` applies the approved promotion.

## 9. Frontend Status

Implemented:

- `src/main/resources/templates/promotion/main.html`
- API-backed dashboard cards.
- API-backed request list.
- Create/edit promotion request form flow.
- Detail view with eligibility, approval steps, activities, and actions.
- Basic "My Approval Tasks" queue.
- Configuration form backed by `/api/promotions/configuration`.
- Configuration form includes reminder and escalation timing/target controls.
- Reference API calls for employees and job steps.
- Report panels for status summary, department summary, salary impact, and approval duration, with CSV export links. Report APIs and CSV exports support optional effective-date range parameters.
- Dedicated page routes for requests, approvals, reports, configuration, and payroll handoffs.
- Document upload, download, and removal controls on the request detail view.
- Promotion detail includes approval route preview.
- Payroll handoff queue includes acknowledge, apply, and cancel actions.
- Payroll handoff apply action is labelled as payroll application in the UI.

Current gaps:

- Task inbox UX is still basic.

## 10. Security And Authorization

Current implementation:

- Uses `PromotionAuthorizationService` over `AuthenticationManager` group checks.
- HR/admin can create, update, submit, configure, and implement.
- HR/Finance/Admin can access list/dashboard.
- Executive users can view details.
- Promoted employees can view their own promotion detail/history.
- Requesters can view promotion details they submitted.
- Current pending line-manager approvers can view assigned promotion detail.
- Flowable task completion validates the current user/group against the expected stage.
- Flowable task completion blocks self-approval.
- Flowable task completion enforces a named approver when an active step is reassigned or delegated.
- Salary visibility is separated through `PromotionAuthorizationService.canViewSalary()`.
- Promotion reference endpoints require HR, Finance, or Admin authorization.
- Spring method security is enabled.
- Promotion page routes and REST API routes have coarse `@PreAuthorize` guards.
- Sensitive promotion service entry points have method-level guards for HR/admin management actions,
  HR/Finance/Admin operational reads, and salary-data reads.
- `PromotionAuthorizationService` remains responsible for request-specific visibility, self-approval prevention,
  and assigned-approver rules.

## 11. Integration Points

Implemented integrations:

- Employee module: employee lookup, current job step, department, manager, employment status, salary source.
- Job structure module: proposed job-step lookup and salary source.
- KPI module: latest finalized appraisal and appraisal score.
- Flowable workflow: BPMN process, task completion, user/group routing.
- Employee position history: creates a new current position history row on implementation.

Partial or missing integrations:

- Payroll module: salary impact is calculated and pending payroll handoff records are created with acknowledge/apply/cancel lifecycle actions. The apply action validates the open payroll period and employee salary target, then triggers `PayrollChangeOrchestrator.recalculateForEmployee`.
- Document module: local promotion attachments, generated PDF letters, local template registry, generated-letter metadata,
  and best-effort employee document-library sync are implemented.
- Notification/communication module: notification events are persisted and email delivery is scheduled after commit.
  Assigned-approver and candidate-group approval emails are implemented. A notification-channel abstraction exists
  for future non-email channels.
- Audit module: promotion activity log captures request IP, user agent, and coarse device metadata when request context exists.

## 12. Validation Rules

Implemented submission validation:

- Request must be `DRAFT`.
- Employee must be active or confirmed.
- Employee should not be on probation unless `PromotionConfiguration.allowProbationPromotion` is enabled.
- Proposed role must differ from current role.
- Employee must not have another active promotion request.
- Appraisal score should meet the configured minimum score, defaulting to `70.00`.
- Employee should have at least the configured minimum months in role, defaulting to `6`.
- Effective date must be today or future.
- Any failed blocker check prevents submission.

Known validation note:

- The minimum-time-in-role check uses current position history when available and falls back to hire date only when
  position history is missing.

Implemented approval validation:

- Flowable task must exist.
- Current user must be authorized for the task stage.
- Self-approval is blocked where enforced by workflow service.
- Rejection and return actions require decision commands at API level.

Implemented cancellation validation:

- HR/admin access is required.
- Draft, submitted, eligibility-review, and approval-review requests can be cancelled.
- Approved, implemented, rejected, and already-cancelled requests cannot be cancelled.
- Active Flowable process instances are deleted during cancellation.

Implemented implementation validation:

- Request must be `APPROVED`.
- Employee must have a pay group before position history is created.
- Target department must resolve from proposed department, proposed role, or current department.
- Existing same-date current history for the proposed role/department is treated as an idempotent retry.
- Existing same-date history for a different position blocks implementation.
- Employee current role and department must still match the request baseline before a new history row is written.

Current gaps:

- Payroll-period validation exists for payroll handoff apply confirmation.
- Payroll handoff apply performs payroll salary application through `PayrollChangeOrchestrator.recalculateForEmployee`.

## 13. Testing Status

Implemented promotion tests:

```text
PromotionBpmnDefinitionTest
PromotionControllerTest
PromotionApiControllerTest
PromotionMapperTest
PromotionApprovalDecisionServiceTest
PromotionApprovalReassignmentServiceTest
PromotionApprovalReminderServiceTest
PromotionApprovalRouteServiceTest
PromotionAuthorizationServiceTest
PromotionConfigurationServiceTest
PromotionEligibilityServiceTest
PromotionEligibilityOverrideServiceTest
PromotionImplementationServiceTest
PromotionAttachmentServiceTest
PromotionCancellationServiceTest
PromotionWorkflowCancellationServiceTest
PromotionPayrollHandoffServiceTest
PromotionSalaryImpactServiceTest
PromotionReportServiceTest
PromotionWorkflowRuntimeTest
PromotionWorkflowServiceAuthorizationTest
PromotionSubmissionServiceTest
PromotionNotificationServiceTest
PromotionEmailServiceTest
PromotionRequiredDocumentServiceTest
PromotionRequesterVisibilityServiceTest
```

Coverage currently verifies:

- BPMN file exists and parses.
- Expected BPMN user/service tasks are present.
- Approval decision persistence behavior.
- Return decision behavior.
- Approval reassignment/delegation, active-step validation, Flowable task assignee updates, and audit entries.
- Approval reminder and escalation eligibility, metadata updates, Flowable task reassignment, and notification hooks.
- Approval route creation, including configurable finance/executive behavior.
- Advanced approval routing for default behavior, department rule, grade rule, employee group/category rule, source rule, and precedence.
- Approval route preview with matched routing-rule names without persisting approval steps.
- Promotion authorization for HR access, assigned approver visibility, task-stage permissions, candidate groups, and self-approval prevention.
- Promotion reference endpoint authorization for employee and job-step lookups.
- Salary response shaping based on salary visibility permissions.
- Requester/current-employee scoped list and dashboard visibility.
- Configuration service default fallback, update, and HR/admin authorization.
- Required-document configuration persistence and response mapping.
- Required-document missing detection and submit/implementation enforcement.
- Eligibility configuration for appraisal score, position-history-based time in role, and probation promotion.
- Eligibility override persistence, validation, authorization, and activity logging.
- Promotion implementation history creation, idempotent retry, and baseline conflict handling.
- Promotion attachment upload, content-type validation, and list mapping.
- Promotion letter generation as persisted downloadable PDF attachments, including template selection and generated-template metadata.
- Promotion cancellation status transition, audit activity, notification hook, and Flowable process deletion.
- Promotion salary-impact preview calculation, explicit proposed salary handling, authorization, and zero-current-salary behavior.
- Promotion report summaries and CSV exports for status, department, salary impact, and approval duration.
- Promotion page routes for the dashboard, requests, approvals, reports, configuration, and payroll handoffs.
- Promotion payroll handoff creation, duplicate suppression, no-change skip behavior, authorized listing, and acknowledge/apply/cancel lifecycle transitions.
- Runtime Flowable approval routing for happy path, rejection, return, cancellation, finance-skip, and executive-required paths.
- Workflow-service guards that stop unauthorized and self-approval task completion before completing the Flowable task.
- Submit orchestration verifies eligibility assertion, route creation, first-stage status movement, workflow start,
  audit activity, and notification hooks.
- Submit orchestration stops before eligibility/workflow creation when configured submit documents are missing.
- Duplicate active request coverage verifies the blocker eligibility result and submission failure.
- Promotion notification service tests verify activity hooks and after-commit email scheduling.
- Promotion email service tests verify outbound employee emails, candidate-group distribution, no-address skip behavior,
  and disabled notification channel behavior.

Recently added:

- Browser-level Playwright coverage for dashboard, request list, request detail, task actions, configuration save,
  required-document indicators, route preview, and payroll handoff actions.

## 14. Delivery Phases

### Phase 1: Backend Foundation

Status: Implemented.

Delivered:

- Entities, enums, repositories, DTOs, service interfaces/implementations, request numbering, activity logging, and basic API controller.

### Phase 2: Core Service Workflow

Status: Implemented.

Delivered:

- Create draft, update draft, submit, cancel, list, detail, dashboard, activity logging.

### Phase 3: Eligibility And Salary Impact

Status: Implemented with deeper rule coverage remaining.

Delivered:

- Eligibility check persistence.
- Submission blocker enforcement.
- Salary impact calculation on request.
- Salary-impact preview endpoint.
- Eligibility and salary values in detail responses.
- Configuration-driven appraisal score, time-in-role, and probation checks.

Remaining:

- Add deeper job hierarchy and employee-risk checks.

### Phase 4: Approval Workflow

Status: Implemented with hardening remaining.

Delivered:

- Persisted approval route.
- Flowable BPMN process.
- Flowable delegates.
- Approve/reject/return actions.
- Task endpoints.
- Direct request-id approval actions.
- Configurable finance/executive route rules.
- Ordered department, grade, employee group/category, and source-based route overrides.
- Approval route preview includes matched routing-rule names.

Remaining:

- Flowable-native reminder/escalation timers.
- Flowable notification delegates and concrete non-email delivery channels.
- More robust in-flight process migration controls.

### Phase 5: Implementation

Status: Implemented with future external integration hardening remaining.

Delivered:

- Approved request implementation.
- Employee current job step update.
- Employee department update.
- Employee position history close/create.
- Pending payroll handoff creation for salary-changing implemented promotions.
- Payroll handoff acknowledge/apply/cancel lifecycle actions.
- Payroll-period validation and confirmer tracking for payroll handoff apply confirmation.
- Implemented status and activity log.
- Dedicated `PromotionImplementationService`.
- Basic idempotency safeguard for retry after same-date promotion history exists.
- Employee baseline conflict detection before writing a new history row.
- Already-implemented retry reconciliation for baseline-still-current employee assignments and missing salary-change payroll handoffs.

Remaining:

- Additional retry safeguards around future external payroll integrations.
- Additional idempotency safeguards around external payroll/application integrations when those integrations are added.
- Group or non-email delivery for implementation notifications if required.

### Phase 6: Frontend Integration

Status: Partially implemented.

Delivered:

- API-backed main promotion page.
- Dedicated page routes for requests, approvals, reports, configuration, and payroll handoffs.
- Dashboard, list, form, detail, request actions, basic task queue, and configuration form.
- Report panels for status summary, department summary, salary impact, and approval duration, with CSV export links.
- Report API and CSV export support for optional effective-date range filters.
- Approval route preview in the request detail view.
- Approval route preview displays matched advanced routing rules when present.
- Payroll handoff queue and lifecycle actions, with apply labelled as payroll application.
- Document upload, download, and removal controls on the request detail view.
- Missing and complete required-document indicators on the request detail view.
- Required-document configuration controls on the configuration panel.
- The main page falls back to scoped requester/employee dashboard and request list data when operational endpoints are unavailable for the current role.
- Cancel action for cancellable requests.
- Browser-level Playwright tests cover dashboard metrics, request list actions, detail rendering, required-document indicators,
  route preview, task approval action, configuration save, and payroll handoff actions.

Remaining:

- Polished task-focused UX.

### Phase 7: Reports And Configuration

Status: Partially implemented.

Delivered:

- Dashboard metrics.
- Configuration API for finance/executive approval routing.
- Configuration API for ordered advanced approval routing rules.
- Report APIs for status summary, department summary, salary impact, and approval duration.
- CSV export APIs and main-page report panels for those reports.
- Optional effective-date range filtering for report APIs and CSV exports.
- Required-document configuration for submit and implementation enforcement stages.

Remaining:

- Specialized report drill-down pages if product needs more than the current dedicated report route.
- Configuration audit trail.

### Phase 8: Hardening

Status: Hardening and expansion remaining.

Remaining:

- Broader audit coverage.
- Additional end-to-end integration tests where needed.
- UI tests.
- Error handling consistency.
- Full-suite cleanup outside promotion is complete.

## 15. Package Structure

Current structure:

```text
com.justjava.humanresource.promotion
  PromotionController
  PromotionApiController
  dto
    PromotionActivityResponse
    PromotionApprovalRoutePreviewResponse
    PromotionApprovalStepResponse
    PromotionApprovalDurationReportResponse
    PromotionAttachmentResponse
    PromotionConfigurationCommand
    PromotionConfigurationResponse
    PromotionDashboardResponse
    PromotionDepartmentReportResponse
    PromotionDecisionCommand
    PromotionDetailResponse
    PromotionEligibilityCheckResponse
    PromotionEligibilityOverrideCommand
    PromotionImplementationCommand
    PromotionPayrollHandoffResponse
    PromotionRequestCommand
    PromotionRoutingRuleCommand
    PromotionRoutingRuleResponse
    PromotionSalaryImpactReportResponse
    PromotionSalaryImpactResponse
    PromotionStatusReportResponse
    PromotionSummaryResponse
  entity
    PromotionActivityLog
    PromotionApprovalStep
    PromotionAttachment
    PromotionConfiguration
    PromotionEligibilityCheck
    PromotionNumberCounter
    PromotionPayrollHandoff
    PromotionRequest
    PromotionRoutingRule
    PromotionStatus
  enums
    PromotionActivityType
    PromotionApprovalDecision
    PromotionApprovalStage
    PromotionAttachmentType
    PromotionPayrollHandoffStatus
    PromotionEligibilitySeverity
    PromotionRequestSource
  repository
    PromotionActivityLogRepository
    PromotionApprovalStepRepository
    PromotionAttachmentRepository
    PromotionConfigurationRepository
    PromotionEligibilityCheckRepository
    PromotionNumberCounterRepository
    PromotionPayrollHandoffRepository
    PromotionRequestRepository
  service
    PromotionActivityService
    PromotionApprovalDecisionService
    PromotionApprovalReassignmentService
    PromotionApprovalReminderService
    PromotionApprovalRouteService
    PromotionAttachmentService
    PromotionAuthorizationService
    PromotionConfigurationService
    PromotionCurrentUserService
    PromotionDocumentIntegrationService
    PromotionEligibilityService
    PromotionImplementationService
    PromotionLetterTemplateService
    PromotionMapper
    PromotionEmailService
    PromotionEmailNotificationChannel
    PromotionNotificationService
    PromotionNotificationChannel
    PromotionNumberService
    PromotionNumberServiceImpl
    PromotionPayrollHandoffService
    PromotionReportService
    PromotionService
    PromotionServiceImpl
    PromotionWorkflowService
    PromotionWorkflowServiceImpl
  workflow.delegate
    EvaluatePromotionEligibilityDelegate
    FinalizePromotionApprovalDelegate
    FinalizePromotionRejectionDelegate
    InitializePromotionApprovalDelegate
    ProcessPromotionApprovalDecisionDelegate
    ReturnPromotionForCorrectionDelegate
```

## 16. MVP Acceptance Status

Current status against MVP acceptance:

- Done: HR can create and submit a promotion request.
- Done: Eligibility checks run and are visible.
- Done: Duplicate active promotion requests are blocked.
- Done: Required approvers can approve, reject, or return via Flowable tasks.
- Done: Approval history is visible in persisted approval steps.
- Done: Approved request can be implemented.
- Done: Employee position history is created during implementation.
- Done: Employee current job and department are updated during implementation.
- Done: Basic duplicate history retry and employee baseline conflict guards exist during implementation.
- Done: Dashboard metrics come from real data.
- Done: Promotion activity log captures key events.
- Done: HR/admin can cancel draft and in-flight requests.
- Partial: Unauthorized users are restricted through current group/task checks, but a full permission model is not implemented.
- Done: Promotion authorization rules are centralized in `PromotionAuthorizationService`.
- Done: Employees and requesters can discover scoped promotion records and dashboard counts without direct links.
- Partial: Core promotion unit, service, and workflow runtime tests pass, but UI/end-to-end coverage is not complete.
- Done: Salary-changing implementations create pending payroll handoff records with acknowledge/apply/cancel lifecycle actions, and apply triggers payroll recalculation after guard validation.
- Partial: Implementation idempotency has same-date history protection and already-implemented retry reconciliation, but future external payroll/application integrations will need their own retry safeguards.

## 17. Remaining Work Snapshot

Review-priority gaps:

- Payroll handoff "apply" validates the effective date against the current open payroll period, validates the employee
  current job-step salary against the handoff proposed salary, and triggers payroll recalculation.
- Summary reports and dashboards currently aggregate with full-table reads. Report details support paged drill-down filtering.
- Local document storage, generated PDFs, local template registry, generated-letter metadata, employee document-library sync,
  and required-document rules are implemented, but content scanning is not.

Backend gaps:

- Additional retry safeguards around future external payroll integrations.
- Configuration audit entries.
- Flowable-native escalation timers.
- Concrete non-email notification channels.

Integration and delivery gaps:

- Additional browser UI coverage for cancellation, upload/delete document flows, and any future polished task-focused UX.
- Additional end-to-end integration coverage only where it adds value beyond the current service and Flowable runtime tests.
- Full-suite cleanup outside the promotion package is complete.

Security and audit gaps:

- Broader audit metadata beyond request IP, user agent, and coarse device classification.

## 18. Remaining Milestones

Each milestone below is scoped so it can be implemented in one focused implementation pass. Larger product themes such
as payroll, documents, workflow hardening, notification expansion, and reporting are intentionally split into smaller
deliverables to keep code changes reviewable and testable.

### Milestone 1: Payroll Application Guardrails

Status: Implemented.

Goal: make payroll handoff confirmation safer before payroll application is introduced.

Delivered:

- Payroll-period validation runs before confirming a handoff as applied.
- Confirmation is blocked when the promotion effective date is outside the current open payroll period for the employee department company.
- The employee/user who confirmed payroll application is stored in `PromotionPayrollHandoff.appliedBy`.
- Handoff responses include `appliedByName`.
- Tests cover successful confirmation, closed/out-of-open-period rejection, unresolved payroll company rejection, and already-applied acknowledgement rejection.
- This plan has been updated to reflect the implemented guardrail behavior.

Payroll recalculation remains out of scope for this milestone and is delivered by Milestone 2.

### Milestone 2: Direct Payroll Salary Application

Status: Implemented.

Goal: apply salary changes through the payroll module instead of only tracking external confirmation.

Delivered:

- Identified payroll's canonical salary application path as employee job-step/position-history recalculation through `PayrollChangeOrchestrator.recalculateForEmployee`.
- Payroll handoff apply now invokes `PayrollChangeOrchestrator.recalculateForEmployee(employeeId, effectiveDate)` before marking the handoff applied.
- Apply is idempotent for already-applied handoffs and returns the current response without recalculating again.
- Apply validates the employee current job-step gross salary against the handoff proposed salary before payroll recalculation.
- Apply keeps the open payroll-period validation from Milestone 1.
- Promotion activity and handoff state changes are recorded after successful payroll recalculation.
- Tests cover successful apply, stale salary conflict, closed/out-of-open-period rejection, unresolved payroll company rejection, and already-applied retry behavior.
- This plan has been updated to reflect the implemented payroll application behavior.

### Milestone 3: Required Promotion Documents

Status: Implemented.

Goal: enforce required document rules without requiring external document-module integration.

Delivered:

- Added submit-stage and implementation-stage required document lists to `PromotionConfiguration`.
- Exposed required document configuration through configuration command/response DTOs.
- Added `PromotionRequiredDocumentService` to detect missing configured attachment types.
- Submission now stops before eligibility/workflow creation when submit-required documents are missing.
- Implementation now stops before employee position writes when implementation-required documents are missing.
- Promotion detail responses include missing required documents for both enforcement stages.
- The promotion page configuration panel can edit required document lists.
- The promotion detail panel shows missing or complete required document status.
- Focused tests cover configuration persistence/response mapping, missing-document detection, submit enforcement, implementation enforcement, and detail response mapping.
- This plan has been updated to reflect the implemented required-document behavior.

### Milestone 4: Document Template Hardening

Status: Implemented.

Goal: improve local promotion document generation before external document integration.

Delivered:

- Added `PromotionLetterTemplateService` as a local template registry for generated promotion documents.
- Registered separate standard templates for promotion letters and salary-adjustment letters.
- Generated attachments now store `generatedTemplateKey`, `generatedTemplateName`, and `generatedTemplateVersion`.
- Attachment responses and promotion detail mapping expose generated-template metadata.
- Tests cover generated promotion-letter content and metadata, generated salary-adjustment-letter content and metadata,
  unsupported generated document types, and uploaded document metadata remaining empty.
- This plan has been updated to reflect the implemented document template hardening behavior.

### Milestone 5: External Document Module Integration

Status: Implemented.

Goal: connect promotion documents to the broader document module.

Delivered:

- Added `PromotionDocumentIntegrationService` as the adapter boundary between promotion attachments and the employee document library.
- Uploaded promotion attachments create linked `EmployeeDocument` records for the promoted employee.
- Generated promotion and salary-adjustment letters create linked `EmployeeDocument` records for the promoted employee.
- Promotion attachments store `documentLibraryDocumentId`, `documentLibrarySyncedAt`, and `documentLibrarySyncError`.
- Sync is retry-safe for already-linked attachments because attachments with an existing document-library id are skipped.
- Sync failures do not block promotion attachment upload or letter generation; the sync error is captured on the promotion attachment.
- Existing promotion attachment responses are preserved and extended with document-library sync metadata.
- Tests cover successful employee document-library sync, retry-safe skip behavior, sync failure capture, and attachment-service behavior with mocked sync.
- This plan has been updated to reflect the implemented document-module integration behavior.

### Milestone 6: Advanced Approval Routing Rules

Status: Implemented.

Goal: support richer configured approval routing.

Delivered:

- Added embedded `PromotionRoutingRule` configuration entries to `PromotionConfiguration`.
- Exposed routing rules through `PromotionRoutingRuleCommand`, `PromotionRoutingRuleResponse`, and the configuration command/response DTOs.
- Routing rules can match proposed department, proposed job grade, employee group/category, and request source.
- Matched rules are evaluated by priority, with lower priority numbers taking precedence for finance/executive overrides.
- Rules can require or skip finance and executive approval independently.
- Existing default finance/executive salary-based routing remains the fallback when no matched rule overrides a decision.
- Approval route preview now returns matched routing-rule names.
- Tests cover default routing, department rules, grade rules, source rules, employee group/category rules, precedence, configuration mapping, and preview matched-rule reporting.
- This plan has been updated to reflect the implemented advanced routing behavior.

### Milestone 7: Approval Reassignment And Delegation

Status: Implemented.

Goal: allow controlled changes to pending approval ownership.

Delivered:

- Added reassignment and delegation metadata to `PromotionApprovalStep`.
- Added `POST /api/promotions/{id}/approval-steps/{stepId}/reassign`.
- Added `POST /api/promotions/{id}/approval-steps/{stepId}/delegate`.
- Added `PromotionApprovalReassignmentService` to validate HR/admin access, ensure only the active pending step is changed,
  update the persisted approval owner, update the active Flowable task assignee, audit the change, and queue assignment notification hooks.
- Reassigned/delegated steps clear candidate-group ownership and require the named approver during task completion.
- Promotion detail approval-step responses expose original approver, reassignment, and delegation metadata.
- Tests cover reassignment, delegation, inactive-step rejection, controller wiring, mapper compatibility, and named approver authorization path.
- This plan has been updated to reflect the implemented reassignment/delegation behavior.

### Milestone 8: Reminder And Escalation Workflow

Status: Implemented.

Goal: add time-based workflow hardening for pending approvals.

Delivered:

- Added approval reminder and escalation timing/target fields to `PromotionConfiguration`.
- Exposed reminder/escalation settings through configuration command and response DTOs.
- Added reminder and escalation metadata to `PromotionApprovalStep`.
- Added `PromotionApprovalReminderService` with an hourly scheduled sweep, configurable through `promotion.approval-reminder-scan-ms`.
- The reminder sweep sends one reminder for due pending approval steps and records reminder timestamp/count metadata.
- The escalation sweep moves due pending approval steps to a configured employee or candidate group and updates the active Flowable task assignment.
- Reminder and escalation notification hooks record notification activity and send assigned-approver emails after commit.
- The promotion configuration panel can edit reminder/escalation timing and target settings.
- Tests cover due reminders, duplicate-reminder suppression, employee escalation, group escalation, configuration mapping, notification hooks, and email delivery.
- This plan has been updated to reflect the implemented reminder/escalation behavior.

### Milestone 9: Notification Channel Expansion

Status: Implemented.

Goal: expand notifications beyond direct email to assigned approvers.

Delivered:

- Added `PromotionNotificationChannel` as the delivery abstraction for promotion notifications.
- Added `PromotionEmailNotificationChannel` as the current email delivery channel.
- `PromotionEmailService` now dispatches through enabled notification channels instead of directly calling the low-level email utility.
- Group-routed approval assignment, reminder, and escalation notices now resolve active employees in the configured approver group.
- Group lookup supports both slash-prefixed and non-prefixed group names.
- Missing email addresses are skipped per recipient without blocking other group recipients.
- Tests cover assigned approver email, candidate-group email, missing email, and disabled channel behavior.
- This plan has been updated to reflect the implemented notification-channel expansion behavior.

### Milestone 10: Requester List And Dashboard Visibility

Status: Implemented.

Goal: allow employees and requesters to discover records they are allowed to view without direct links.

Delivered:

- Added `GET /api/promotions/my` for current employee/requester scoped promotion lists.
- Added `GET /api/promotions/my/dashboard` for current employee/requester scoped dashboard counts and recent requests.
- Added `PromotionService.myPromotions(status)` and `PromotionService.myDashboard()`.
- Scoped queries include records where the current employee is the promoted employee or requester.
- Existing mapper salary visibility rules still hide salary fields unless `PromotionAuthorizationService.canViewSalary()` allows them.
- The promotion page falls back to scoped list/dashboard endpoints when operational HR/Finance endpoints are unavailable.
- Tests cover scoped filtering, scoped dashboard counts and impacts, missing employee context rejection, and API controller delegation.
- This plan has been updated to reflect the implemented requester visibility behavior.

### Milestone 11: Method-Level Permission Model

Status: Implemented.

Goal: harden promotion authorization with framework-level method security.

Delivered:

- Confirmed Spring method security is enabled through `@EnableMethodSecurity`.
- Added `PromotionSecurity` as a SpEL expression bean for promotion role gates.
- Added coarse `@PreAuthorize` guards to promotion page and REST controllers.
- Added method-level guards to sensitive promotion service entry points, including draft management,
  submission, cancellation, implementation, configuration, attachments, payroll handoffs, and approval reassignment/delegation.
- Kept `PromotionAuthorizationService` as the request-specific authorization layer for detail visibility,
  salary masking, task ownership, self-approval prevention, and named-approver enforcement.
- Added `PromotionSecurityTest` coverage for security enablement, role-expression behavior, and key controller/service annotations.
- This plan has been updated to reflect the implemented method-level permission behavior.

### Milestone 12: Audit Metadata

Status: Implemented.

Goal: enrich the promotion audit trail with request context where available.

Delivered:

- Added IP address, user agent, and coarse device metadata fields to `PromotionActivityLog`.
- `PromotionActivityService` captures request metadata from the current servlet request when available.
- `X-Forwarded-For` is preferred for IP capture, falling back to the request remote address.
- System/background activity recording continues to work when no request context exists.
- `PromotionActivityResponse` and promotion detail mapping expose activity audit metadata.
- Tests cover request-backed metadata capture, system/background activity without request context, and detail response mapping.
- This plan has been updated to reflect the implemented audit metadata behavior.

### Milestone 13: Report Drill-Down And Pagination

Status: Implemented.

Goal: make reports more useful and scalable for operational users.

Delivered:

- Added `GET /api/promotions/reports/details`.
- Added `PromotionReportDetailPageResponse` with content and page metadata.
- Added JPA specification support to `PromotionRequestRepository`.
- Report details support filters for status, department, effective date range, and approval date range.
- Report details are paged and sorted by newest requested date, then newest id.
- Page size is clamped to a maximum of 100.
- Existing summary and CSV report endpoints remain stable.
- Tests cover page-size clamping, pageable query use, controller delegation, and detail response metadata.
- This plan has been updated to reflect the implemented report drill-down behavior.

### Milestone 14: Browser-Level UI Tests

Status: Implemented.

Goal: cover the main promotion workflows at browser level.

Delivered:

- Added Playwright as a test-scoped dependency.
- Added `PromotionMainPageBrowserTest`, which renders `templates/promotion/main.html` in headless Chromium with mocked API responses.
- Covered dashboard metrics, request list rendering, request detail rendering, required-document indicators, attachment list rendering,
  route preview display, task approval action, submit and implement actions, configuration save, focused page routes, and payroll handoff actions.
- The browser test runs with the targeted promotion suite command in the Current Verification Status section.
- This plan has been updated to reflect the implemented browser UI coverage.

Remaining:

- Additional browser coverage for reject, return, cancellation, upload/delete document flows, and future task-focused UX polish.

### Milestone 15: Non-Promotion Full-Suite Cleanup

Status: Implemented.

Goal: unblock the full Maven test suite outside the promotion package.

Delivered:

- Confirmed `DocumentLibraryServiceTest` already passes under the current code.
- Restored scoped employee-exit clearance authorization so HR can complete HR/legal clearance, admin can complete all
  clearance types, and other operational roles remain limited to their own clearance lanes.
- Restored Fixed Asset Management lookup fallback sample assets when the external FAM catalog is unavailable.
- Restored payroll period close/open carry-forward behavior to start `payrollCarryForwardProcess` with non-null-safe
  process variables after the next period is saved.
- Verified the previously failing cleanup group passes: `DocumentLibraryServiceTest`,
  `EmployeeExitAuthorizationServiceTest`, `FamAssetLookupServiceTest`, and `PayrollPeriodServiceImplTest`.
- Verified the full Maven suite passes: `mvnw.cmd test` reports 329 tests, 0 failures, 0 errors, 1 skipped.
- This plan has been updated to reflect the implemented non-promotion full-suite cleanup behavior.

## 19. Recommended Milestone Order

1. Payroll Application Guardrails. Status: implemented.
2. Direct Payroll Salary Application. Status: implemented.
3. Required Promotion Documents. Status: implemented.
4. Advanced Approval Routing Rules. Status: implemented.
5. Requester List And Dashboard Visibility. Status: implemented.
6. Browser-Level UI Tests. Status: implemented.
7. Document Template Hardening. Status: implemented.
8. External Document Module Integration. Status: implemented.
9. Approval Reassignment And Delegation. Status: implemented.
10. Reminder And Escalation Workflow. Status: implemented.
11. Notification Channel Expansion. Status: implemented.
12. Method-Level Permission Model. Status: implemented.
13. Audit Metadata. Status: implemented.
14. Report Drill-Down And Pagination. Status: implemented.
15. Non-Promotion Full-Suite Cleanup. Status: implemented.
