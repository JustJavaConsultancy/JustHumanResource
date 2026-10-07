# Employee Loan Feature Gaps

## Purpose

This document records the gaps found while reviewing the Employee Loan feature from a non-technical testing point of view.

The focus is on what a user, HR officer, Finance officer, or tester may notice while using the system. It avoids technical implementation details and explains each issue in plain language.

## Review Summary

The Employee Loan feature already has many expected screens and flows:

- Employees can view loan products and apply for loans.
- HR can set up loan products and review loan applications.
- Finance can review finance-stage loan applications.
- Custom approval paths are supported.
- Approved loans can become active loans.
- Loan repayment deductions are connected to payroll.
- Users can upload supporting documents.

However, some gaps still need attention before the feature can be considered fully ready for business use.

## Identified Gaps

### 1. Finance May Be Able To View Loan Applications Too Early

**What I noticed**

Finance users are only supposed to see loan applications that are ready for Finance review or already approved/active. However, if a Finance user knows or guesses the direct link to a loan application, they may be able to open loan details that should not yet be visible to Finance.

**Why this matters**

Some loan applications may still be with HR, custom approvers, or may have been rejected/cancelled. Finance should not see applications outside its allowed stage unless the business has clearly approved that visibility.

**Expected behavior**

Finance should only be able to open loan applications that belong to Finance visibility, such as:

- Waiting for Finance approval.
- Finance approved.
- Custom approved and active for monitoring.
- Active, completed, or closed loans that affect payroll.

Finance should not be able to open applications that are still only with HR or custom approvers unless the business rule says otherwise.

**Priority**

High.

### 2. Delayed Approval Can Cause Repayment To Start In The Past

**What I noticed**

When an employee submits a loan, the system checks that the selected repayment start month is not in the past. But if the approval takes a long time, the loan may later be approved after that selected month has already passed.

For example:

- Employee applies in October 2026.
- Employee selects November 2026 as repayment start month.
- The loan is not finally approved until December 2026.
- The loan may still be activated with November 2026 as the first repayment month.

**Why this matters**

This can make the first repayment look missed immediately, even though the employee could not have been deducted before the loan was approved. It may also confuse HR, Finance, payroll users, and the employee.

**Expected behavior**

At final approval, the system should confirm whether the repayment start month is still valid.

If the selected start month has already passed, the system should either:

- Ask Finance/approver to choose a new valid start month.
- Automatically move the repayment start month to the next payroll month, if that is the approved business rule.
- Stop activation and ask for correction.

**Priority**

High.

### 3. Loan Product Document Requirement Can Change After The Product Has Been Used

**What I noticed**

Once a loan product has been used, some loan rules are locked so HR cannot change them. This is good because submitted applications should not be affected by later setup changes.

However, the supporting document requirement still appears changeable after the loan product has already been used.

**Why this matters**

This can create confusion. For example:

- An employee may start a loan application when documents are not required.
- HR later changes the product to require documents.
- The employee may then be blocked or asked for a document unexpectedly.

The reverse can also happen, where a product that required documents is later changed not to require them.

**Expected behavior**

The business should decide whether the document requirement is a fixed loan rule.

If it is a fixed rule, it should be locked after the product has been used, just like the loan amount, repayment, interest, and approval route rules.

**Priority**

Medium.

### 4. Loan Email Notifications Appear Incomplete

**What I noticed**

The loan feature appears to have planned email notifications, but the review did not confirm that the system actually sends emails during the loan journey.

Important moments where emails may be expected include:

- Employee submits a loan application.
- HR approval is pending.
- Finance approval is pending.
- Custom approver is assigned.
- Loan is approved.
- Loan is rejected.
- Loan is returned for correction.
- Loan is activated.
- Loan repayment is missed.
- Loan is fully paid.

**Why this matters**

Without notifications, users may not know that action is required. This can delay loan approvals, delay payroll deductions, and create manual follow-up work for HR and Finance.

**Expected behavior**

The system should send clear notifications at each important stage of the loan process, or the business should document that users must check the loan dashboard manually.

**Priority**

Medium.

### 5. Active Loan Count For Loan Products Is Not Fully Shown

**What I noticed**

The loan product area shows product usage information, but active loan count per product appears incomplete.

**Why this matters**

HR may need to know how many active loans are attached to each loan product before deciding whether to disable, update, or replace a product.

**Expected behavior**

Each loan product should clearly show:

- Number of applications.
- Number of active loans.
- Outstanding balance linked to that product, if available.

**Priority**

Low to Medium.

### 6. There Are No Loan-Specific Automated Tests

**What I noticed**

No dedicated automated tests were found for the loan feature.

**Why this matters**

The loan feature affects sensitive business areas:

- Employee money.
- Payroll deductions.
- Approval decisions.
- HR and Finance visibility.
- Supporting documents.
- Loan balances.

Without loan-specific tests, future changes may accidentally break the feature without being noticed early.

**Expected behavior**

The loan feature should have tests for important scenarios, such as:

- Employee creates and submits a loan.
- HR approves, rejects, or returns a loan.
- Finance approves, rejects, or returns a loan.
- Custom approval path works correctly.
- Loan activates after final approval.
- Payroll deducts the correct amount.
- Missed deductions are handled correctly.
- Users cannot view loans they should not access.
- Required attachments are enforced.

**Priority**

Medium.

### 7. Full Test Run Could Not Be Confirmed

**What I noticed**

The full project test run was attempted, but it did not finish within the available review time.

**Why this matters**

Because the full test run was not completed, there is no confirmed pass result for the whole application after the loan implementation.

**Expected behavior**

Before release, the full test suite should be run successfully, and any failed tests should be fixed or explained.

**Priority**

Medium.

## Suggested Business Test Scenarios

The following manual test scenarios are recommended before release:

1. Employee applies for a loan with a valid repayment start month.
2. Employee applies for a loan product that requires an attachment and tries to submit without uploading a file.
3. HR approves a role-based loan and confirms it moves to Finance.
4. HR rejects a loan and confirms the employee sees the correct status and comment.
5. HR returns a loan and confirms the employee can revise and resubmit.
6. Finance approves a loan and confirms the loan becomes active.
7. Finance rejects or returns a loan and confirms the employee sees the correct outcome.
8. A custom approval path loan moves from one approver to the next correctly.
9. A custom approver cannot approve their own loan.
10. Finance cannot open loan applications that are not meant for Finance.
11. A loan approved after the selected repayment start month is handled correctly.
12. Payroll deducts the correct monthly repayment amount.
13. Payroll does not make the employee net pay negative because of loan deductions.
14. Missed deductions are shown clearly to HR and Finance.
15. Completed loans show as fully repaid.

## Overall Assessment

The Employee Loan feature is well advanced and covers the main business journey. The biggest concerns are around access control, repayment start date handling after delayed approvals, notification completeness, and lack of loan-specific tests.

These gaps should be addressed or formally accepted by the business before production release.
