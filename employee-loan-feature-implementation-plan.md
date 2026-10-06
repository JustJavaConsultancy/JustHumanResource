# Employee Loan Feature Implementation Plan

## Purpose

This document describes the full implementation plan for adding a dedicated Employee Loan feature to the application.

It expands the product flow in `employee-loan-feature-flow-report.md` into backend, workflow, payroll, and frontend development work. It lists the new Java classes/files to create, existing classes/files to edit, expected behavior, and the safety rules needed to avoid breaking existing features.

## Compatibility And Safety Assessment

The safest implementation path is to build Employee Loans as a dedicated module, not as another generic request type.

The current app already has:

- Generic request workflow under `com.justjava.humanresource.request`.
- Custom approval path support under `com.justjava.humanresource.approval`.
- Flowable workflows under `src/main/resources/processes`.
- Payroll calculation and deduction processing under `com.justjava.humanresource.payroll`.
- Separate Thymeleaf layouts for HR/admin, employee, and finance users.

The existing generic request workflow is assignee-based. It expects specific employee IDs as approvers. Employee Loans require two approval options:

- A default role-based approval route where any HR user can approve the HR stage and any Finance Officer can approve the final stage.
- An optional custom approval path route where HR configures a named sequence of employee approvers for a loan product.

For this reason, loans should use their own Flowable process and services instead of becoming another generic request type. The loan module should reuse the existing `com.justjava.humanresource.approval` route-resolution concepts where safe, but it should not force loan applications into `WorkflowRequest`.

This avoids breaking:

- Staff requisitions.
- File requests.
- Asset requests.
- Expense reimbursement requests.
- General requests.
- Existing custom approval paths.
- Existing line-manager approval routes.
- Existing payroll period close approval.
- Existing payroll deduction setup.

The Employee Loan feature should integrate with existing systems only through controlled extension points:

- Authentication/group checks through `AuthenticationManager`.
- Employee profile/salary/grade lookups through existing HR entities/services.
- Payroll deduction injection through payroll orchestration.
- Email notifications through a new loan-specific notification service.
- New sidebar links in existing layouts.

## Implementation Strategy

Create a new bounded module:

```text
com.justjava.humanresource.loan
```

This module will own:

- Loan setup.
- Loan application.
- Role-based approval.
- Custom approval path routing for loan products.
- Active loan account state.
- Repayment schedule.
- Repayment transactions.
- Missed deduction tracking.
- Loan-specific activity history.
- Loan-specific pages and API endpoints.

The generic request module should not be changed to support loans unless a small shared utility is clearly reusable and safe.

## Backend Package Structure To Create

```text
src/main/java/com/justjava/humanresource/loan
src/main/java/com/justjava/humanresource/loan/controller
src/main/java/com/justjava/humanresource/loan/dto
src/main/java/com/justjava/humanresource/loan/entity
src/main/java/com/justjava/humanresource/loan/enums
src/main/java/com/justjava/humanresource/loan/repository
src/main/java/com/justjava/humanresource/loan/service
src/main/java/com/justjava/humanresource/loan/service/impl
src/main/java/com/justjava/humanresource/loan/workflow/delegate
```

## Entities To Create

### `LoanProduct`

Package:

```text
com.justjava.humanresource.loan.entity
```

Purpose:

Represents HR-configured loan setup/policy.

Key fields:

- `id`
- `code`
- `name`
- `description`
- `minimumAmount`
- `maximumAmount`
- `minimumRepaymentAmount`
- `maximumTenorMonths`
- `interestType`
- `interestRate`
- `repaymentFrequency`
- `approvalRouteType`
- `customApprovalPathId`
- `requiresAttachment`
- `active`
- `used`
- `createdAt`
- `updatedAt`
- `version`

Behavior:

- HR can create loan products.
- HR can edit unused loan products fully.
- Once used, only non-financial/display fields and active/inactive status can be changed.
- Approval route settings can be edited only while the product is unused, because submitted applications must preserve the route that was valid at submission time.
- If `approvalRouteType` is `CUSTOM`, `customApprovalPathId` is required and must reference an enabled, non-empty custom approval path.
- Used loan products cannot be deleted.
- Inactive products cannot be selected by employees for new applications.

### `EmployeeLoanApplication`

Purpose:

Represents the employee's submitted or draft loan application.

Key fields:

- `id`
- `applicationNumber`
- `loanProduct`
- `employee`
- `departmentId`
- `jobGradeNameSnapshot`
- `jobStepNameSnapshot`
- `grossSalarySnapshot`
- `requestedAmount`
- `repaymentAmount`
- `tenorMonths`
- `repaymentStartMonth`
- `interestTypeSnapshot`
- `interestRateSnapshot`
- `approvalRouteTypeSnapshot`
- `customApprovalPathIdSnapshot`
- `customApprovalPathNameSnapshot`
- `totalRepayableAmount`
- `totalInterestAmount`
- `purpose`
- `status`
- `workflowInstanceId`
- `submittedAt`
- `hrApprovedAt`
- `hrApprovedByEmployeeId`
- `financeApprovedAt`
- `financeApprovedByEmployeeId`
- `customApprovalCompletedAt`
- `rejectedAt`
- `rejectedByEmployeeId`
- `cancelledAt`
- `cancelledByEmployeeId`
- `activatedAt`
- `closedAt`
- `createdAt`
- `updatedAt`
- `version`

Behavior:

- Draft applications can be edited and deleted by the employee.
- Submitted applications cannot be edited unless returned for correction.
- HR/Finance cannot modify employee-selected terms.
- Finance approval automatically activates the loan.
- For custom approval path products, final custom approval automatically activates the loan.
- Rejected/cancelled/submitted records are retained.

### `EmployeeLoanApprovalStep`

Purpose:

Represents the approval steps generated for a submitted loan application.

Key fields:

- `id`
- `loanApplicationId`
- `sequenceNo`
- `approvalStage`
- `approverEmployeeId`
- `approverGroup`
- `decision`
- `comments`
- `flowableTaskId`
- `decisionAt`
- `actedByEmployeeId`
- `createdAt`
- `updatedAt`
- `version`

Behavior:

- Role-based approvals create HR and Finance approval step records with `approverGroup` populated.
- Custom approvals create one step per configured custom approval path employee with `approverEmployeeId` populated.
- The table is the approval audit source for both role-based and custom loan routes.
- Existing approval steps must not be recalculated after submission unless the loan is returned for correction and resubmitted.

### `EmployeeLoanAccount`

Purpose:

Represents the active loan after Finance final approval on the role-based route or final custom approval on the custom route.

Key fields:

- `id`
- `loanApplicationId`
- `loanProductId`
- `employeeId`
- `principalAmount`
- `interestAmount`
- `totalRepayableAmount`
- `totalPaidAmount`
- `outstandingBalance`
- `repaymentAmount`
- `tenorMonths`
- `repaymentStartMonth`
- `status`
- `activatedAt`
- `completedAt`
- `closedAt`
- `createdAt`
- `updatedAt`
- `version`

Behavior:

- Created automatically after Finance approval.
- Drives payroll repayment schedule.
- Not editable through normal HR/Finance screens.
- Completed automatically when fully repaid.

### `LoanRepaymentSchedule`

Purpose:

Represents expected monthly repayments.

Key fields:

- `id`
- `loanAccountId`
- `sequenceNumber`
- `dueMonth`
- `expectedAmount`
- `principalPortion`
- `interestPortion`
- `paidAmount`
- `outstandingAmount`
- `status`
- `payrollRunId`
- `payrollLineItemId`
- `deductedAt`
- `missedAt`
- `createdAt`
- `updatedAt`
- `version`

Behavior:

- Previewed before approval.
- Locked after Finance approval.
- Used by payroll to determine deductions.
- Marked `PAID`, `PARTIALLY_PAID`, `MISSED`, or `PENDING`.
- Missed deductions are flagged, not automatically rolled forward.

### `LoanRepaymentTransaction`

Purpose:

Records actual payroll deductions against loan schedules.

Key fields:

- `id`
- `loanAccountId`
- `repaymentScheduleId`
- `employeeId`
- `payrollRunId`
- `payrollLineItemId`
- `amount`
- `transactionMonth`
- `transactionType`
- `createdAt`
- `updatedAt`
- `version`

Behavior:

- Created when payroll successfully applies a loan deduction.
- Used to calculate repayment history and outstanding balance.

### `EmployeeLoanAttachment`

Purpose:

Stores metadata for employee loan supporting documents.

Key fields:

- `id`
- `loanApplicationId`
- `originalFilename`
- `contentType`
- `fileSize`
- `storagePath`
- `attachmentType`
- `uploadedByEmployeeId`
- `createdAt`
- `updatedAt`
- `version`

Behavior:

- Employees can add/remove attachments in draft or returned status.
- Attachments are retained after submission.

### `EmployeeLoanActivity`

Purpose:

Keeps the audit/activity timeline for loan actions.

Key fields:

- `id`
- `loanApplicationId`
- `loanAccountId`
- `activityType`
- `description`
- `actorEmployeeId`
- `createdAt`
- `updatedAt`
- `version`

Behavior:

- Records setup, application, approval, activation, payroll deduction, missed deduction, completion, and closure activities.

### `LoanNumberCounter`

Purpose:

Generates sequential loan application numbers.

Key fields:

- `id`
- `prefix`
- `year`
- `lastNumber`
- `createdAt`
- `updatedAt`
- `version`

Behavior:

- Generates stable numbers like `LOAN-2026-0001`.

## Enums To Create

Package:

```text
com.justjava.humanresource.loan.enums
```

### `LoanInterestType`

Values:

- `INTEREST_FREE`
- `INTEREST_BEARING`

### `LoanRepaymentFrequency`

Values:

- `MONTHLY`

### `LoanApplicationStatus`

Values:

- `DRAFT`
- `SUBMITTED`
- `PENDING_HR_APPROVAL`
- `PENDING_CUSTOM_APPROVAL`
- `RETURNED_FOR_CORRECTION`
- `HR_APPROVED`
- `PENDING_FINANCE_APPROVAL`
- `FINANCE_APPROVED`
- `CUSTOM_APPROVED`
- `ACTIVE`
- `REJECTED`
- `CANCELLED`
- `COMPLETED`
- `CLOSED`

### `LoanAccountStatus`

Values:

- `ACTIVE`
- `COMPLETED`
- `CLOSED`

### `LoanRepaymentStatus`

Values:

- `PENDING`
- `PAID`
- `PARTIALLY_PAID`
- `MISSED`

### `LoanApprovalDecision`

Values:

- `APPROVE`
- `REJECT`
- `RETURN`

### `LoanApprovalRouteType`

Values:

- `ROLE_BASED`
- `CUSTOM`

Meaning:

- `ROLE_BASED` uses the standard HR approval then Finance approval route.
- `CUSTOM` uses an enabled custom approval path selected on the loan product.

### `LoanApprovalStage`

Values:

- `HR`
- `FINANCE`
- `CUSTOM`

### `LoanActivityType`

Values:

- `PRODUCT_CREATED`
- `PRODUCT_UPDATED`
- `PRODUCT_DEACTIVATED`
- `PRODUCT_REACTIVATED`
- `DRAFT_CREATED`
- `DRAFT_UPDATED`
- `APPLICATION_SUBMITTED`
- `APPLICATION_CANCELLED`
- `APPLICATION_RETURNED`
- `APPLICATION_RESUBMITTED`
- `HR_APPROVED`
- `HR_REJECTED`
- `FINANCE_APPROVED`
- `FINANCE_REJECTED`
- `CUSTOM_APPROVAL_STARTED`
- `CUSTOM_APPROVED`
- `CUSTOM_REJECTED`
- `LOAN_ACTIVATED`
- `PAYROLL_DEDUCTION_APPLIED`
- `PAYROLL_DEDUCTION_MISSED`
- `LOAN_COMPLETED`
- `LOAN_CLOSED`

### `LoanAttachmentType`

Values:

- `SUPPORTING_DOCUMENT`
- `OTHER`

### `LoanRepaymentTransactionType`

Values:

- `PAYROLL_DEDUCTION`

## DTOs To Create

Package:

```text
com.justjava.humanresource.loan.dto
```

### Product DTOs

- `LoanProductCommand`
- `LoanProductResponse`
- `LoanProductSummaryResponse`

Purpose:

- Create/update/display loan setup.
- Keep request payload separate from entity.

### Application DTOs

- `LoanApplicationCommand`
- `LoanApplicationResponse`
- `LoanApplicationSummaryResponse`
- `LoanApplicationDetailResponse`
- `LoanApplicationEditResponse`
- `LoanApprovalStepResponse`

Purpose:

- Create/edit draft applications.
- Display employee, HR, and Finance detail pages.
- Include salary, grade, step, department, and existing loan exposure.

### Approval DTOs

- `LoanApprovalActionCommand`
- `LoanApprovalTaskResponse`
- `LoanApprovalContextResponse`
- `LoanApprovalRouteResponse`

Purpose:

- Approve/reject/return loan applications.
- Show pending HR/Finance queues.

### Repayment DTOs

- `LoanRepaymentPreviewRequest`
- `LoanRepaymentPreviewResponse`
- `LoanRepaymentScheduleLineResponse`
- `LoanRepaymentTransactionResponse`

Purpose:

- Preview repayment before submission.
- Show locked schedule and repayment history.

### Dashboard/Report DTOs

- `EmployeeLoanDashboardResponse`
- `HrLoanDashboardResponse`
- `FinanceLoanDashboardResponse`
- `EmployeeLoanExposureResponse`
- `LoanMissedDeductionResponse`

Purpose:

- Support dashboard cards, exposure warnings, and missed deduction monitoring.

## Repositories To Create

Package:

```text
com.justjava.humanresource.loan.repository
```

Repositories:

- `LoanProductRepository`
- `EmployeeLoanApplicationRepository`
- `EmployeeLoanApprovalStepRepository`
- `EmployeeLoanAccountRepository`
- `LoanRepaymentScheduleRepository`
- `LoanRepaymentTransactionRepository`
- `EmployeeLoanAttachmentRepository`
- `EmployeeLoanActivityRepository`
- `LoanNumberCounterRepository`

Important query needs:

- Active loan products for employees.
- Loan product usage count.
- Employee draft/submitted/active loans.
- HR pending applications.
- Finance pending applications.
- Custom approval tasks assigned to a specific employee.
- Active loan accounts due in a payroll month.
- Repayment schedule rows due in a payroll month.
- Missed repayment rows.
- Employee loan exposure summary.
- Loan history by status.

## Services To Create

Package:

```text
com.justjava.humanresource.loan.service
com.justjava.humanresource.loan.service.impl
```

### `LoanProductService`

Responsibilities:

- Create loan products.
- Update loan products.
- Validate loan approval route settings.
- Deactivate/reactivate loan products.
- Delete unused loan products.
- Prevent unsafe financial edits after product usage.
- List products for HR.
- List active products for employees.

Implementation:

- `LoanProductServiceImpl`

### `EmployeeLoanApplicationService`

Responsibilities:

- Create draft application.
- Edit draft or returned application.
- Delete draft.
- Submit application.
- Cancel allowed applications.
- Return application detail for employee, HR, and Finance views.
- Validate current user access.
- Snapshot the selected loan product approval route at submission.
- Start Flowable loan approval process.

Implementation:

- `EmployeeLoanApplicationServiceImpl`

### `LoanApprovalService`

Responsibilities:

- Find HR approval tasks for Human Resource users.
- Find Finance approval tasks for Finance Officers.
- Find custom loan approval tasks assigned to the current employee.
- Approve, reject, or return HR-stage applications.
- Approve, reject, or return Finance-stage applications.
- Approve, reject, or return custom approval path applications.
- Validate role access for each task.
- Validate assignee access for custom approval tasks.
- Complete Flowable tasks with loan decision variables.

Implementation:

- `LoanApprovalServiceImpl`

### `LoanRepaymentCalculationService`

Responsibilities:

- Validate requested amount, tenor, repayment amount, and repayment start month.
- Calculate total repayable amount.
- Calculate interest where applicable.
- Generate repayment preview lines.
- Generate locked repayment schedule after Finance approval.

Implementation:

- `LoanRepaymentCalculationServiceImpl`

### `EmployeeLoanAccountService`

Responsibilities:

- Activate approved loan after Finance final approval on the role-based route or final custom approval on the custom route.
- Create account.
- Lock schedule.
- Complete loan after full repayment.
- Close loan where applicable.
- Summarize active loans and outstanding balances.

Implementation:

- `EmployeeLoanAccountServiceImpl`

### `LoanPayrollDeductionService`

Responsibilities:

- During payroll, find due loan repayments for employee/month.
- Create loan deduction payroll line items.
- Record repayment transactions.
- Update repayment schedule rows.
- Update outstanding balance.
- Mark loan completed when fully repaid.
- Mark expected repayments missed when payroll does not deduct them.

Implementation:

- `LoanPayrollDeductionServiceImpl`

This service is the main payroll integration point.

### `LoanAttachmentService`

Responsibilities:

- Store loan attachments.
- Load loan attachments.
- Delete allowed draft/returned attachments.
- Enforce access control.

Implementation:

- `LoanAttachmentServiceImpl`

### `LoanActivityService`

Responsibilities:

- Record all loan activity events.
- Return activity timeline for detail pages.

Implementation:

- `LoanActivityServiceImpl`

### `LoanNumberService`

Responsibilities:

- Generate loan application numbers.

Implementation:

- `LoanNumberServiceImpl`

### `LoanNotificationService`

Responsibilities:

- Notify employee after submission.
- Notify HR users of pending HR approval.
- Notify employee after HR decision.
- Notify Finance users of pending Finance approval.
- Notify employee after Finance final decision.
- Notify employee after loan activation.
- Notify employee after completion.
- Optionally notify HR/Finance about missed deductions.

Implementation:

- `LoanNotificationServiceImpl`

### `LoanEmployeeContextService`

Responsibilities:

- Build approval detail context.
- Fetch salary, grade, step, department, active loans, pending loans, and outstanding balances.

Implementation:

- `LoanEmployeeContextServiceImpl`

### `LoanApprovalRouteService`

Responsibilities:

- Resolve the approval route for a loan application at submission time.
- Use role-based HR/Finance stages when `LoanApprovalRouteType.ROLE_BASED` is selected.
- Use `ApprovalRouteResolverFactory` with `ApprovalModuleType.LOAN` when `LoanApprovalRouteType.CUSTOM` is selected.
- Create `EmployeeLoanApprovalStep` rows for the resolved route.
- Reject submission if a custom path is missing, disabled, empty, or resolves to no eligible approvers.

Implementation:

- `LoanApprovalRouteServiceImpl`

## Controllers To Create

Package:

```text
com.justjava.humanresource.loan.controller
```

### `EmployeeLoanPageController`

Routes:

- `GET /employee/loans`
- `GET /employee/loans/{id}`

Templates:

- `templates/loan/employee-main.html`
- `templates/loan/employee-detail.html`

Purpose:

- Employee-facing pages.

### `HrLoanPageController`

Routes:

- `GET /loans`
- `GET /loans/{id}`
- `GET /loans/products`

Templates:

- `templates/loan/hr-main.html`
- `templates/loan/hr-detail.html`
- Product setup may be a tab inside `hr-main.html` unless it becomes too large.

Purpose:

- HR loan setup, approvals, and monitoring.

### `FinanceLoanPageController`

Routes:

- `GET /finance/loans`
- `GET /finance/loans/{id}`

Templates:

- `templates/loan/finance-main.html`
- `templates/loan/finance-detail.html`

Purpose:

- Finance approval queue and active loan monitoring.

### `LoanProductController`

Base route:

```text
/api/loans/products
```

Endpoints:

- `GET /api/loans/products`
- `GET /api/loans/products/active`
- `GET /api/loans/products/{id}`
- `POST /api/loans/products`
- `PUT /api/loans/products/{id}`
- `POST /api/loans/products/{id}/deactivate`
- `POST /api/loans/products/{id}/reactivate`
- `DELETE /api/loans/products/{id}`

Purpose:

- HR loan setup API.

### `EmployeeLoanController`

Base route:

```text
/api/employee/loans
```

Endpoints:

- `GET /api/employee/loans/dashboard`
- `GET /api/employee/loans/products`
- `GET /api/employee/loans`
- `GET /api/employee/loans/{id}`
- `GET /api/employee/loans/approval-tasks`
- `POST /api/employee/loans/drafts`
- `PUT /api/employee/loans/{id}`
- `DELETE /api/employee/loans/{id}`
- `POST /api/employee/loans/{id}/submit`
- `POST /api/employee/loans/{id}/cancel`
- `POST /api/employee/loans/preview`
- `POST /api/employee/loans/{id}/attachments`
- `GET /api/employee/loans/{id}/attachments/{attachmentId}`
- `DELETE /api/employee/loans/{id}/attachments/{attachmentId}`
- `POST /api/employee/loans/approval-tasks/{taskId}/approve`
- `POST /api/employee/loans/approval-tasks/{taskId}/reject`
- `POST /api/employee/loans/approval-tasks/{taskId}/return`

Purpose:

- Employee application and self-service API.
- Assigned custom loan approver task API for approvers who may not be HR or Finance users.

### `HrLoanController`

Base route:

```text
/api/hr/loans
```

Endpoints:

- `GET /api/hr/loans/dashboard`
- `GET /api/hr/loans`
- `GET /api/hr/loans/{id}`
- `GET /api/hr/loans/tasks`
- `POST /api/hr/loans/approvals/approve`
- `POST /api/hr/loans/approvals/reject`
- `POST /api/hr/loans/approvals/return`
- `GET /api/hr/loans/missed-deductions`

Purpose:

- HR review, approval, and monitoring API.

### `FinanceLoanController`

Base route:

```text
/api/finance/loans
```

Endpoints:

- `GET /api/finance/loans/dashboard`
- `GET /api/finance/loans`
- `GET /api/finance/loans/{id}`
- `GET /api/finance/loans/tasks`
- `POST /api/finance/loans/approvals/approve`
- `POST /api/finance/loans/approvals/reject`
- `POST /api/finance/loans/approvals/return`
- `GET /api/finance/loans/missed-deductions`

Purpose:

- Finance approval and monitoring API.

## Workflow Files To Create

### `employeeLoanApprovalProcess.bpmn`

Path:

```text
src/main/resources/processes/employeeLoanApprovalProcess.bpmn
```

Process key:

```text
employeeLoanApprovalProcess
```

Flow:

1. Start.
2. Initialize loan approval.
3. Route gateway:
   - `ROLE_BASED` -> HR user task.
   - `CUSTOM` -> Custom approver user task.
4. For role-based route, process HR decision.
5. Role-based gateway:
   - HR approve -> Finance user task.
   - HR reject -> Finalize rejection.
   - HR return -> Return for correction.
6. Process Finance decision.
7. Finance gateway:
   - Finance approve -> Activate loan.
   - Finance reject -> Finalize rejection.
   - Finance return -> Return for correction.
8. For custom route, process custom approver decision.
9. Custom route gateway:
   - Custom approve and more approvers remain -> next custom approver user task.
   - Custom approve and no more approvers remain -> Activate loan.
   - Custom reject -> Finalize rejection.
   - Custom return -> Return for correction.
10. End.

Role-based tasks:

- HR task should be candidate group `humanresource`.
- Finance task should be candidate group `financialofficers`.

Candidate groups should match normalized Keycloak group names already used by `AuthenticationManager`.

Custom route tasks:

- Custom approval tasks should be assigned to the specific employee ID resolved from the selected custom approval path.
- Custom tasks should use the existing employee-id assignee convention used by generic requests.
- Custom task names should include the current approval level, for example `Loan Custom Approval Level 2`.
- If the requester is listed in the custom path, the resolver should follow the existing custom resolver behavior and exclude the requester from approving their own loan.

## Workflow Delegates To Create

Package:

```text
com.justjava.humanresource.loan.workflow.delegate
```

### `InitializeLoanApprovalDelegate`

Responsibilities:

- Load loan application.
- Read the application approval route snapshot.
- For `ROLE_BASED`, set status to `PENDING_HR_APPROVAL` and create HR/Finance approval step placeholders.
- For `CUSTOM`, resolve and persist custom approval steps, set status to `PENDING_CUSTOM_APPROVAL`, and set the first custom approver.
- Record activity.
- Send HR pending notification for role-based route.
- Send custom approver pending notification for custom route.

### `ProcessHrLoanDecisionDelegate`

Responsibilities:

- Read HR decision variables.
- Validate decision.
- Record HR approver.
- Update the matching HR `EmployeeLoanApprovalStep`.
- Update status:
  - `HR_APPROVED` then `PENDING_FINANCE_APPROVAL`.
  - `REJECTED`.
  - `RETURNED_FOR_CORRECTION`.
- Record activity.
- Send employee/Finance notifications as applicable.

### `ProcessFinanceLoanDecisionDelegate`

Responsibilities:

- Read Finance decision variables.
- Validate decision.
- Record Finance approver.
- Update the matching Finance `EmployeeLoanApprovalStep`.
- Update status:
  - `FINANCE_APPROVED`.
  - `REJECTED`.
  - `RETURNED_FOR_CORRECTION`.
- Record activity.

### `ProcessCustomLoanDecisionDelegate`

Responsibilities:

- Read custom approval decision variables.
- Validate that the current user is the assigned custom approver for the active step.
- Update the matching `EmployeeLoanApprovalStep`.
- If approved and another custom approver remains, set the next custom approver task variables.
- If approved and no more custom approvers remain, set status to `CUSTOM_APPROVED`.
- If rejected, set rejection variables.
- If returned, set return variables.
- Record activity.
- Send employee and next-approver notifications as applicable.

### `ActivateApprovedLoanDelegate`

Responsibilities:

- Create `EmployeeLoanAccount` after Finance final approval or final custom approval.
- Generate locked `LoanRepaymentSchedule`.
- Mark application `ACTIVE`.
- Record activation/disbursement event.
- Notify employee.

### `FinalizeLoanRejectionDelegate`

Responsibilities:

- Set rejection status/timestamps.
- Record rejection activity.
- Notify employee.

### `ReturnLoanForCorrectionDelegate`

Responsibilities:

- Set application `RETURNED_FOR_CORRECTION`.
- Clear workflow instance.
- Keep prior approval steps for audit, and generate fresh approval steps when the employee resubmits.
- Record return activity.
- Notify employee.

## Payroll Integration

### New Payroll Service Integration Point

Create:

```text
LoanPayrollDeductionService
LoanPayrollDeductionServiceImpl
```

Use it from:

```text
PayrollOrchestrationServiceImpl.applyOtherDeductions(Long payrollRunId)
```

Required behavior:

1. Existing pay group and employee deductions should continue working.
2. Loan deductions should be added after existing other deductions are calculated.
3. The payroll run's `totalDeductions` and `netPay` should include loan repayments.
4. Loan repayment line items should have a predictable code, for example:

```text
LOAN_REPAYMENT_<loanAccountId>
```

5. Successful deduction should create a `LoanRepaymentTransaction`.
6. The due schedule line should be marked paid or partially paid.
7. The loan account outstanding balance should be reduced.
8. The loan account should be completed when outstanding balance reaches zero.
9. Missed deductions should be detected and flagged for HR/Finance visibility.

Important safety rule:

Do not replace or alter the existing deduction resolution logic. Add loan deductions as a controlled extra step.

## Existing Java Files To Edit

### `com.justjava.humanresource.approval.enums.ApprovalModuleType`

Reason:

- Allow the existing approval route resolver infrastructure to identify loan approval contexts.

Change:

- Add:

```java
LOAN
```

Safety:

- Add enum value only.
- Do not rename or reorder existing values in a way that would break persisted strings.

### `com.justjava.humanresource.approval.service.impl.CustomApprovalRouteResolver`

Reason:

- Existing custom approval paths currently support request and exit modules. Loans need to reuse the same path definition and employee-step resolver.

Change:

- Include `ApprovalModuleType.LOAN` in the resolver's supported module types.

Safety:

- Keep existing request and exit behavior unchanged.
- Do not change how steps are ordered, deduplicated, or requester self-approval is excluded.

### `com.justjava.humanresource.core.config.AuthenticationManager`

Reason:

- Add or reuse clear role helpers for HR and Finance.

Possible changes:

- Keep existing `isHumanResource()`.
- Keep existing `isFinancialOfficer()`.
- Add helper if useful:

```java
public boolean isLoanHrApprover()
public boolean isLoanFinanceApprover()
```

Safety:

- Do not change current group normalization behavior.

### `com.justjava.humanresource.workflow.service.FlowableTaskService`

Reason:

- Current task service supports assignee tasks and task definition queries, but loans need candidate group queries for role-based approvals and assignee queries for custom approval tasks.

Possible additions:

- `getTasksForCandidateGroup(String group, String processDefinitionKey)`
- `getTasksForCandidateGroups(Collection<String> groups, String processDefinitionKey)`
- `isTaskCandidateForGroup(String taskId, String group)`
- `claimCandidateTask(String taskId, String assignee)` if Flowable requires a candidate-group task to be claimed before completion.
- `getTask(String taskId)` or equivalent helper if the loan service needs to inspect task variables safely.

Safety:

- Add methods only.
- Do not change current methods used by requests, payroll, or finance lock approval.

### `com.justjava.humanresource.payroll.workflow.impl.PayrollOrchestrationServiceImpl`

Reason:

- Add loan deduction application during `applyOtherDeductions`.

Change:

- Inject `LoanPayrollDeductionService`.
- After existing deductions are calculated, apply due loan deductions and include them in totals.

Safety:

- Keep existing deduction behavior.
- Keep existing statutory deduction behavior.
- Make loan deduction idempotent for payroll recalculation/retry.

### `com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository`

Reason:

- May need repository helpers for deleting/replacing loan repayment line items idempotently.

Possible additions:

- `deleteByPayrollRunIdAndComponentCodeStartingWith(...)` or explicit query.
- Query for loan deduction line by payroll run and component code.

Safety:

- Add methods only.
- Do not alter existing queries.

### `com.justjava.humanresource.payroll.entity.PayrollLineItem`

Reason:

- Ideally no change.

Expected:

- Use existing `componentCode`, `description`, `amount`, and `componentType=DEDUCTION`.

Safety:

- Avoid adding loan-specific fields here unless later proven necessary.

### `com.justjava.humanresource.utils`

Reason:

- Add loan notification support.

Create preferred:

```text
LoanEmailService
```

or use:

```text
LoanNotificationService
```

Safety:

- Do not modify `RequestEmailService` unless only adding shared formatting helpers.

## Existing Frontend Files To Edit

### HR/Admin Layout

File:

```text
src/main/resources/templates/layout.html
```

Change:

- Add sidebar link to `/loans`.
- Label: `Employee Loans`.
- Show for HR/admin users only where possible.

Safety:

- Do not remove or rename existing links.

### Employee Layout

File:

```text
src/main/resources/templates/layouts/employeeLayout.html
```

Change:

- Add sidebar link to `/employee/loans`.
- Label: `Loans`.

Safety:

- Do not alter existing employee routes.

### Finance Layout

File:

```text
src/main/resources/templates/layouts/financialOfficerLayout.html
```

Change:

- Add sidebar link to `/finance/loans`.
- Label: `Employee Loans`.

Safety:

- Do not alter existing finance dashboard, posting, bank details, or accounting settings links.

## Frontend Templates To Create

Package/path:

```text
src/main/resources/templates/loan
```

### `employee-main.html`

Purpose:

Employee loan dashboard.

Sections:

- Summary cards:
  - Draft applications.
  - Pending applications.
  - Active loans.
  - Outstanding balance.
- Available loan products.
- New application modal/form.
- Repayment preview panel.
- Application table.
- Active loan table.
- Assigned custom loan approval tasks table where the logged-in employee is a configured custom approver.

Actions:

- Create draft.
- Edit draft.
- Delete draft.
- Submit.
- Cancel allowed application.
- Open detail.
- Approve/reject/return assigned custom approval tasks.

### `employee-detail.html`

Purpose:

Employee loan detail view.

Sections:

- Application details.
- Approval status/timeline.
- Approval route and approval step history.
- Repayment schedule.
- Repayment history.
- Attachments.
- Missed deduction notices.

Actions:

- Edit if draft/returned.
- Submit/resubmit.
- Cancel where allowed.
- Approve/reject/return where the logged-in user is the assigned custom approver.
- Upload/remove attachment where allowed.

### `hr-main.html`

Purpose:

HR loan workspace.

Sections:

- Dashboard cards.
- Loan product setup tab.
- Approval route selector in product setup:
  - `Role-based HR then Finance`
  - `Custom approval path`
- Custom approval path dropdown populated from enabled paths when custom route is selected.
- Pending HR approvals tab.
- Custom approval overview tab showing applications currently waiting on configured custom approvers.
- All applications tab.
- Active loans tab.
- Missed deductions tab.

Actions:

- Create/edit product.
- Deactivate/reactivate product.
- Delete unused product.
- Open application detail.
- Approve/reject/return HR task.
- Inspect custom approval progress for products configured with custom paths.

### `hr-detail.html`

Purpose:

HR loan application detail.

Sections:

- Application terms.
- Employee context:
  - Salary.
  - Grade.
  - Step.
  - Department.
  - Employment status.
- Existing active loans.
- Pending loan applications.
- Outstanding loan balances.
- Repayment preview/schedule.
- Attachments.
- Activity history.
- Approval route snapshot and step-by-step approval history.

Actions:

- Approve.
- Reject.
- Return for correction.
- View custom path approvers and current custom approval owner.

### `finance-main.html`

Purpose:

Finance loan workspace.

Sections:

- Dashboard cards.
- Pending Finance approval queue.
- Role-based approvals that have reached Finance.
- Active loans.
- Completed loans.
- Missed deductions.
- Payroll deduction impact.

Actions:

- Open detail.
- Approve/reject/return Finance task.

### `finance-detail.html`

Purpose:

Finance loan detail.

Sections:

- Application terms.
- HR approval result.
- Approval route snapshot.
- Employee context.
- Existing loan exposure.
- Repayment schedule.
- Payroll impact preview.
- Attachments.
- Activity history.

Actions:

- Final approve.
- Reject.
- Return for correction.

For custom approval path loans, Finance should be able to view the approved loan after activation for monitoring and payroll impact, but Finance should not receive a final approval task unless the selected custom approval path explicitly includes a Finance employee as one of its approvers.

## Frontend Quality Expectations

The loan pages should be better structured than the current large request templates.

Recommended approach:

- Keep pages Thymeleaf-based to match the current app.
- Use Bootstrap/Tailwind-style utility patterns already present.
- Move larger JavaScript into static JS files where practical:

```text
src/main/resources/static/js/employee-loans.js
src/main/resources/static/js/hr-loans.js
src/main/resources/static/js/finance-loans.js
```

The UI should include:

- Clear dashboard cards.
- Status badges.
- Repayment calculator.
- Tables with filters.
- Dedicated detail pages.
- Attachments area.
- Approval action panels.
- Approval route badges, for example `Role-based` or `Custom path`.
- Custom approval step timeline showing pending, approved, rejected, and returned steps.
- Warnings for existing active/unsettled loans.
- Warnings for missed deductions.

## Static JavaScript Files To Create

Optional but recommended:

```text
src/main/resources/static/js/employee-loans.js
src/main/resources/static/js/hr-loans.js
src/main/resources/static/js/finance-loans.js
```

Purpose:

- Keep templates maintainable.
- Avoid repeating large embedded scripts.
- Centralize API calls and rendering logic for each role.

## Existing Configuration Files To Edit

### `src/main/resources/application.yml`

Expected:

- No required change for database because app uses `spring.jpa.hibernate.ddl-auto=update`.

Possible future optional config:

```yaml
loan:
  payroll:
    component-code-prefix: LOAN_REPAYMENT
```

Safety:

- Avoid adding required environment variables for initial implementation.

## Tests To Create

### Service Tests

Create under:

```text
src/test/java/com/justjava/humanresource/loan
```

Tests:

- `LoanRepaymentCalculationServiceTest`
- `LoanProductServiceTest`
- `EmployeeLoanApplicationServiceTest`
- `LoanApprovalServiceTest`
- `LoanApprovalRouteServiceTest`
- `EmployeeLoanAccountServiceTest`
- `LoanPayrollDeductionServiceTest`

### Workflow Tests

Tests:

- `EmployeeLoanProcessDefinitionTest`
- `EmployeeLoanApprovalWorkflowTest`

Purpose:

- Validate BPMN loads.
- Validate HR approve -> Finance task.
- Validate HR reject.
- Validate Finance approve -> active loan.
- Validate return for correction.
- Validate custom path first approver task.
- Validate custom path multi-step approval.
- Validate custom path rejection.
- Validate custom path return for correction.
- Validate final custom approval -> active loan.
- Validate disabled or empty custom path blocks submission.

### Controller Tests

Tests:

- `EmployeeLoanControllerTest`
- `HrLoanControllerTest`
- `FinanceLoanControllerTest`
- `LoanProductControllerTest`

### Payroll Integration Tests

Tests:

- Loan deduction appears in payroll line items.
- Existing deductions still appear.
- Total deductions include loan repayment.
- Net pay reflects loan repayment.
- Loan balance updates after payroll.
- Missed deduction is flagged when expected deduction is not applied.

## Implementation Phases

### Phase 1: Domain Foundation

- Create enums.
- Create entities.
- Create repositories.
- Create loan number service.
- Create product service.
- Create repayment calculation service.
- Add `ApprovalModuleType.LOAN`.
- Extend custom approval route resolution to support loan contexts.

### Phase 2: Employee Application

- Create employee page controller.
- Create employee API controller.
- Create employee templates.
- Implement draft, edit, delete draft, submit, cancel, resubmit.
- Implement repayment preview.
- Implement attachments.
- Snapshot loan product approval route when submitting.

### Phase 3: Loan Approval Workflow

- Create BPMN process.
- Create workflow delegates.
- Create approval route service.
- Create approval service.
- Create HR/Finance task APIs.
- Create employee custom approval task APIs.
- Implement HR approve/reject/return.
- Implement Finance approve/reject/return.
- Implement custom approver approve/reject/return.
- Implement automatic activation after Finance approval.
- Implement automatic activation after final custom approval.

### Phase 4: HR And Finance Pages

- Create HR page and APIs.
- Create Finance page and APIs.
- Add layout links.
- Add dashboard, approval queues, detail pages, exposure warnings.
- Add loan product approval route controls and custom path dropdown.
- Add approval route badges and step timelines.
- Add custom approval task surface for assigned approvers.

### Phase 5: Payroll Integration

- Create loan payroll deduction service.
- Integrate into `PayrollOrchestrationServiceImpl.applyOtherDeductions`.
- Add loan repayment line items.
- Record repayment transactions.
- Update balances.
- Mark loans completed.
- Flag missed deductions.

### Phase 6: Reporting, Notifications, And Polish

- Add notification service.
- Add dashboard summaries.
- Add missed deduction reports.
- Add activity timelines.
- Add frontend refinements.
- Add tests.

## Files To Create Summary

### Backend Java

```text
loan/entity/LoanProduct.java
loan/entity/EmployeeLoanApplication.java
loan/entity/EmployeeLoanApprovalStep.java
loan/entity/EmployeeLoanAccount.java
loan/entity/LoanRepaymentSchedule.java
loan/entity/LoanRepaymentTransaction.java
loan/entity/EmployeeLoanAttachment.java
loan/entity/EmployeeLoanActivity.java
loan/entity/LoanNumberCounter.java

loan/enums/LoanInterestType.java
loan/enums/LoanRepaymentFrequency.java
loan/enums/LoanApplicationStatus.java
loan/enums/LoanAccountStatus.java
loan/enums/LoanRepaymentStatus.java
loan/enums/LoanApprovalDecision.java
loan/enums/LoanApprovalRouteType.java
loan/enums/LoanApprovalStage.java
loan/enums/LoanActivityType.java
loan/enums/LoanAttachmentType.java
loan/enums/LoanRepaymentTransactionType.java

loan/dto/LoanProductCommand.java
loan/dto/LoanProductResponse.java
loan/dto/LoanProductSummaryResponse.java
loan/dto/LoanApplicationCommand.java
loan/dto/LoanApplicationResponse.java
loan/dto/LoanApplicationSummaryResponse.java
loan/dto/LoanApplicationDetailResponse.java
loan/dto/LoanApplicationEditResponse.java
loan/dto/LoanApprovalStepResponse.java
loan/dto/LoanApprovalActionCommand.java
loan/dto/LoanApprovalTaskResponse.java
loan/dto/LoanApprovalContextResponse.java
loan/dto/LoanApprovalRouteResponse.java
loan/dto/LoanRepaymentPreviewRequest.java
loan/dto/LoanRepaymentPreviewResponse.java
loan/dto/LoanRepaymentScheduleLineResponse.java
loan/dto/LoanRepaymentTransactionResponse.java
loan/dto/EmployeeLoanDashboardResponse.java
loan/dto/HrLoanDashboardResponse.java
loan/dto/FinanceLoanDashboardResponse.java
loan/dto/EmployeeLoanExposureResponse.java
loan/dto/LoanMissedDeductionResponse.java

loan/repository/LoanProductRepository.java
loan/repository/EmployeeLoanApplicationRepository.java
loan/repository/EmployeeLoanApprovalStepRepository.java
loan/repository/EmployeeLoanAccountRepository.java
loan/repository/LoanRepaymentScheduleRepository.java
loan/repository/LoanRepaymentTransactionRepository.java
loan/repository/EmployeeLoanAttachmentRepository.java
loan/repository/EmployeeLoanActivityRepository.java
loan/repository/LoanNumberCounterRepository.java

loan/service/LoanProductService.java
loan/service/EmployeeLoanApplicationService.java
loan/service/LoanApprovalService.java
loan/service/LoanApprovalRouteService.java
loan/service/LoanRepaymentCalculationService.java
loan/service/EmployeeLoanAccountService.java
loan/service/LoanPayrollDeductionService.java
loan/service/LoanAttachmentService.java
loan/service/LoanActivityService.java
loan/service/LoanNumberService.java
loan/service/LoanNotificationService.java
loan/service/LoanEmployeeContextService.java

loan/service/impl/LoanProductServiceImpl.java
loan/service/impl/EmployeeLoanApplicationServiceImpl.java
loan/service/impl/LoanApprovalServiceImpl.java
loan/service/impl/LoanApprovalRouteServiceImpl.java
loan/service/impl/LoanRepaymentCalculationServiceImpl.java
loan/service/impl/EmployeeLoanAccountServiceImpl.java
loan/service/impl/LoanPayrollDeductionServiceImpl.java
loan/service/impl/LoanAttachmentServiceImpl.java
loan/service/impl/LoanActivityServiceImpl.java
loan/service/impl/LoanNumberServiceImpl.java
loan/service/impl/LoanNotificationServiceImpl.java
loan/service/impl/LoanEmployeeContextServiceImpl.java

loan/controller/EmployeeLoanPageController.java
loan/controller/HrLoanPageController.java
loan/controller/FinanceLoanPageController.java
loan/controller/LoanProductController.java
loan/controller/EmployeeLoanController.java
loan/controller/HrLoanController.java
loan/controller/FinanceLoanController.java

loan/workflow/delegate/InitializeLoanApprovalDelegate.java
loan/workflow/delegate/ProcessHrLoanDecisionDelegate.java
loan/workflow/delegate/ProcessFinanceLoanDecisionDelegate.java
loan/workflow/delegate/ProcessCustomLoanDecisionDelegate.java
loan/workflow/delegate/ActivateApprovedLoanDelegate.java
loan/workflow/delegate/FinalizeLoanRejectionDelegate.java
loan/workflow/delegate/ReturnLoanForCorrectionDelegate.java
```

### BPMN

```text
src/main/resources/processes/employeeLoanApprovalProcess.bpmn
```

### Frontend Templates

```text
src/main/resources/templates/loan/employee-main.html
src/main/resources/templates/loan/employee-detail.html
src/main/resources/templates/loan/hr-main.html
src/main/resources/templates/loan/hr-detail.html
src/main/resources/templates/loan/finance-main.html
src/main/resources/templates/loan/finance-detail.html
```

### Static JavaScript

```text
src/main/resources/static/js/employee-loans.js
src/main/resources/static/js/hr-loans.js
src/main/resources/static/js/finance-loans.js
```

## Files To Edit Summary

```text
src/main/java/com/justjava/humanresource/core/config/AuthenticationManager.java
src/main/java/com/justjava/humanresource/approval/enums/ApprovalModuleType.java
src/main/java/com/justjava/humanresource/approval/service/impl/CustomApprovalRouteResolver.java
src/main/java/com/justjava/humanresource/workflow/service/FlowableTaskService.java
src/main/java/com/justjava/humanresource/payroll/workflow/impl/PayrollOrchestrationServiceImpl.java
src/main/java/com/justjava/humanresource/payroll/repositories/PayrollLineItemRepository.java
src/main/resources/templates/layout.html
src/main/resources/templates/layouts/employeeLayout.html
src/main/resources/templates/layouts/financialOfficerLayout.html
```

Optional:

```text
src/main/resources/application.yml
```

## Current Feature Protection Rules

- Do not change existing `RequestType` values.
- Do not modify `genericRequestApprovalProcess.bpmn`.
- Do not modify current request delegates.
- Do not change existing request custom approval path behavior.
- Reuse custom approval paths for loans through `ApprovalModuleType.LOAN`; do not make loans another `RequestType`.
- Do not alter existing payroll statutory deduction logic.
- Do not replace employee/pay group deduction resolution.
- Do not remove or rename existing routes/templates.
- Add new methods to shared services instead of changing existing method behavior.
- Keep loan-specific logic inside `com.justjava.humanresource.loan` except for controlled integration points.

## Final Expected User Flow

1. HR creates active loan products from `/loans`.
   - HR selects either `Role-based HR then Finance` or `Custom approval path`.
   - If custom path is selected, HR chooses an enabled custom approval path.
2. Employee opens `/employee/loans`.
3. Employee creates a draft loan application.
4. Employee previews repayment schedule.
5. Employee submits the application.
6. System snapshots the loan product approval route.
7. If the route is role-based, application enters HR approval.
8. Any Human Resource user can approve/reject/return the HR task.
9. If HR approves, application enters Finance approval.
10. Any Finance Officer can approve/reject/return the Finance task.
11. If Finance approves, the loan activates automatically.
12. If the route is custom, the application follows the selected custom approval path employee by employee.
13. Each assigned custom approver can approve/reject/return from their assigned loan approval task.
14. If the final custom approver approves, the loan activates automatically.
15. Repayment schedule locks.
16. Payroll automatically deducts repayments from the selected start month.
17. Repayment transactions update outstanding balance.
18. Missed deductions are flagged for HR/Finance review.
19. Loan completes automatically when fully repaid.

