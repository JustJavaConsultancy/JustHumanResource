# Promotion Bugfixes And UI Alignment Implementation Plan

## Issues / Findings / Solution Summary

1. Admin has no link to Promotion from `/admin/users`.
2. HR users land on `/departments`, but the Promotion sidebar link in `templates/layout.html` is commented out.
3. Employee users land on `/employee/dashboard`, but the Promotion sidebar link in `templates/layouts/employeeLayout.html` is commented out.
4. Finance users land on `/finance/dashboard`, but `templates/layouts/financialOfficerLayout.html` has no Promotion navigation item.
5. The current Promotion web UI is one catch-all page, `templates/promotion/main.html`, shared by all roles.
6. The current shared Promotion page hides panels after unauthorized API calls instead of serving role-owned pages.
7. The final role-owned URLs do not exist yet:

```text
/admin/promotions
/hr/promotions
/finance/promotions
/executive/promotions
/manager/promotions
/employee/promotions
```

8. Legacy `/promotion` and `/promotion/*` routes currently render the shared page instead of redirecting to role-appropriate pages.
9. `jobhr` and `restrictedhr` must be explicitly excluded from Promotion.
10. Department head must be treated as Manager for Promotion approval access.
11. Executive has no explicit landing route in `HomeController`; use `/executive/promotions` as the rational default.
12. `/employee/promotions` must replace the existing static employee promotions page with real Promotion data.
13. Promotion forms mostly use selects, but configuration still has raw ID/free-text inputs for escalation routing:

```text
escalationApproverId
escalationApproverGroup
```

14. Manager access is not currently represented in `PromotionSecurity.canAccessPromotionWorkArea()`.
15. Payroll handoff endpoints are currently guarded by broad operational access and should be tied to a payroll-handoff capability.
16. Finance and Executive views need role-owned pages that expose only their tasks and allowed data.
17. Employee scoped view needs a real Promotion page with own/requested records, timeline, and allowed documents/letters.
18. The `promotion-testing-guide.md` requires testing navigation, role separation, form quality, authorization, approval paths, payroll handoff, reports, configuration, documents, audit, and notifications. Current UI structure does not fully satisfy those manual testing expectations.

Solution summary:

- Keep all existing Promotion business behavior.
- Add role-owned Promotion routes and templates.
- Add visible navigation links for allowed roles.
- Replace the shared page-by-failed-API model with role-specific pages.
- Add a Promotion capability model that drives route access, link visibility, API authorization, and test expectations.
- Replace raw ID inputs with protected reference-data selects.
- Strengthen tests around click paths, role page security, HR/Admin parity, legacy redirects, form quality, and manual QA scenarios.

## Confirmed Business Decisions

| Question | Decision |
| --- | --- |
| Should `jobhr` and `restrictedhr` access Promotion? | No. Explicitly exclude both. |
| What should Executive use as landing page? | Use `/executive/promotions` as the reasonable default. |
| Should department head be treated as Manager? | Yes. Treat `departmenthead` as Manager for Promotion approval access. |
| Should `/employee/promotions` replace the static employee promotions page? | Yes. Replace with real Promotion module page/data. |

## Implementation Status - 2026-10-12

Concrete implementation completed in this pass:

- Added role-owned Promotion routes in `PromotionController`:
  - `/admin/promotions`
  - `/hr/promotions`
  - `/finance/promotions`
  - `/executive/promotions`
  - `/manager/promotions`
  - `/employee/promotions`
- Changed legacy `/promotion` routes to role-aware redirects.
- Added explicit Promotion capability methods in `PromotionSecurity`.
- Explicitly excluded `jobhr`, `restrictedhr`, `assetmanager`, and `auditor` from Promotion capabilities.
- Added `departmenthead` support as a manager-equivalent Promotion role.
- Added `AuthenticationManager` helpers for `departmenthead`, `assetmanager`, and `auditor`.
- Added Executive home redirect to `/executive/promotions`.
- Removed the old static `EmployeeController` mapping for `/employee/promotions`; the route is now owned by `PromotionController`.
- Added Admin `Promotions` button beside `Employee Exits` on `/admin/users`.
- Restored HR/Manager sidebar Promotion links in `templates/layout.html`.
- Restored Employee sidebar Promotion link in `templates/layouts/employeeLayout.html`.
- Added Finance sidebar Promotion link in `templates/layouts/financialOfficerLayout.html`.
- Added global template flags for HR and Manager Promotion navigation visibility.
- Added role context to `promotion/main.html` and role-gated the biggest UI panels/actions while the shared page is still in use.
- Tightened backend guards for:
  - configuration: `canConfigurePromotions()`
  - implementation: `canImplementPromotions()`
  - payroll handoffs: `canManagePromotionPayrollHandoffs()`
  - reference data: `canViewPromotionReferenceData()`
- Updated focused controller/security tests for role routes, redirects, excluded roles, department head access, capability annotations, protected reference endpoints, form quality, and browser workflow modal behavior.
- Added protected reference endpoints in `PromotionApiController`:
  - `/api/promotions/reference/approvers`
  - `/api/promotions/reference/escalation-approvers`
  - `/api/promotions/reference/approval-groups`
  - `/api/promotions/reference/document-types`
  - `/api/promotions/reference/statuses`
  - `/api/promotions/reference/departments`
  - `/api/promotions/reference/pay-groups`
- Added `PayGroupRepository` to Promotion reference-data support.
- Replaced remaining Promotion UI `alert()` API error paths with an inline page-level error banner.
- Confirmed no `prompt()` calls remain in `promotion/main.html`.
- Added `novalidate` to the Promotion action modal form so the custom inline modal validation is shown consistently instead of being intercepted by browser-native required validation.
- Added browser coverage for reject, return, cancel, payroll handoff cancel, reassign, delegate, required-comment validation, selected approver submission, and inline backend error rendering.
- Added browser coverage for document upload and delete controls, including the no-file inline validation path and the expected upload/delete API requests.
- Added legacy redirect coverage for Finance, Executive, Manager, Employee, and the no-authorized-role fallback.
- Split the role-owned Promotion templates so they compose only allowed Thymeleaf fragments instead of replacing the whole shared `promotion/main.html` content fragment.
- Kept styles/scripts reusable in `promotion/main.html`, but moved dashboard, request toolbar, reports, payroll handoffs, create/edit form, configuration, tasks, request list, detail, and action modal into independently included fragments.
- Removed request-table approval action rendering for Employee by adding an explicit approval-action role gate.
- Added template-composition browser coverage that checks forbidden fragments are absent from Finance, Executive, Manager, and Employee role pages.
- Added MVC security coverage for allowed/denied role-owned Promotion pages and fixed the test slice by mocking the JPA mapping context.
- Hardened Finance and Employee layouts so missing display-name model attributes do not break Promotion role-page rendering.

Verification run:

```text
mvnw.cmd "-Dtest=PromotionControllerTest,PromotionSecurityTest" test
```

Result: passed, 9 tests.

Additional verification after the reference-data and inline-error pass:

```text
mvnw.cmd "-Dtest=PromotionControllerTest,PromotionSecurityTest,PromotionApiControllerTest,PromotionMainPageBrowserTest" test
```

Result after reference-data and inline-error pass: passed, 23 tests.

Result after workflow browser coverage and legacy redirect coverage pass: passed, 26 tests.

Result after document upload/delete browser coverage pass: passed, 27 tests.

Result after role-template split and MVC security coverage pass: passed, 17 focused tests:

```text
mvnw.cmd "-Dtest=PromotionControllerTest,PromotionControllerSecurityTest,PromotionMainPageBrowserTest" test
```

```text
mvnw.cmd "-Dtest=PromotionBpmnDefinitionTest,PromotionControllerTest,PromotionMainPageBrowserTest,PromotionApiControllerTest,PromotionMapperTest,PromotionApprovalDecisionServiceTest,PromotionApprovalReassignmentServiceTest,PromotionApprovalReminderServiceTest,PromotionApprovalRouteServiceTest,PromotionAuthorizationServiceTest,PromotionConfigurationServiceTest,PromotionEligibilityServiceTest,PromotionEligibilityOverrideServiceTest,PromotionImplementationServiceTest,PromotionAttachmentServiceTest,PromotionDocumentIntegrationServiceTest,PromotionCancellationServiceTest,PromotionWorkflowCancellationServiceTest,PromotionPayrollHandoffServiceTest,PromotionSalaryImpactServiceTest,PromotionReportServiceTest,PromotionWorkflowRuntimeTest,PromotionWorkflowServiceAuthorizationTest,PromotionSubmissionServiceTest,PromotionNotificationServiceTest,PromotionEmailServiceTest,PromotionRequiredDocumentServiceTest,PromotionRequesterVisibilityServiceTest,PromotionSecurityTest,PromotionActivityServiceTest" test
```

Result after reference-data and inline-error pass: passed, 147 tests.

Result after workflow browser coverage and legacy redirect coverage pass: passed, 150 tests.

```text
mvnw.cmd test
```

Result: passed, 337 tests, 1 skipped. Maven still reports the existing local `~/.m2/settings.xml` warning: expected root element `settings` but found `servers`.

Important remaining work after the role-template split pass:

- The role-owned templates (`promotion/manage.html`, `promotion/finance.html`, `promotion/executive.html`, `promotion/manager.html`, `promotion/employee.html`) now include only the server-side fragments each role is allowed to render.
- `promotion/main.html` still keeps the shared fragment definitions and JavaScript helpers, but role pages no longer include the whole shared page fragment.
- Raw configuration inputs for `escalationApproverId` and `escalationApproverGroup` have been replaced with selects.
- Reassign/delegate now use the shared action modal with protected approver selects.
- Approval, rejection, return, cancellation, implementation, and payroll handoff comments now use a shared modal/form instead of browser `prompt()`.
- Promotion API/action errors now render in an inline page error banner instead of browser `alert()`.
- Employee/requester UI now has a physically limited template composition. It still needs a richer dedicated employee experience around own/requested records, status timeline/history, and allowed document/letter access only.
- Click-path browser tests and rendered-link negative tests are still pending.
- Promotion targeted Maven suite and full Maven suite have passed after the latest implementation pass, but they must be rerun after the remaining UI/template/test phases.

## Completion Plan To Fully Satisfy Definition Of Done

This is the implementation plan for finishing the remaining work against this file and `doc/promotion-testing-guide.md`. Execute in order; each phase should leave the app usable and the focused Promotion tests passing.

### Completion Phase A: Split Shared Promotion Page Into Role-Owned Templates

Goal: replace runtime hiding with server-selected role templates.

Implementation:

- [x] Create role-owned template shells:
  - `src/main/resources/templates/promotion/manage.html`
  - `src/main/resources/templates/promotion/finance.html`
  - `src/main/resources/templates/promotion/executive.html`
  - `src/main/resources/templates/promotion/manager.html`
  - `src/main/resources/templates/promotion/employee.html`
- [x] Split role-specific markup so unauthorized panels/actions are absent from each role template instead of included through the shared fragment.
- [x] Keep reusable JavaScript/helpers in a shared fragment while the role pages still share behavior.
- [x] Update `PromotionController` so:
  - `/admin/promotions` and `/hr/promotions` render `promotion/manage`.
  - `/finance/promotions` renders `promotion/finance`.
  - `/executive/promotions` renders `promotion/executive`.
  - `/manager/promotions` renders `promotion/manager`.
  - `/employee/promotions` renders `promotion/employee`.
- [x] Keep legacy `/promotion/*` redirects exactly as implemented.
- [x] Preserve `section` query state for templates that still have multiple sections.

Acceptance criteria:

- Admin/HR template contains dashboard, requests, approval tasks, reports, configuration, payroll handoffs, detail, create/edit, documents, letters, reassign/delegate, cancel, submit, and implement controls.
- Finance template contains finance-relevant tasks, detail, salary/reports where permitted, and payroll handoffs only.
- Executive template contains assigned executive approval tasks and detail/actions only.
- Manager template contains assigned manager approval tasks and detail/actions only.
- Employee template contains own/requested records, status timeline/history, and allowed document/letter links only.
- Unauthorized panels/actions are not present in role templates.

Tests:

- [x] Update `PromotionControllerTest` to assert each route returns its role template.
- [x] Add/extend browser tests that inspect role pages for required and forbidden panels/actions after templates are physically split.

### Completion Phase B: Replace Raw ID Inputs With Protected Selectors

Goal: remove visible raw ID entry from Promotion forms.

Implementation:

- [x] Add or confirm reference endpoints for:
  - escalation approvers,
  - approval groups,
  - reassignment/delegation approvers,
  - document types,
  - statuses where needed,
  - departments,
  - pay groups.
- [x] Protect reference endpoints with the narrowest matching capability currently available: `canViewPromotionReferenceData()`.
- [x] Replace `escalationApproverId` number input with an approver select/search control.
- [x] Replace `escalationApproverGroup` free text input with an approval-group select.
- [x] Replace reassign/delegate raw approver prompts with modal forms using protected approver selects.
- [x] Keep backend validation for submitted IDs/groups.

Acceptance criteria:

- [x] No visible raw `employeeId`, `jobStepId`, `approverId`, `escalationApproverId`, or equivalent operational ID text/number fields remain in `promotion/main.html`.
- [x] HR/Admin can configure escalation and perform reassignment/delegation without typing IDs.
- [x] Employee, manager, and executive users cannot access protected operational reference data through the reference capability.
- [x] Re-check this criterion against physically split role templates after Phase A is completed.

Tests:

- [x] Add a form-quality test that scans the current Promotion page for forbidden raw ID inputs.
- [x] Add reference endpoint protection/delegation tests.
- [x] Add reassignment/delegation browser tests that submit selected approvers.

### Completion Phase C: Replace Browser Prompts With Role-Appropriate Forms

Goal: make workflow actions testable and production-quality.

Implementation:

- [x] Add reusable action modal/form components for:
  - approve,
  - reject,
  - return,
  - cancel,
  - implement,
  - acknowledge payroll handoff,
  - apply payroll handoff,
  - cancel payroll handoff,
  - reassign,
  - delegate.
- [x] Enforce UI validation that mirrors backend requirements:
  - reject requires comment,
  - return requires comment,
  - optional comments remain optional.
- [x] Render API errors inline in a page-level error banner instead of browser `alert()`.

Acceptance criteria:

- [x] No `prompt()` calls remain in `promotion/main.html`.
- [x] Required comments cannot be submitted empty from the UI action modal.
- [x] Backend errors for missing documents, eligibility blockers, stale payroll handoffs, and unauthorized actions are visible on the page.

Tests:

- [x] Browser tests cover approve, reject, return, cancel, implement, payroll acknowledge/apply/cancel, reassign/delegate, modal submission, required-comment validation, no-prompt behavior, inline error rendering, and upload/delete document scenarios.

### Completion Phase D: Employee/Requester Experience Closure

Goal: satisfy scoped employee/requester expectations from `promotion-testing-guide.md`.

Implementation:

- Build `promotion/employee.html` around:
  - `/api/promotions/my/dashboard`,
  - `/api/promotions/my`,
  - `/api/promotions/{id}`,
  - allowed attachment download endpoints.
- Display own/requested promotion records, current status, status timeline/history, safe activity, and allowed documents/letters.
- Hide all operational controls: create, edit, submit, approve, implement, reports, configuration, and payroll handoffs.
- Confirm direct unrelated detail links remain blocked by backend authorization.

Acceptance criteria:

- Employee can complete guide section 19.2 without operational access.
- Employee/requester sees only own/requested records.
- Salary fields remain masked according to backend `canViewSalary` behavior.

Tests:

- Add employee scoped page browser test.
- Add direct-link unrelated detail denied test if not already covered by service/API tests.

### Completion Phase E: Security And Link Coverage Expansion

Goal: prove route/link/API behavior matches the capability model.

Implementation:

- Add MVC/security tests for role-owned page access:
  - Employee cannot load Admin/HR/Finance/Executive/Manager pages.
  - Finance cannot load Admin/HR/Manager/Executive pages.
  - Executive cannot load Admin/HR/Finance/Manager pages.
  - Manager cannot load Admin/HR/Finance/Executive pages.
  - JobHR, RestrictedHR, Asset Manager, and Auditor cannot load any Promotion page.
- Add rendered-link tests for:
  - Admin, HR, Finance, Manager, Employee positive click paths.
  - JobHR and RestrictedHR negative link visibility.
- Add API authorization tests for configuration, implement, payroll handoff, reports/CSV, reference data, and reassignment/delegation.

Acceptance criteria:

- Every capability in the capability model has at least one positive and one negative automated test where meaningful.
- Excluded roles fail both link visibility and route/API access checks.

Tests:

- Add focused MVC tests, preferably with mocked `AuthenticationManager` role states.
- Extend browser click-path tests for landing page to Promotion page navigation.

### Completion Phase F: Reports, CSV, Activity, And Audit Role Tuning

Goal: close guide sections 17, 19, and 20.

Implementation:

- Ensure Admin/HR report pages show all report panels and CSV exports.
- Ensure Finance sees only permitted finance/payroll-relevant report panels and CSV exports.
- Ensure Executive/Manager/Employee templates do not show operational report panels.
- Ensure report filters cover:
  - `startDate`,
  - `endDate`,
  - `status`,
  - `departmentId`,
  - `approvalStartDate`,
  - `approvalEndDate`,
  - `page`,
  - `size`.
- Show full audit/activity metadata only to Admin/HR if sensitive.
- Show safe timeline/history to Employee/requester.

Acceptance criteria:

- CSV links match backend capability rules.
- Activity timeline is visible in detail views.
- Employee timeline does not expose sensitive operational metadata.

Tests:

- Add CSV authorization tests.
- Add browser tests for Admin/HR/Finance report panels and Employee-safe activity rendering.

### Completion Phase G: Final Regression And Manual QA Pass

Goal: prove the Definition of Done.

Required automated commands:

```text
mvnw.cmd "-Dtest=PromotionBpmnDefinitionTest,PromotionControllerTest,PromotionMainPageBrowserTest,PromotionApiControllerTest,PromotionMapperTest,PromotionApprovalDecisionServiceTest,PromotionApprovalReassignmentServiceTest,PromotionApprovalReminderServiceTest,PromotionApprovalRouteServiceTest,PromotionAuthorizationServiceTest,PromotionConfigurationServiceTest,PromotionEligibilityServiceTest,PromotionEligibilityOverrideServiceTest,PromotionImplementationServiceTest,PromotionAttachmentServiceTest,PromotionDocumentIntegrationServiceTest,PromotionCancellationServiceTest,PromotionWorkflowCancellationServiceTest,PromotionPayrollHandoffServiceTest,PromotionSalaryImpactServiceTest,PromotionReportServiceTest,PromotionWorkflowRuntimeTest,PromotionWorkflowServiceAuthorizationTest,PromotionSubmissionServiceTest,PromotionNotificationServiceTest,PromotionEmailServiceTest,PromotionRequiredDocumentServiceTest,PromotionRequesterVisibilityServiceTest,PromotionSecurityTest,PromotionActivityServiceTest" test
```

```text
mvnw.cmd test
```

Manual QA:

- Execute the Manual QA Matrix in this file.
- Execute the applicable walkthroughs in `doc/promotion-testing-guide.md`.
- Record any environment-only items, such as actual email inbox delivery, as out of scope or externally verified.

## Current Files To Read Before Implementation

Always read these before changing Promotion UI/security:

```text
promotion ui.txt
doc/promotion-plan.md
doc/promotion-testing-guide.md
src/main/java/com/justjava/humanresource/HomeController.java
src/main/java/com/justjava/humanresource/core/config/AuthenticationManager.java
src/main/java/com/justjava/humanresource/core/config/GlobalControllerAdvice.java
src/main/java/com/justjava/humanresource/promotion/PromotionController.java
src/main/java/com/justjava/humanresource/promotion/PromotionApiController.java
src/main/java/com/justjava/humanresource/promotion/service/PromotionSecurity.java
src/main/java/com/justjava/humanresource/promotion/service/PromotionAuthorizationService.java
src/main/resources/templates/promotion/main.html
src/main/resources/templates/layout.html
src/main/resources/templates/layouts/employeeLayout.html
src/main/resources/templates/layouts/financialOfficerLayout.html
src/main/resources/templates/admin/userManagement.html
src/main/resources/templates/employees/promotions.html
```

Read these when working on role landing pages:

```text
src/main/java/com/justjava/humanresource/admin/AdminController.java
src/main/java/com/justjava/humanresource/finance/FinanceController.java
src/main/java/com/justjava/humanresource/employee/EmployeeController.java
src/main/java/com/justjava/humanresource/departments/DepartmentsController.java
src/main/java/com/justjava/humanresource/recruitment/RecruitmentController.java
src/main/resources/templates/departments/main.html
src/main/resources/templates/finance/dashboard.html
src/main/resources/templates/recruitment/dashboard.html
```

## Target Role Navigation

| Role | Landing Page | Click Path To Promotion | Final Promotion URL |
| --- | --- | --- | --- |
| Admin | `/admin/users` | Login -> `/admin/users` -> `Promotions` button beside `Employee Exits` in the page header | `/admin/promotions` |
| HR | `/departments` | Login -> `/departments` -> sidebar `Promotions` link in `templates/layout.html` | `/hr/promotions` |
| Finance | `/finance/dashboard` | Login -> `/finance/dashboard` -> sidebar `Promotions` link in `templates/layouts/financialOfficerLayout.html` | `/finance/promotions` |
| Executive | `/executive/promotions` | Login -> `/executive/promotions` directly, with Promotion page as default executive landing | `/executive/promotions` |
| Manager: hiring manager | `/recruitment` | Login -> `/recruitment` -> sidebar `Promotions` link in `templates/layout.html` | `/manager/promotions` |
| Manager: department head | current broad layout page, normally `/departments` when routed by role | Login -> existing landing page -> sidebar `Promotions` link | `/manager/promotions` |
| Employee | `/employee/dashboard` | Login -> `/employee/dashboard` -> sidebar `Promotions` link in `templates/layouts/employeeLayout.html` | `/employee/promotions` |
| Job HR | `/departments` | Explicitly excluded | No route/link |
| Restricted HR | `/departments` | Explicitly excluded | No route/link |
| Asset Manager | module-specific pages | Explicitly excluded | No route/link |
| Auditor | not currently in Promotion | Explicitly excluded | No route/link |

## Target Page Ownership

| Page | Allowed Role(s) | Must Include | Must Not Include |
| --- | --- | --- | --- |
| `/admin/promotions` | Admin | Full HR/Admin operational Promotion workspace: dashboard, request list, create/edit draft, eligibility, documents, submit/cancel, route preview, HR approval tasks, reassign/delegate, implementation, configuration, reports, CSV exports. | Non-Promotion admin user management. |
| `/hr/promotions` | HumanResource only, not JobHR/RestrictedHR | Same Promotion powers as Admin. | Admin shell dependency, JobHR/RestrictedHR access. |
| `/finance/promotions` | Financial officers | Finance approval tasks, salary-impact view, payroll handoffs, allowed reports/exports. | Create/update/submit/configuration/implementation/reassign/delegate. |
| `/executive/promotions` | Executive | Executive approval queue, detail view with salary visibility, approve/reject/return. | Configuration, payroll handoffs, create/update/submit. |
| `/manager/promotions` | Hiring manager and department head | Assigned line-manager tasks, detail view, approve/reject/return. | HR panels, Finance panels, reports, configuration, payroll handoffs, salary unless backend permits. |
| `/employee/promotions` | Employees | Own/requested records, status timeline, approved/implemented history, allowed documents/letters. | Create, update, submit, approve, implement, configuration, reports, payroll handoffs. |

## Capability Model

Implement Promotion capabilities in one place and reuse them for:

- link visibility,
- page-route authorization,
- API authorization,
- service-level guards where applicable,
- test expectations.

| Capability | Meaning |
| --- | --- |
| `PROMOTION_ACCESS_ADMIN_PAGE` | Load `/admin/promotions`. |
| `PROMOTION_ACCESS_HR_PAGE` | Load `/hr/promotions`. |
| `PROMOTION_ACCESS_FINANCE_PAGE` | Load `/finance/promotions`. |
| `PROMOTION_ACCESS_EXECUTIVE_PAGE` | Load `/executive/promotions`. |
| `PROMOTION_ACCESS_MANAGER_PAGE` | Load `/manager/promotions`. |
| `PROMOTION_ACCESS_EMPLOYEE_PAGE` | Load `/employee/promotions`. |
| `PROMOTION_MANAGE` | Create/update draft, submit, cancel, upload/remove docs, generate letters. |
| `PROMOTION_CONFIGURE` | Read/update configuration, history, routing, documents, reminders, escalation. |
| `PROMOTION_IMPLEMENT` | Implement approved promotions. |
| `PROMOTION_APPROVE_HR` | Complete HR approval stage. |
| `PROMOTION_APPROVE_FINANCE` | Complete Finance approval stage. |
| `PROMOTION_APPROVE_EXECUTIVE` | Complete Executive approval stage. |
| `PROMOTION_APPROVE_MANAGER` | Complete assigned line-manager approval stage. |
| `PROMOTION_VIEW_OPERATIONAL` | View operational dashboard/list/reports. |
| `PROMOTION_VIEW_OWN` | View own/requested promotion records. |
| `PROMOTION_VIEW_SALARY` | View salary/payroll fields. |
| `PROMOTION_PAYROLL_HANDOFF` | List, acknowledge, apply, and cancel payroll handoffs. |
| `PROMOTION_REFERENCE_DATA` | Read protected employee/job/approver/group reference data. |

Role-to-capability mapping:

| Role | Capabilities |
| --- | --- |
| Admin | All capabilities. |
| HumanResource | Same as Admin. |
| Financial Officer | Finance page, finance approval, operational view, salary view, payroll handoff, permitted report exports, reference data needed for Finance UI. |
| Executive | Executive page, executive approval, salary view, assigned detail visibility. |
| Hiring Manager | Manager page, assigned manager approval, assigned detail visibility. |
| Department Head | Manager page, assigned manager approval, assigned detail visibility. |
| Employee | Employee page, own/requested visibility, allowed documents/letters. |
| JobHR | No Promotion capability. |
| RestrictedHR | No Promotion capability. |
| Asset Manager | No Promotion capability. |
| Auditor | No Promotion capability. |

## Implementation Phases

Each phase is intentionally small enough for one focused implementation session and leaves the app working.

### Phase 1: Quick Navigation Win

Goal: let users reach Promotion by clicking before deeper UI splitting begins.

Files to read first:

```text
src/main/java/com/justjava/humanresource/HomeController.java
src/main/java/com/justjava/humanresource/promotion/PromotionController.java
src/main/resources/templates/admin/userManagement.html
src/main/resources/templates/layout.html
src/main/resources/templates/layouts/employeeLayout.html
src/main/resources/templates/layouts/financialOfficerLayout.html
```

Implementation:

- Add final role-owned page routes in `PromotionController`:

```text
GET /admin/promotions
GET /hr/promotions
GET /finance/promotions
GET /executive/promotions
GET /manager/promotions
GET /employee/promotions
```

- Initially these can render the existing `promotion/main` with a role context model attribute, so the app remains usable.
- Add Admin `Promotions` button to `templates/admin/userManagement.html` beside `Employee Exits`.
- Replace the commented-out Promotion link in `templates/layout.html` with role-conditional links:
  - HR -> `/hr/promotions`
  - Manager -> `/manager/promotions`
  - Do not show for `jobhr` or `restrictedhr`.
- Replace the commented-out Promotion link in `templates/layouts/employeeLayout.html` with `/employee/promotions`.
- Add Finance `Promotions` link in `templates/layouts/financialOfficerLayout.html`.
- Add `HomeController` executive branch:

```text
executive -> redirect:/executive/promotions
```

Verification checklist:

- [x] Admin has a `Promotions` link from `/admin/users`.
- [x] HR has a sidebar `Promotions` link to `/hr/promotions`.
- [x] Finance has a sidebar `Promotions` link to `/finance/promotions`.
- [x] Executive login redirects to `/executive/promotions`.
- [x] Hiring manager has a sidebar `Promotions` link to `/manager/promotions`.
- [x] Department head is covered by the manager Promotion sidebar flag.
- [x] Employee has a sidebar `Promotions` link to `/employee/promotions`.
- [x] JobHR and RestrictedHR do not receive Promotion capability-driven links.

Automated tests:

- [ ] Add browser click-path acceptance tests for Admin, HR, Finance, Executive, Manager, and Employee.
- [ ] Add negative rendered-link visibility tests for JobHR and RestrictedHR.
- [x] Added controller/unit coverage for role-owned route rendering context and legacy redirects.

### Phase 2: Capability-Based Authorization

Goal: make backend authorization match the role-specific UI.

Files to read first:

```text
src/main/java/com/justjava/humanresource/core/config/AuthenticationManager.java
src/main/java/com/justjava/humanresource/promotion/service/PromotionSecurity.java
src/main/java/com/justjava/humanresource/promotion/service/PromotionAuthorizationService.java
src/main/java/com/justjava/humanresource/promotion/PromotionApiController.java
src/test/java/com/justjava/humanresource/promotion/PromotionSecurityTest.java
src/test/java/com/justjava/humanresource/promotion/PromotionAuthorizationServiceTest.java
```

Implementation:

- Add group checks or normalized group helpers for:
  - `departmenthead`
  - optionally `auditor` and `assetmanager` only for explicit exclusion tests.
- Do not grant Promotion access to `jobhr` or `restrictedhr`.
- Expand `PromotionSecurity` into capability-style methods.
- Keep `PromotionAuthorizationService` for request-specific authorization:
  - detail visibility,
  - salary masking,
  - assigned approver checks,
  - self-approval prevention.
- Update page route `@PreAuthorize` checks:
  - `/admin/promotions` -> Admin capability.
  - `/hr/promotions` -> HumanResource capability.
  - `/finance/promotions` -> Finance capability.
  - `/executive/promotions` -> Executive capability.
  - `/manager/promotions` -> Manager capability.
  - `/employee/promotions` -> Employee own-view capability.
- Update API guards:
  - manage endpoints -> `PROMOTION_MANAGE`.
  - configuration endpoints -> `PROMOTION_CONFIGURE`.
  - implement endpoint -> `PROMOTION_IMPLEMENT`.
  - payroll handoff endpoints -> `PROMOTION_PAYROLL_HANDOFF`.
  - operational reports/list/dashboard -> `PROMOTION_VIEW_OPERATIONAL`.
  - salary reports/CSV -> `PROMOTION_VIEW_SALARY`.
  - reference endpoints -> protected reference-data capability.
- Preserve service-level checks so direct API calls remain protected even if UI hides controls.

Verification checklist:

- [x] Role-owned page routes have capability-specific `@PreAuthorize` guards.
- [x] Finance payroll handoff endpoints use a payroll-handoff capability.
- [x] Configuration endpoints use a configuration capability.
- [x] Implementation endpoint uses an implementation capability.
- [x] Reference endpoints use a reference-data capability.
- [x] JobHR and RestrictedHR are explicitly excluded from Promotion capability checks.
- [ ] Add MVC/security tests proving Employee cannot load `/hr/promotions`, `/finance/promotions`, `/admin/promotions`, `/manager/promotions`, or `/executive/promotions`.
- [ ] Add MVC/security tests proving Finance cannot load HR/Admin/configuration pages.
- [ ] Add MVC/security tests proving Executive cannot load HR/Admin/Finance/Manager pages.
- [ ] Add MVC/security tests proving Manager cannot load HR/Admin/Finance/Executive pages.
- [ ] Add MVC/security tests proving JobHR and RestrictedHR cannot load any Promotion page.
- [ ] Add HR-equals-Admin end-to-end action tests.

Automated tests:

- [x] Unit tests for excluded role capability behavior.
- [x] Unit tests for department-head manager capability behavior.
- [x] Annotation tests for page routes and core API/service capability guards.
- [ ] Page route security tests by role.
- [ ] API security tests by capability.
- [ ] HR-equals-Admin tests for manage/configure/implement/reassign/delegate/upload/generate-letter/report access.
- [ ] Exclusion tests for JobHR and RestrictedHR using MVC/security execution, not only unit capability checks.

### Phase 3: Legacy Route Redirects

Goal: preserve old bookmarks while forcing users into role-owned pages.

Files to read first:

```text
src/main/java/com/justjava/humanresource/promotion/PromotionController.java
src/test/java/com/justjava/humanresource/promotion/PromotionControllerTest.java
```

Implementation:

- Change legacy routes to redirect:

```text
/promotion
/promotion/requests
/promotion/approvals
/promotion/reports
/promotion/configuration
/promotion/payroll-handoffs
```

- Role-aware redirect targets:

| Current user | Legacy redirect target |
| --- | --- |
| Admin | `/admin/promotions` |
| HumanResource | `/hr/promotions` |
| Finance | `/finance/promotions` |
| Executive | `/executive/promotions` |
| Hiring manager or department head | `/manager/promotions` |
| Employee | `/employee/promotions` |
| JobHR/RestrictedHR/other excluded roles | access denied or safe non-Promotion landing page |

Verification checklist:

- [x] Old `/promotion` routes call role-aware redirect logic.
- [x] Unit coverage verifies HR redirect and Admin priority.
- [x] Add redirect tests for Finance, Executive, Manager, Employee.
- [ ] Add legacy route denial/security tests for excluded users.

Automated tests:

- [x] Basic legacy redirect tests.
- [x] Legacy redirect test per allowed role.
- [ ] Legacy route denial test for JobHR and RestrictedHR.

### Phase 4: Split The Shared Promotion UI Into Role-Owned Pages

Goal: stop relying on hidden panels after failed API calls.

Files to read first:

```text
src/main/resources/templates/promotion/main.html
src/test/java/com/justjava/humanresource/promotion/PromotionMainPageBrowserTest.java
doc/promotion-testing-guide.md
```

Implementation:

- Extract shared reusable pieces where practical:
  - status badge rendering,
  - request table row rendering,
  - detail rendering,
  - task actions,
  - attachment controls,
  - utility functions.
- Create role-owned templates:

```text
src/main/resources/templates/promotion/manage.html
src/main/resources/templates/promotion/finance.html
src/main/resources/templates/promotion/executive.html
src/main/resources/templates/promotion/manager.html
src/main/resources/templates/promotion/employee.html
```

- Use `manage.html` for both `/admin/promotions` and `/hr/promotions`.
- Remove unauthorized panels from each role page instead of rendering and hiding them.
- Keep current business calls intact.

Page content:

| Template | Contents |
| --- | --- |
| `promotion/manage.html` | dashboard, requests, new/edit form, detail, eligibility, required docs, approval route preview, HR tasks, reassign/delegate, implementation, configuration, reports, payroll handoff link or panel where Admin/HR allowed. |
| `promotion/finance.html` | finance task queue, detail, salary impact, payroll handoffs, permitted reports/CSV. |
| `promotion/executive.html` | executive tasks, detail with salary, approve/reject/return. |
| `promotion/manager.html` | assigned manager tasks, detail, approve/reject/return. |
| `promotion/employee.html` | own/requested records, status timeline, approved/implemented history, allowed docs/letters. |

Verification checklist:

- [ ] Admin/HR page has no missing former functionality.
- [ ] Finance page has no configuration/new-request/implementation controls.
- [ ] Executive page has no configuration/payroll/new-request controls.
- [ ] Manager page has no HR/Finance/report/configuration controls.
- [ ] Employee page has no create/approve/implement/report/handoff controls.

Automated tests:

- Browser tests per role page.
- Role page panel visibility tests.
- Regression coverage for existing `PromotionMainPageBrowserTest` behaviors moved to role-owned pages.

### Phase 5: Replace Employee Static Promotions Page

Goal: make `/employee/promotions` use real Promotion module data.

Files to read first:

```text
src/main/java/com/justjava/humanresource/employee/EmployeeController.java
src/main/resources/templates/employees/promotions.html
src/main/resources/templates/layouts/employeeLayout.html
src/main/resources/templates/promotion/employee.html
```

Implementation:

- Stop rendering `templates/employees/promotions.html` for `/employee/promotions`.
- Route `/employee/promotions` through `PromotionController`.
- Render `templates/promotion/employee.html`.
- Keep the old static template only if needed for reference, or remove it in a separate cleanup after tests pass.
- Employee page calls:

```text
GET /api/promotions/my/dashboard
GET /api/promotions/my
GET /api/promotions/{id}
GET /api/promotions/{id}/attachments
GET /api/promotions/{id}/attachments/{attachmentId}
```

- Employee page must rely on backend detail authorization and salary masking.

Verification checklist:

- [ ] Employee sees own/requested Promotion records only.
- [ ] Employee sees status timeline/history.
- [ ] Employee can view/download allowed letters/documents.
- [ ] Employee cannot create, edit, submit, approve, implement, configure, report, or manage handoffs.

Automated tests:

- Employee scoped page browser test.
- Direct-link unrelated detail access denied test.

### Phase 6: Form Quality And Reference Data

Goal: remove raw ID inputs and protect reference data.

Files to read first:

```text
src/main/resources/templates/promotion/main.html
src/main/java/com/justjava/humanresource/promotion/PromotionApiController.java
src/main/java/com/justjava/humanresource/promotion/dto/PromotionConfigurationCommand.java
src/main/java/com/justjava/humanresource/promotion/dto/PromotionConfigurationResponse.java
src/main/java/com/justjava/humanresource/promotion/dto/PromotionReassignmentCommand.java
```

Current acceptable form controls:

- `employeeId` is already a select.
- `proposedRoleId` is already a select.
- `effectiveDate` is already a date picker.
- `proposedSalary` is numeric and should remain HR/Admin only.
- required document lists are multi-selects.

Fix these:

| Current field | Current control | Target control |
| --- | --- | --- |
| `escalationApproverId` | fixed: protected employee select | keep select and improve search UX if needed |
| `escalationApproverGroup` | fixed: approval-group select | keep protected group select |
| reassign/delegate approver prompt or raw ID entry if present | fixed: action modal employee select | add browser submit coverage |
| report status filters where free text appears | fixed: status dropdown | keep status dropdown |
| document types | select already exists | keep, but ensure generated/upload controls are role-gated |

Reference endpoints to add or confirm:

```text
GET /api/promotions/reference/employees
GET /api/promotions/reference/job-steps
GET /api/promotions/reference/approvers
GET /api/promotions/reference/escalation-approvers
GET /api/promotions/reference/approval-groups
GET /api/promotions/reference/document-types
GET /api/promotions/reference/statuses
GET /api/promotions/reference/departments
GET /api/promotions/reference/pay-groups
```

Security requirements:

- [x] Employee users must not access operational employee/job/salary reference data.
- [x] Manager/Executive should only receive reference data needed for their page, usually none beyond task detail.
- [ ] Finance currently has the shared reference-data capability; tighten this later if finance pages no longer need unrestricted employee/job/salary reference data.
- [x] Backend must validate all submitted IDs even if frontend uses dropdowns.

Verification checklist:

- [x] No visible raw `employeeId`, `jobStepId`, `approverId`, `escalationApproverId`, or similar ID text/number inputs in the current shared Promotion page.
- [x] Employee users cannot call protected reference endpoints.
- [x] HR/Admin can use dropdowns to create and configure requests.
- [x] Reassign/delegate uses employee selector, not ID typing.
- [x] Re-run this checklist after physically split role templates replace the shared fragment.

Automated tests:

- [x] Form quality test scans the current shared Promotion page for forbidden raw ID inputs.
- [x] Reference endpoint protection tests.
- [ ] Backend ID validation tests for bypassed frontend submissions.

### Phase 7: Role-Specific Workflow Polish

Goal: satisfy the manual end-to-end testing guide role by role.

Files to read first:

```text
doc/promotion-testing-guide.md
src/main/resources/templates/promotion/*.html
src/main/java/com/justjava/humanresource/promotion/service/PromotionWorkflowServiceImpl.java
src/main/java/com/justjava/humanresource/promotion/service/PromotionApprovalDecisionService.java
```

Implementation:

- [x] Replace `prompt()` based approval, rejection, return, implementation, and payroll comments with a shared modal form.
- [x] Ensure comment requirements match backend requirements:
  - reject requires comment,
  - return requires comment,
  - approve may allow optional comment,
  - payroll handoff actions allow optional/required comment according to service.
- Add clear empty states:
  - no assigned tasks,
  - no own records,
  - no payroll handoffs,
  - no report data.
- [x] Add clear page-level error display for:
  - unauthorized action,
  - missing documents,
  - eligibility blocker,
  - payroll handoff stale salary/open period failure.
- Keep all UI behavior backed by existing API/service responses.

Verification checklist:

- [x] Approve/reject/return are usable without browser prompts.
- [x] Validation messages are visible and actionable; the action modal uses `novalidate` so custom inline validation displays reliably.
- [ ] Empty states are role-appropriate.
- [x] Missing document errors appear before submit/implement when returned by the backend.
- [x] Payroll handoff errors are visible to Finance when returned by the backend.

Automated tests:

- [x] Browser tests for reject, return, cancellation, payroll cancel, selected-approver reassign/delegate, payroll/action error display, upload document, delete document, and no-file upload validation.
- [x] Existing browser tests cover no-prompt behavior, approval modal submit, implement modal submit, and payroll acknowledge/apply/cancel modal submit.

### Phase 8: Reports And CSV Role Alignment

Goal: align report visibility with role pages.

Files to read first:

```text
src/main/java/com/justjava/humanresource/promotion/PromotionApiController.java
src/main/java/com/justjava/humanresource/promotion/service/PromotionReportService.java
src/main/resources/templates/promotion/manage.html
src/main/resources/templates/promotion/finance.html
```

Implementation:

- Admin/HR:
  - all report panels and CSV exports.
- Finance:
  - salary-impact and payroll-relevant reports/exports only where permitted.
- Executive/Manager/Employee:
  - no operational report panels unless business later requests.
- Ensure CSV endpoints use the same capability rules as page links.
- Add date range filters to role pages where reports remain visible.

Verification checklist:

- [ ] Finance cannot access configuration report controls.
- [ ] Employee cannot access report endpoints.
- [ ] CSV exports honor capability and filters.

Automated tests:

- Report endpoint authorization tests.
- CSV authorization tests.
- Browser tests for report panels on Admin/HR/Finance.

### Phase 9: Audit, Activity, Notification Visibility

Goal: make audit expectations from the testing guide verifiable in UI.

Files to read first:

```text
src/main/java/com/justjava/humanresource/promotion/service/PromotionActivityService.java
src/main/java/com/justjava/humanresource/promotion/service/PromotionMapper.java
src/main/resources/templates/promotion/*.html
src/test/java/com/justjava/humanresource/promotion/PromotionActivityServiceTest.java
```

Implementation:

- Ensure detail pages show activity timeline for authorized roles.
- Employee page can show safe status/history activity without exposing internal-only operational details if any become sensitive.
- Show request metadata only to Admin/HR if considered sensitive:
  - IP address,
  - user agent,
  - device metadata.
- Keep notification activity visible where useful:
  - assignment,
  - reminder,
  - escalation,
  - final approval/rejection/return/cancellation/implementation.

Verification checklist:

- [ ] Activity log appears in detail.
- [ ] Activity is chronological.
- [ ] Request metadata appears for Admin/HR.
- [ ] Employee sees only safe timeline/history.

Automated tests:

- Mapper tests for role-safe activity fields.
- Browser test for activity timeline rendering.

### Phase 10: Test Suite Expansion

Goal: encode the testing-guide expectations so regressions are caught.

Files to read first:

```text
src/test/java/com/justjava/humanresource/promotion/*
src/test/java/com/justjava/humanresource/promotion/PromotionMainPageBrowserTest.java
doc/promotion-testing-guide.md
```

Required tests:

1. Click-path acceptance tests:
   - Admin: login landing `/admin/users` -> Promotion button -> `/admin/promotions`.
   - HR: `/departments` -> sidebar -> `/hr/promotions`.
   - Finance: `/finance/dashboard` -> sidebar -> `/finance/promotions`.
   - Executive: login -> `/executive/promotions`.
   - Manager: `/recruitment` or manager landing -> sidebar -> `/manager/promotions`.
   - Employee: `/employee/dashboard` -> sidebar -> `/employee/promotions`.

2. Page-route security tests:
   - Employee cannot load Admin/HR/Finance/Executive/Manager pages.
   - Finance cannot load Admin/HR/Manager/Executive pages.
   - Executive cannot load Admin/HR/Finance/Manager pages.
   - Manager cannot load Admin/HR/Finance/Executive pages.
   - JobHR and RestrictedHR cannot load any Promotion page.

3. HR-equals-Admin tests:
   - create/update/submit/cancel,
   - upload/delete/generate letters,
   - configuration,
   - reassign/delegate,
   - implement,
   - reports/exports.

4. Legacy redirect tests:
   - `/promotion` and `/promotion/*` redirect by role.
   - excluded roles are blocked.

5. Form quality tests:
   - no visible raw ID text/number inputs for employee, job step, approver, escalation approver.
   - date picker exists for effective date.
   - salary numeric input appears only on Admin/HR manage page.

6. Role page browser tests:
   - Admin/HR manage page.
   - Finance page.
   - Executive page.
   - Manager page.
   - Employee page.

7. Workflow action tests:
   - approve,
   - reject,
   - return,
   - cancel,
   - implement,
   - upload/delete document,
   - payroll handoff acknowledge/apply/cancel.

Expected command after completion:

```text
mvnw.cmd "-Dtest=PromotionBpmnDefinitionTest,PromotionControllerTest,PromotionMainPageBrowserTest,PromotionApiControllerTest,PromotionMapperTest,PromotionApprovalDecisionServiceTest,PromotionApprovalReassignmentServiceTest,PromotionApprovalReminderServiceTest,PromotionApprovalRouteServiceTest,PromotionAuthorizationServiceTest,PromotionConfigurationServiceTest,PromotionEligibilityServiceTest,PromotionEligibilityOverrideServiceTest,PromotionImplementationServiceTest,PromotionAttachmentServiceTest,PromotionDocumentIntegrationServiceTest,PromotionCancellationServiceTest,PromotionWorkflowCancellationServiceTest,PromotionPayrollHandoffServiceTest,PromotionSalaryImpactServiceTest,PromotionReportServiceTest,PromotionWorkflowRuntimeTest,PromotionWorkflowServiceAuthorizationTest,PromotionSubmissionServiceTest,PromotionNotificationServiceTest,PromotionEmailServiceTest,PromotionRequiredDocumentServiceTest,PromotionRequesterVisibilityServiceTest,PromotionSecurityTest,PromotionActivityServiceTest" test
```

Full suite should remain:

```text
mvnw.cmd test
```

## Detailed Bugfix Backlog

| Priority | Bug/GAP | Impact | Fix Summary | Verification |
| --- | --- | --- | --- | --- |
| P0 | Admin cannot click to Promotion from `/admin/users`. | Admin manual test fails immediately. | Add `Promotions` button beside `Employee Exits`. | Admin click-path test. |
| P0 | HR cannot click to Promotion from sidebar. | HR manual test requires typed URL. | Restore role-conditional sidebar link to `/hr/promotions`. | HR click-path test. |
| P0 | Finance cannot click to Promotion. | Finance cannot reach approvals/handoffs. | Add Finance sidebar link to `/finance/promotions`. | Finance click-path test. |
| P0 | Employee Promotion link is commented and old page is static. | Employee cannot test real Promotion history. | Replace `/employee/promotions` with real Promotion employee page. | Employee scoped browser test. |
| P0 | Role-owned URLs do not exist. | Product requirement not met. | Add final routes and legacy redirects. | Controller route tests. |
| P0 | JobHR/RestrictedHR may be confused with HR in shared layouts/global flags. | Excluded roles could see links or access pages. | Explicitly exclude in Promotion capabilities and link conditions. | Negative security/link tests. |
| P0 | Manager access missing from `canAccessPromotionWorkArea`. | Line-manager/department-head approval UI cannot be reached reliably. | Add manager capability for hiring manager and department head. | Manager route and task tests. |
| P1 | Single shared page shows/hides panels by failed API calls. | Poor UX and role leakage risk. | Split role-owned templates. | Role panel visibility tests. |
| P1 | Broad payroll handoff authorization. | Finance-specific action may be too broadly exposed. | Add payroll handoff capability. | API authorization tests. |
| P1 | Raw escalation approver ID input. | Tester can type invalid IDs; poor UX. | Replace with protected employee select. | Form quality test. |
| P1 | Free-text escalation group. | Typos and invalid group names. | Replace with approval-group select. | Configuration test. |
| P1 | Reassignment/delegation may require raw ID/prompt UX. | Hard for tester and risky. | Use employee dropdown modal. | Reassignment browser test. |
| P1 | Executive has no explicit landing route. | Executive login path unclear. | Redirect executive login to `/executive/promotions`. | Executive click-path test. |
| P2 | Prompt-based comments for actions. | Unprofessional UX and harder testing. | Replace prompts with modals/forms. | Browser tests for approve/reject/return/payroll. |
| P2 | Report role boundaries unclear in UI. | Finance/employee may see wrong controls. | Role-specific report panels and API guards. | Report auth tests. |
| P2 | Activity metadata visibility not role-tuned. | Sensitive audit metadata could be overexposed. | Show full metadata to Admin/HR; safe timeline to Employee. | Mapper/browser tests. |
| P2 | Browser coverage missing reject/return/cancel/upload/delete. | Testing guide not fully automated. | Add Playwright tests. | Promotion browser suite. |

## Manual QA Matrix After Fixes

### Admin

- [ ] Login lands on `/admin/users`.
- [ ] Click `Promotions` button beside `Employee Exits`.
- [ ] Page opens `/admin/promotions`.
- [ ] Dashboard metrics load.
- [ ] Create draft.
- [ ] Upload required submit documents.
- [ ] Submit.
- [ ] Override warning eligibility check if applicable.
- [ ] Approve HR task if applicable.
- [ ] Reassign and delegate active approval.
- [ ] Cancel eligible request.
- [ ] Implement approved request.
- [ ] Configure routing, required documents, reminders, and escalation.
- [ ] View reports and export CSV.
- [ ] View payroll handoffs.

### HR

- [ ] Login lands on `/departments`.
- [ ] Click sidebar `Promotions`.
- [ ] Page opens `/hr/promotions`.
- [ ] Confirm every Admin Promotion action is available.
- [ ] Confirm HR does not need Admin sidebar or `/admin/users`.

### Finance

- [ ] Login lands on `/finance/dashboard`.
- [ ] Click sidebar `Promotions`.
- [ ] Page opens `/finance/promotions`.
- [ ] Finance approval tasks load.
- [ ] Detail view shows salary where allowed.
- [ ] Approve/reject/return finance task.
- [ ] View payroll handoffs.
- [ ] Acknowledge/apply/cancel payroll handoff.
- [ ] View permitted reports/exports.
- [ ] Confirm no create/configuration/implementation controls.

### Executive

- [ ] Login lands on `/executive/promotions`.
- [ ] Executive approval tasks load.
- [ ] Detail view shows salary visibility.
- [ ] Approve/reject/return executive task.
- [ ] Confirm no configuration, payroll handoff, create, submit, or implementation controls.

### Manager

- [ ] Hiring manager can reach `/manager/promotions` by clicking.
- [ ] Department head can reach `/manager/promotions` by clicking.
- [ ] Assigned line-manager tasks load.
- [ ] Detail view loads for assigned task.
- [ ] Approve/reject/return assigned task.
- [ ] Confirm no HR, Finance, report, configuration, implementation, or payroll handoff controls.
- [ ] Confirm salary is hidden unless backend rules explicitly allow it.

### Employee

- [ ] Login lands on `/employee/dashboard`.
- [ ] Click sidebar `Promotions`.
- [ ] Page opens `/employee/promotions`.
- [ ] Own/requested records load.
- [ ] Status timeline/history loads.
- [ ] Allowed documents/letters can be viewed/downloaded.
- [ ] Unrelated promotion direct link is blocked.
- [ ] No create/update/submit/approve/implement/report/handoff controls appear.

### Excluded Roles

- [ ] JobHR sees no Promotion link.
- [ ] RestrictedHR sees no Promotion link.
- [ ] JobHR cannot load any Promotion role page.
- [ ] RestrictedHR cannot load any Promotion role page.
- [ ] Asset Manager sees no Promotion link and cannot load Promotion pages.
- [ ] Auditor sees no Promotion link and cannot load Promotion pages.

## Definition Of Done

The bugfix work is complete when:

- All final role-owned URLs exist and are protected.
- Every allowed role can reach Promotion by clicking from its existing landing page.
- No excluded role sees or accesses Promotion.
- HR and Admin have identical Promotion powers.
- JobHR and RestrictedHR are explicitly excluded.
- Department head is treated as Manager for Promotion approval access.
- `/employee/promotions` uses real Promotion data and replaces the static page.
- Legacy `/promotion` routes redirect by role.
- Role-owned pages show only role-appropriate panels/actions.
- No visible raw ID inputs remain in Promotion forms.
- Reference endpoints are capability-protected.
- Backend API authorization matches UI capability rules.
- Existing Promotion business behavior remains intact.
- Promotion targeted tests pass.
- Full Maven test suite passes.
- Manual QA matrix in this file and `promotion-testing-guide.md` can be completed without typing Promotion URLs.
