# Employee Loan Feature Flow Report

## Purpose

The Employee Loan feature will allow the organization to manage staff loan products from setup through employee application, configurable approval, activation, and automatic payroll deduction.

The feature will be built as a dedicated Employee Loans area instead of being hidden only inside the general request workflow. Employees, HR users, and Finance users will each have loan pages suited to their role.

## Goals

- Allow HR to configure employee loan products and policy rules.
- Allow employees to apply for loans from their employee portal.
- Allow employees to choose both repayment start month and repayment plan details.
- Support the default approval route where submitted loan applications go first to HR, then to Finance.
- Support a custom approval path route where a loan product can be configured to use a named sequence of employee approvers.
- Allow all Human Resource users to act on the HR approval stage when the default role-based route is used.
- Allow all Finance Officers to act on the final finance approval stage when the default role-based route is used.
- Allow assigned custom approvers to act on their own custom loan approval tasks when a custom route is used.
- Automatically activate approved loans after Finance approval or after the final custom approval.
- Automatically deduct loan repayments from payroll starting from the employee-selected repayment month.
- Give employees, HR, and Finance visibility into loan status, balances, and repayment progress.

## User Roles

### Employee

Employees will use the loan page to:

- View available loan products.
- Review loan terms before applying.
- Submit a loan application.
- Choose the repayment start month.
- Choose repayment tenor and repayment amount, subject to validation.
- Preview repayment schedule before submission.
- Track submitted loan applications.
- View approved/active loans.
- View repayment history and outstanding balance.

### Human Resource

HR users will use the loan page to:

- Configure loan products and policies.
- Choose the approval route for each loan product:
  - Default role-based HR then Finance approval.
  - Custom approval path.
- Review submitted employee loan applications.
- Approve, reject, or return loan applications for correction.
- View employee loan history.
- Monitor active and completed loans.
- Track HR approval workload.
- Track loans currently waiting on custom approvers.

### Finance Officer

Finance users will use the loan page to:

- Review HR-approved loan applications when the default role-based route is used.
- Give final approval or reject the loan when the default role-based route is used.
- Confirm that the loan is financially acceptable before activation.
- View approved loans that will affect payroll.
- Monitor active loan deductions and outstanding balances.

### Custom Approver

Custom approvers are employees selected as steps in a configured custom approval path.

Custom approvers will use their assigned loan approval task area to:

- Review the submitted loan details.
- View employee context needed for a decision.
- Approve the loan to the next custom approver.
- Reject the loan.
- Return the loan for correction.

A custom approver does not need to be an HR or Finance user unless the configured custom path intentionally selects an HR or Finance employee.

## Dedicated Pages

### Employee Portal: Loan Page

The employee portal layout will include a dedicated `Loan` page.

This page should contain:

- Loan dashboard summary.
- Available loan products.
- New loan application form.
- Repayment calculator/preview.
- Application status list.
- Active loan list.
- Assigned custom approval tasks where the employee is a custom approver.
- Repayment schedule and repayment history.

Expected employee actions:

- Apply for loan.
- Save as draft where applicable.
- Edit draft loan application.
- Delete draft loan application.
- Submit application.
- Cancel draft or pending application where allowed.
- Correct and resubmit returned loan application.
- View approval status.
- View repayment progress.
- Approve, reject, or return assigned custom approval tasks.

### HR Layout: Employee Loans Page

The HR layout will include an `Employee Loans` page.

This page should contain:

- Loan setup/configuration area.
- Approval route setup for each loan product.
- Pending HR approval queue.
- Custom approval monitoring queue.
- All loan applications list.
- Employee loan history lookup.
- Active loan monitoring.
- Loan product status management.

Expected HR actions:

- Create loan product.
- Edit loan product.
- Select role-based or custom approval routing for a loan product.
- Select an enabled custom approval path where custom routing is used.
- Enable/disable loan product.
- Delete loan product only where it has never been used.
- Review application.
- Approve application for Finance review.
- Reject application.
- Return application for correction.
- View repayment schedule and employee loan exposure.
- View active, completed, rejected, and cancelled loan records.
- View custom approval progress and current custom approver.

### Finance Layout: Employee Loans Page

The Finance layout will include an `Employee Loans` page.

This page should contain:

- Pending Finance approval queue.
- Role-based finance approval queue.
- Finance-approved loan list.
- Active loan repayment monitoring.
- Loan balance summary.
- Payroll deduction impact view.

Expected Finance actions:

- Review HR-approved loan application.
- Give final approval.
- Reject application.
- View repayment schedule before final approval.
- View deductions expected in future payroll periods.
- View active, completed, rejected, and cancelled loan records.
- View missed deduction flags.

For loan products that use custom approval paths, Finance should still see activated loans for monitoring and payroll impact, but Finance should not receive a final approval task unless the selected custom approval path includes a Finance employee as one of its approvers.

## Loan Setup Flow

HR will configure loan products before employees can apply.

Loan setup should include:

- Loan product name.
- Description/purpose.
- Minimum loan amount.
- Maximum loan amount.
- Minimum repayment amount.
- Maximum repayment tenor.
- Interest type:
  - Interest-free loan.
  - Interest-bearing loan.
- Interest rate where applicable.
- Repayment frequency, initially monthly.
- Eligibility rules.
- Required supporting documents.
- Approval route:
  - Role-based HR then Finance.
  - Custom approval path.
- Custom approval path, required only when the custom route is selected.
- Whether the loan product is active or inactive.

The configured setup determines what the employee can select during application and which approval route the application will follow after submission.

## Loan Approval Route Setup

Each loan product should define how applications for that product are approved.

### Role-Based HR Then Finance

This is the default route.

Flow:

1. Employee submits the loan application.
2. Any eligible Human Resource user can approve, reject, or return the HR approval task.
3. If HR approves, any eligible Finance Officer can approve, reject, or return the Finance approval task.
4. If Finance approves, the loan activates automatically.

This route is best for normal loan products where HR policy review and Finance final review are always required.

### Custom Approval Path

This route uses an existing custom approval path configured by HR.

Flow:

1. Employee submits the loan application.
2. The system snapshots the selected custom approval path from the loan product.
3. The first employee in the custom path receives the approval task.
4. If the first approver approves, the task moves to the next custom approver.
5. If any custom approver rejects, the loan is rejected.
6. If any custom approver returns the application, the employee can correct and resubmit.
7. If the final custom approver approves, the loan activates automatically.

This route is best for products that need special approval chains, for example executive loans, department-specific loans, or loan products requiring a named finance director, CEO, or committee representative.

The custom approval path is employee-specific, not group-specific. If the organization wants any HR user or any Finance Officer to act, the role-based route should be used.

Approval route selection should be locked onto the submitted application. Later changes to the loan product's approval route should affect only new applications, not applications already submitted.

## Loan Setup Lifecycle

Loan setup records should have controlled lifecycle behavior because employee applications and approved loans depend on them.

### Create

HR can create new loan products from the HR Employee Loans page.

### Edit

HR can edit a loan product while it has no submitted, approved, or active loan records attached to it.

If a loan product has already been used, HR should only be allowed to edit non-financial display fields that do not change existing agreements, such as:

- Product description.
- Visibility/help text.
- Required document notes.
- Active/inactive status.

HR should not be allowed to edit historical financial terms in a way that changes existing applications or active loans. Examples of restricted fields after use:

- Minimum loan amount.
- Maximum loan amount.
- Minimum repayment amount.
- Maximum tenor.
- Interest type.
- Interest rate.
- Repayment calculation rules.
- Approval route type.
- Selected custom approval path.

If HR needs different financial rules after a product has already been used, the expected action is to deactivate the old product and create a new product.

If HR needs a different approval route for future applications after a product has already been used, the safest action is also to deactivate the old product and create a new product with the new approval route. This prevents in-flight or historical loan applications from appearing to have followed a route that was not valid when they were submitted.

### Disable Or Make Inactive

HR can make a loan product inactive.

Inactive loan products:

- Should no longer appear to employees for new applications.
- Should remain visible to HR and Finance for reporting/history.
- Should not affect existing draft, submitted, approved, active, or completed loans that already reference the product.

### Delete

Loan products should only be deletable if they have never been used by any draft, submitted, approved, active, cancelled, rejected, or completed loan record.

Once a loan product has been used, it should not be hard-deleted. It should be made inactive instead.

### Reactivate

HR can reactivate an inactive loan product if the product is still valid and should be available for new employee applications again.

## Employee Application Flow

1. Employee opens the Loan page.
2. Employee selects an available loan product.
3. System displays the loan rules and limits.
4. Employee enters requested loan amount.
5. Employee chooses repayment start month.
6. Employee chooses repayment tenor and repayment amount.
7. System validates both repayment tenor and repayment amount against HR setup.
8. System calculates repayment schedule preview.
9. Employee enters reason/purpose and uploads required documents if needed.
10. Employee submits the loan application.
11. System snapshots the loan product approval route.
12. Application enters either the role-based approval flow or the custom approval path flow.

## Loan Application Lifecycle

Loan applications should follow clear edit, delete, cancellation, and correction rules.

### Draft

Employees can create draft loan applications before submission.

In draft status, the employee can:

- Edit selected loan product if no product-specific fields have been locked.
- Edit requested loan amount.
- Edit repayment start month.
- Edit repayment tenor.
- Edit repayment amount.
- Edit reason/purpose.
- Add, replace, or remove supporting attachments.
- Delete the draft.
- Submit the application.

When draft values change, the repayment validation and repayment schedule preview should be recalculated.

### Submitted And Pending Approval

After submission, the employee should not be able to edit the application directly.

The employee can view the application and approval status.

The employee may cancel the application while it is still at the first approval stage, provided no approver has approved it yet.

For role-based approval, this first stage is HR approval.

For custom approval, this first stage is the first custom approver.

HR can:

- Review the submitted details.
- View employee context and existing loan exposure.
- Approve to Finance.
- Reject.
- Return for correction.

HR should not be allowed to change the employee-selected loan amount, repayment amount, repayment tenor, or repayment start month.

For a custom approval path, the assigned custom approver can:

- Review the submitted details.
- View employee context and existing loan exposure.
- Approve to the next custom approver.
- Reject.
- Return for correction.

Custom approvers should not be allowed to change the employee-selected loan amount, repayment amount, repayment tenor, or repayment start month.

### Returned For Correction

If HR, Finance, or a custom approver returns the application for correction, the employee can edit only the returned application fields and resubmit.

Editable fields after return should include:

- Requested loan amount.
- Repayment start month.
- Repayment tenor.
- Repayment amount.
- Reason/purpose.
- Attachments.

The application should keep its history, comments, and previous approval activity.

The employee should not need to create a new application unless they choose to cancel the returned one.

### HR Approved And Pending Finance Approval

Once HR approves the application and sends it to Finance, the employee should not be able to edit or cancel it.

Finance can:

- Review all loan details.
- View employee salary, grade/step, department, and active loan exposure.
- Approve and activate the loan.
- Reject.
- Return for correction where correction is still appropriate.

Finance should not be allowed to change the approved amount, repayment amount, repayment tenor, or repayment start month.

This lifecycle state applies only to role-based loan approval.

### Pending Custom Approval

For custom approval path loans, the application remains in custom approval until every configured custom approver has acted.

Each custom approval step should show:

- Sequence number.
- Approver name.
- Pending, approved, rejected, or returned status.
- Decision comment.
- Decision date and time.

The employee should not be able to edit or cancel the application after any custom approver has approved it. If correction is needed, the current custom approver should return the application for correction.

### Final Approval And Active

After Finance final approval on the role-based route, or after final custom approval on the custom route, the loan becomes active automatically.

At this point:

- The loan terms should be locked.
- The repayment schedule should be locked.
- The loan should no longer be editable by Employee, HR, or Finance.
- Payroll deduction should begin from the employee-selected repayment start month.

Any later correction should be handled as an administrative/audit process, not as normal editing.

### Rejected

Rejected applications should remain visible for history.

Rejected applications should not be editable or resubmitted. If the employee still wants a loan, they should create a new application.

Rejected applications should not be hard-deleted.

### Cancelled

Cancelled applications should remain visible for history.

An employee can cancel:

- A draft application.
- A submitted application that has not yet been approved by HR.
- A returned application before resubmission.

An employee should not be able to cancel after HR approval has already moved the application to Finance.

Cancelled applications should not be hard-deleted after submission.

### Completed

Completed loans are loans where the full principal and applicable interest have been repaid.

Completed loans should remain visible to Employee, HR, and Finance based on their access level.

Completed loans should not be editable or deletable.

### Closed

Closed should be used for administrative finalization where needed after completion or exceptional handling.

Closed records should remain visible for reporting and audit.

## Repayment Validation

The employee will be able to choose both repayment tenor and repayment amount, but the system must validate the selection.

Validation should ensure:

- Requested amount is within configured minimum and maximum amount.
- Repayment amount is not below the configured minimum repayment.
- Tenor does not exceed configured maximum tenor.
- Repayment amount and tenor are consistent enough to repay the loan.
- Interest-bearing loans include interest in the repayment calculation.
- Interest-free loans calculate repayment only against principal.
- Repayment starts from the employee-selected month.
- Repayment start month cannot be earlier than allowed by policy.

If the employee chooses a repayment amount that does not clear the loan within the selected tenor, the system should reject the combination or ask the employee to adjust one of the values.

Validation should run whenever the employee creates, edits, or resubmits a loan application.

## Repayment Schedule Lifecycle

Before final approval, the repayment schedule is a preview.

The preview can change when the employee edits:

- Requested loan amount.
- Repayment start month.
- Repayment amount.
- Repayment tenor.

After final approval, the repayment schedule becomes locked.

Locked repayment schedules:

- Should drive payroll deductions.
- Should show expected repayment months.
- Should show expected principal and interest portions where interest applies.
- Should show paid, pending, missed, and completed repayment rows.
- Should not be recalculated automatically after approval unless a formal administrative correction feature is added later.

## Approval Flow

The approval flow will support two routes.

### Default Role-Based Route

1. Employee submits loan application.
2. Application goes to HR approval.
3. Any eligible Human Resource user can review and act on the HR approval task.
4. If HR approves, the application goes to Finance.
5. Any eligible Finance Officer can review and act on the Finance approval task.
6. If Finance gives final approval, the loan becomes active automatically.
7. Payroll deduction begins from the repayment start month chosen by the employee.

Possible approval outcomes:

- Approved by HR and sent to Finance.
- Rejected by HR.
- Returned by HR for correction.
- Approved by Finance and activated.
- Rejected by Finance.
- Returned by Finance where correction is allowed.

### Custom Approval Path Route

1. Employee submits loan application.
2. Application goes to the first employee in the configured custom approval path.
3. The assigned custom approver reviews and approves, rejects, or returns the application.
4. If approved and more custom approvers remain, the application moves to the next custom approver.
5. If rejected, the application is rejected.
6. If returned, the employee can correct and resubmit.
7. If the final custom approver approves, the loan becomes active automatically.
8. Payroll deduction begins from the repayment start month chosen by the employee.

Possible custom approval outcomes:

- Approved by one custom approver and sent to the next custom approver.
- Rejected by any custom approver.
- Returned by any custom approver for correction.
- Approved by final custom approver and activated.

The application detail page should always show which route was used, because the meaning of "final approval" differs by route.

## Automatic Payroll Deduction Flow

After final approval:

1. The loan is marked as approved/active.
2. The system records the activation/disbursement event automatically, including timestamp and system/actor context.
3. The repayment schedule is locked in.
4. The system prepares deductions according to the selected repayment start month.
5. When payroll is processed for the repayment start month and later months, the repayment amount is deducted automatically.
6. Each successful payroll deduction updates the loan balance.
7. The loan is marked completed when the full principal and applicable interest have been repaid.

Payroll deduction must respect the employee-selected repayment start month. If the employee chooses May 2027, deductions should not begin before the May 2027 payroll.

If an expected loan deduction is missed during payroll, the system should not automatically double-deduct or change the repayment schedule. The missed repayment should be flagged for HR/Finance review so they can decide the next action.

Since manual repayment and early repayment are outside the current scope, missed deduction handling for this phase should focus on visibility:

- Mark the expected repayment row as missed.
- Show the missed deduction on HR and Finance loan pages.
- Show the missed deduction on the employee loan detail page.
- Keep the outstanding balance accurate.
- Require HR/Finance awareness before any later recovery process is introduced.

## Loan Statuses

Expected loan/application statuses:

- Draft.
- Submitted.
- Pending HR Approval.
- Pending Custom Approval.
- Returned for Correction.
- HR Approved.
- Pending Finance Approval.
- Finance Approved.
- Custom Approved.
- Active.
- Rejected.
- Cancelled.
- Completed.
- Closed.

The final naming can be adjusted later, but the flow should clearly separate application approval status from active repayment status.

## Deletion And Record Retention Rules

Financial and approval records should not be hard-deleted once they have entered a submitted, approved, active, rejected, cancelled, completed, or closed state.

Allowed deletion:

- Employee can delete own draft loan application.
- HR can delete an unused loan setup that has never been referenced.

Not allowed for hard deletion:

- Submitted loan applications.
- Returned loan applications.
- HR-approved applications.
- Finance-approved applications.
- Custom-approved applications.
- Rejected applications.
- Cancelled submitted applications.
- Active loans.
- Completed loans.
- Payroll deduction history.
- Repayment history.

Where a record should no longer be used, it should be cancelled, rejected, closed, or made inactive instead of deleted.

## Audit And Activity History

The loan feature should keep an activity history for important actions.

Activities should include:

- Loan product created.
- Loan product edited.
- Loan product made inactive.
- Loan product reactivated.
- Draft loan created.
- Draft loan edited.
- Application submitted.
- Application cancelled.
- Application returned for correction.
- Application resubmitted.
- HR approved.
- HR rejected.
- Finance approved.
- Finance rejected.
- Custom approval started.
- Custom approver approved.
- Custom approver rejected.
- Custom approver returned.
- Loan activated.
- Payroll deduction applied.
- Payroll deduction missed.
- Loan completed.
- Loan closed.

Each activity should preserve who performed the action where applicable and when it happened.

## Notifications

The feature should notify relevant users when:

- Employee submits a loan application.
- HR users have a pending approval.
- HR approves, rejects, or returns an application.
- Finance users have a pending final approval.
- Finance approves or rejects an application.
- A custom approver has a pending approval task.
- A custom approver approves, rejects, or returns an application.
- Loan becomes active.
- Loan repayment starts.
- Loan is fully repaid.

## Reporting And Visibility

The loan pages should support basic tracking for:

- Pending applications.
- Applications pending HR approval.
- Applications pending Finance approval.
- Applications pending custom approval.
- Approved applications.
- Rejected applications.
- Active loans.
- Completed loans.
- Outstanding principal.
- Outstanding interest where applicable.
- Monthly expected deductions.
- Actual repayment history.
- Missed deductions.
- Loan product usage.
- Applications by status.
- Active loan exposure by employee.

HR should have visibility across employees. Finance should have visibility into loans affecting payroll and financial exposure. Employees should only see their own loans.

Assigned custom approvers should be able to see only the loan applications they are assigned to approve, plus the decision context required for that approval. Being a custom approver should not grant access to all employee loans.

## Multiple Active Loans

Employees will be allowed to apply for multiple loans.

The system should not automatically block a new loan application because the employee already has an active or unsettled loan. However, before HR or Finance approves another loan, the application detail page should clearly show whether the employee has existing active loans, pending loans, or unsettled balances.

The decision to approve or reject an additional loan remains with HR and Finance.

## Approval Detail Context

The HR, Finance, and assigned custom approver loan detail pages should show enough employee context to support approval decisions.

This should include:

- Existing active loans.
- Pending loan applications.
- Outstanding loan balances.
- Salary information.
- Grade and step.
- Department.
- Employment details relevant to the employee profile.
- Proposed loan amount.
- Repayment start month.
- Repayment amount.
- Repayment tenor.
- Repayment schedule preview.

The system should display this information for decision support only. It should not automatically reject applications based on salary, grade, step, length of service, or existing loans.

## Approval Route Visibility

Every loan detail page should clearly show:

- Approval route type:
  - Role-based HR then Finance.
  - Custom approval path.
- Approval route snapshot captured at submission.
- Current approval owner:
  - HR group.
  - Finance group.
  - Named custom approver.
- Completed approval steps.
- Pending approval step.
- Decision comments.

This avoids confusion when two loan products follow different approval routes.

## Agreed Decisions

- Employee Loans will have dedicated pages for Employee, HR, and Finance.
- HR will configure loan setups.
- HR will configure the approval route on each loan product.
- Loan products can use either the default role-based HR then Finance route or a custom approval path.
- Employees will apply from their own Loan page.
- The default approval route will be role-based.
- All Human Resource users can approve at HR stage when the role-based route is used.
- All Finance Officers can approve at Finance stage when the role-based route is used.
- Custom approval path loans will be approved by the specific employees configured in the selected custom approval path.
- Final custom approval activates the loan in the same way Finance final approval activates a role-based loan.
- Employees can choose both repayment tenor and repayment amount, with validation.
- Repayment starts from the month selected by the employee.
- After final approval, payroll deduction starts automatically from the selected repayment month.
- HR cannot override employee-selected repayment terms during approval.
- Finance cannot adjust the approved amount during final approval.
- Employees can apply for multiple loans.
- Existing active or unsettled loans should be shown clearly to HR and Finance before approval.
- Loan eligibility should not be automatically blocked by salary, grade, step, length of service, or active loans.
- Salary, grade, step, and related employee details should be visible on HR and Finance loan detail pages.
- Early repayment or manual repayment outside payroll is not required for this feature.
- Missed deductions should be flagged for HR/Finance review, not automatically rolled forward.
- Finance final approval is enough to activate a role-based loan; final custom approval is enough to activate a custom-path loan. The system should still record the activation/disbursement event automatically.
- Draft applications can be edited and deleted by the employee.
- Submitted applications cannot be edited directly by the employee unless returned for correction.
- HR and Finance cannot change employee-selected loan terms during approval.
- Custom approvers cannot change employee-selected loan terms during approval.
- Used loan products should be made inactive instead of deleted.
- Active, completed, rejected, cancelled, and payroll-linked loan records should be retained for history and audit.

## Closed Product Questions

- HR should not be allowed to override employee-selected repayment terms before approval.
- Finance should not be allowed to adjust the approved amount before final approval.
- Employees should be allowed to have multiple active loans.
- Existing loans should warn/inform HR and Finance, but should not block approval automatically.
- Loan eligibility should not be automatically determined by salary, grade, step, length of service, or existing loans.
- Early repayment or manual repayment outside payroll is not part of the planned scope.
- Missed deductions should be marked for HR/Finance review instead of rolling forward automatically.
- Finance final approval should activate role-based loans automatically, with an activation/disbursement event recorded by the system.
- Final custom approval should activate custom-path loans automatically, with an activation/disbursement event recorded by the system.
