# Employee Exit Module User Guide

Last updated: 2026-09-03

## Purpose

The Employee Exit module manages the full offboarding lifecycle for an employee. It supports employee resignations, HR-initiated exits, approval routing, clearance, asset return, handover, final settlement, payroll posting, document handling, access revocation, finalization, and reporting.

Use this guide according to your role in the application.

## User Roles Covered

- Employees
- HR users and HR admins
- Exit approvers
- Department heads or line managers
- Asset and facilities managers
- IT and security/admin users
- Payroll and finance officers
- Report users

## Main Navigation

HR users:

```text
Main sidebar -> Employee Exits
```

This opens:

```text
/employee-exits
```

Employees:

```text
Employee sidebar -> My Exit
```

This opens:

```text
/employee/exit
```

Reports:

```text
/employee-exits/reports
```

Package settings:

```text
/employee-exits/settings/package-components
```

## Key Statuses

| Status | Meaning |
| --- | --- |
| `DRAFT` | Exit has been created but not submitted for approval. |
| `IN_APPROVAL` | Exit is waiting for one or more approvers. |
| `RETURNED` | Approver sent the exit back for revision. |
| `REJECTED` | Exit request was rejected. |
| `APPROVED` | Exit request was approved and can move into clearance. |
| `CLEARANCE_IN_PROGRESS` | Clearance workstreams are active. |
| `SETTLEMENT_IN_PROGRESS` | Final settlement calculation or approval is in progress. |
| `READY_TO_EXIT` | Operational checks are complete and the exit can proceed to finalization when due. |
| `COMPLETED` | Exit has been finalized. |
| `CANCELLED` | Exit was cancelled before completion. |

## Employee Guide

### Create A Resignation Draft

1. Go to:

   ```text
   My Exit
   ```

2. In the `New resignation` form, enter:
   - Reason
   - Notice date
   - Proposed last working date
   - Details, if needed

3. Select `Create draft`.

The system creates a draft resignation request. It is not sent for approval until you submit it.

### Submit A Draft

1. Open `My Exit`.
2. Select the draft from `Previous requests`.
3. Select `Submit for approval`.

After submission, the status changes to `IN_APPROVAL`.

### Track An Exit Request

Open:

```text
My Exit -> Previous requests -> Exit number
```

The detail page shows:

- Exit summary
- Current status
- Revision form, if the request was returned
- Readiness blockers
- Uploaded documents
- Clearance progress

### Revise A Returned Exit

If an approver returns your request, the detail page shows `Revise returned exit`.

Update the required fields:

- Reason
- Notice date
- Proposed last working date
- Contractual notice days
- Notice waived days
- Details

Then select:

```text
Save and return to approval
```

The same workflow continues. A new exit request is not created.

### Upload Exit Documents

From your exit detail page, use the `Documents` section.

1. Select the document type.
2. Choose the file.
3. Select `Upload document`.

Common employee documents include:

- `RESIGNATION_LETTER`
- `HANDOVER_NOTE`
- `OTHER`

For resignation exits, the resignation letter is required before the exit can become fully ready.

### Download Exit Documents

On your exit detail page, use the `Download` action beside any document visible to you.

You cannot view HR-only documents.

## HR User Guide

### View All Exit Cases

Go to:

```text
Employee Exits
```

The list page shows:

- All exit cases
- Number currently in clearance
- Exit number
- Employee ID
- Exit type
- Status
- Effective or proposed last working date

Select an exit number to open the detail page.

### Start An Exit For An Employee

1. Go to:

   ```text
   Employee Exits -> Start exit
   ```

2. Select the employee.
3. Select the exit type.
4. Enter:
   - Reason code
   - Notice date
   - Proposed last working date
   - Contractual notice days
   - Notice waived days
   - Details

5. Select `Create draft`.

6. Open the draft and select `Submit for approval`.

### Cancel An Exit

HR can cancel an exit before it reaches a terminal state.

Terminal states that cannot be cancelled:

- `COMPLETED`
- `REJECTED`
- `CANCELLED`

When cancelling, provide a reason so the audit trail remains clear.

### Handle A Returned Exit

If an exit is returned by an approver, the HR detail page shows a revision task when the active workflow task is `reviseExit`.

Update the fields and select:

```text
Save and return to approval
```

### Upload, Download, And Delete Exit Documents

On the HR exit detail page, use the `Documents` section.

HR can upload documents with these visibility options:

- `EMPLOYEE_AND_HR`
- `HR_ONLY`
- `FINANCE_AND_HR`

HR can also download and delete exit documents.

Common HR documents include:

- `TERMINATION_LETTER`
- `CLEARANCE_FORM`
- `FINAL_SETTLEMENT_LETTER`
- `SIGNED_SETTLEMENT`
- `OTHER`

### Review Readiness Blockers

The `Readiness` section shows whether the exit is ready or blocked.

Possible blockers include:

- Pending clearance
- Pending asset disposition
- Missing required document
- Settlement not approved
- Settlement not posted
- Effective date not reached
- Access revocation pending
- Workflow task pending

Use this section to identify what still needs action before finalization.

## Approver Guide

### Approve, Return, Or Reject An Exit

When an exit reaches you for approval, open the exit detail page from the HR exit area.

In the active task panel, you can:

- `Approve`
- `Return`
- `Reject`

Use the comments field to explain the decision.

Approval routing follows the configured approval route for `EXIT`. If no route is configured, the system falls back to HR approvers.

### What Each Decision Does

| Decision | Result |
| --- | --- |
| `Approve` | Moves to the next approver, or starts clearance if this is the final approval. |
| `Return` | Sends the request to `reviseExit` for correction. |
| `Reject` | Ends the exit request as rejected. |

## Department Head Or Line Manager Guide

### Complete Manager Handover Clearance

The manager handover clearance is tied to the `MANAGER_HANDOVER` clearance type.

Before this clearance can be completed:

- Handover items must be added where required.
- All handover items must be marked completed.

On the exit detail page:

1. Review the `Handover` section.
2. Add handover items if needed.
3. Mark each handover item complete.
4. Complete the manager clearance task.

Valid clearance outcomes:

- `CLEARED`
- `CLEARED_WITH_EXCEPTION`
- `WAIVED`

## Asset And Facilities Manager Guide

### Sync Assigned Assets

On the HR exit detail page, use:

```text
Company assets -> Sync assigned assets
```

The system snapshots assets currently assigned to the employee from the local assigned-asset source.

The sync is idempotent. Running it more than once does not duplicate the same external asset ID for the same exit case.

### Add A Missing Asset Manually

If an asset is not found during sync, use `Add assigned asset`.

Provide:

- External asset ID
- Asset name
- Asset code, if available
- Category, if available

### Record Asset Disposition

Each asset must have a terminal disposition before asset clearance can complete.

Supported dispositions:

- `RETURNED`
- `TRANSFERRED`
- `WRITTEN_OFF`
- `WAIVED`
- `RECOVER_FROM_SETTLEMENT`

If `RECOVER_FROM_SETTLEMENT` is selected, provide a recovery amount. The settlement calculation can include this as an asset recovery deduction.

Asset clearance cannot be completed while any assigned asset remains `PENDING`.

## IT And Security/Admin Guide

### Complete IT And Security Clearance

IT and security clearance covers system access, devices, accounts, and related offboarding checks.

On the active clearance task, choose one of:

- `CLEARED`
- `CLEARED_WITH_EXCEPTION`
- `WAIVED`

Add comments when there are exceptions or manual follow-up actions.

### Access Revocation

After readiness and effective-date handling, the workflow runs the access revocation step. The process records access revocation status as part of final completion.

## Payroll And Finance Guide

### Calculate A Manual Settlement

On the exit detail page, use `Final settlement`.

For manual settlement lines, provide:

- Line type
- Description
- Amount
- Whether the line is an earning

Manual adjustment lines require a reason.

### Calculate From Package Rules

If package rules have been configured:

1. Enter monthly basic pay.
2. Enter leave days, if applicable.
3. Select `Calculate from package rules`.

The system generates settlement lines from active package rules and includes applicable asset recovery deductions.

### Approve Settlement

After settlement calculation, review the latest version.

If the status is `IN_APPROVAL`, select:

```text
Approve
```

Approved settlements can be previewed and posted to payroll.

### Preview Payroll Impact

From the settlement section, select:

```text
Payroll preview
```

The preview shows:

- Gross earnings
- Total deductions
- Net settlement
- Payroll component lines

### Post Settlement To Payroll

On the payroll preview page:

1. Select the payroll period.
2. Select `Post to payroll`.

The system creates payroll line items and records a posting reference. Posting is idempotent, so the same settlement version and calculation fingerprint cannot be posted twice as a duplicate.

### Complete Payroll Clearance

Payroll clearance cannot be completed until the settlement is one of:

- `POSTED`
- `PAYMENT_PENDING`
- `PAID`

## Package Settings Guide

Users with HR or finance access can manage package components and rules.

Open:

```text
/employee-exits/settings/package-components
```

### Add A Package Component

Provide:

- Component code
- Name
- Line type
- Earning flag
- Taxable flag
- Pensionable flag

Examples:

- `EXIT_SEVERANCE`
- `EXIT_NOTICE_PAY`
- `EXIT_LEAVE_ENCASHMENT`
- `EXIT_ASSET_RECOVERY`

### Add A Package Rule

Provide:

- Component
- Calculation method
- Fixed amount or percentage, where applicable
- Applicable exit types
- Minimum years of service
- Maximum years of service
- Manual approval flag

Supported calculation methods:

- `FIXED_AMOUNT`
- `MANUAL`
- `DAYS_PRORATED`
- `MONTHS_OF_SERVICE`
- `PERCENT_OF_BASIC`
- `YEARS_OF_SERVICE_MULTIPLIER`
- `LEAVE_BALANCE`

Rules marked `MANUAL` are not auto-generated during package calculation.

## Reporting Guide

Open:

```text
/employee-exits/reports
```

The report page shows:

- Total exits
- Active exits
- Pending clearance count
- Approved settlement liability
- Asset recovery total
- Count by status
- Count by exit type

Available filters:

- Date from
- Date to
- Status
- Exit type

There is also a JSON summary endpoint:

```text
/api/employee-exits/reports/summary
```

## End-To-End Exit Flow

Typical flow:

1. Employee or HR creates an exit draft.
2. Employee or HR submits the draft.
3. The exit follows configured approval routing.
4. Approver approves, returns, or rejects.
5. Returned exits are revised and sent back through the same workflow.
6. Approved exits initialize clearance workstreams.
7. Assets are synced and disposed.
8. Handover items are completed.
9. HR/legal, IT/security, payroll/finance, asset, and manager clearance tasks are completed.
10. Settlement is calculated, approved, previewed, and posted to payroll.
11. Required documents are uploaded.
12. Readiness blockers are resolved.
13. The workflow waits for the effective exit date if necessary.
14. Employment is finalized.
15. Access is revoked.
16. Exit case is completed.

## Common Blocking Messages

| Blocker | What To Do |
| --- | --- |
| Pending clearance | Complete all clearance tasks or waive with a reason where allowed. |
| Pending asset disposition | Mark all assigned assets as returned, transferred, written off, waived, or recoverable from settlement. |
| Missing required document | Upload the resignation or termination letter, depending on exit type. |
| Settlement not approved | Finance should approve the calculated settlement. |
| Settlement not posted | Finance should post the approved settlement to payroll. |
| Effective date not reached | Wait until the effective exit date. |
| Access revocation pending | IT/admin should confirm access revocation through the workflow. |

## Important Rules

- An employee cannot have more than one active exit case.
- Last working date cannot be before notice date.
- Returned exits must be revised through the active `reviseExit` task.
- Submitting a returned exit as a new draft is not allowed.
- Asset clearance cannot complete while assets are pending.
- Manager clearance cannot complete while handover items are incomplete.
- Payroll clearance requires settlement posting unless the settlement has progressed to payment pending or paid.
- Required documents are checked by readiness validation.
- Completed, rejected, and cancelled exits are terminal for normal processing.

## Troubleshooting

### I Cannot See Employee Exits

You may not have HR, finance, asset manager, department head, or admin access.

### I Cannot Submit My Exit

Only draft exits can be submitted. If the exit was returned, open the detail page and use the revision form.

### I Cannot Complete Asset Clearance

Check the `Company assets` section. Every asset must have a terminal disposition.

### I Cannot Complete Payroll Clearance

Check the latest settlement. It must be posted, payment pending, or paid.

### The Exit Is Blocked Even After Clearance

Review the `Readiness` section. It lists the exact blockers that remain.

### I Cannot Download A Document

The document may be HR-only or finance-and-HR only. Contact HR if access is needed.
