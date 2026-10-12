# Promotion Testing Guide Navigation Closure Plan

## Goal

Strictly satisfy `doc/promotion-testing-guide.md` from a navigation perspective: every UI the walkthrough asks testers to open must have a visible, role-appropriate navigation path and must land on the expected panel or workflow area.

## Implementation Plan

1. Restore guide-compatible Promotion section routes:
   - Keep role-owned URLs as primary entry points.
   - Preserve section intent for:
     - `/promotion`
     - `/promotion/requests`
     - `/promotion/approvals`
     - `/promotion/reports`
     - `/promotion/configuration`
     - `/promotion/payroll-handoffs`
   - Redirect legacy routes to the current user's role-owned page with `section` query state.

2. Add role-owned page shells:
   - Admin and HR use the main HR layout.
   - Finance uses the finance layout.
   - Employee uses the employee layout.
   - Manager and Executive use an authenticated layout with only their allowed Promotion UI.

3. Add Promotion in-page navigation:
   - Add visible section navigation for:
     - Dashboard
     - Requests
     - Approval Tasks
     - Reports
     - Configuration
     - Payroll Handoffs
   - Gate every item by role/capability.
   - Clicking a section updates the URL state and shows/focuses the related panel.

4. Make route focus reliable:
   - Read `section` in the controller and pass it as `initialSection`.
   - If the requested section is not allowed for the role, fall back to the first allowed section.
   - Ensure the guide URLs land on the expected panels for authorized users.

5. Add missing HR/Admin detail actions:
   - Show Upload, Download, Remove, Submit, Cancel, Implement.
   - Add Generate Promotion Letter and Generate Salary Adjustment Letter controls using existing API endpoints.

6. Add reassignment/delegation UI:
   - Show Reassign and Delegate controls for active pending approval steps to HR/Admin.
   - Use existing endpoints:
     - `/api/promotions/{id}/approval-steps/{stepId}/reassign`
     - `/api/promotions/{id}/approval-steps/{stepId}/delegate`

7. Add approval task navigation and actions:
   - Make Approval Tasks directly reachable from section navigation and legacy route redirects.
   - Preserve View, Approve, Return, and Reject actions.

8. Complete payroll handoff navigation:
   - Finance/HR/Admin see Payroll Handoffs navigation.
   - `/promotion/payroll-handoffs` lands on the Payroll Handoffs panel for authorized users.

9. Complete reports navigation:
   - HR/Admin/Finance see Reports navigation when authorized.
   - Add visible report filters for date/status/department fields where the backend already accepts them.
   - Keep CSV links visible only where permitted.

10. Complete configuration navigation:
    - HR/Admin see Configuration navigation.
    - `/promotion/configuration` lands on Configuration for HR/Admin.
    - Add visible Configuration History and Deactivate controls where backend endpoints exist.

11. Employee/requester navigation:
    - Employee sidebar opens `/employee/promotions`.
    - Employee page defaults to own/requested records and hides operational actions.

12. Add walkthrough navigation tests:
    - Legacy section routes preserve section intent.
    - Role-owned routes set correct role and section.
    - Capability tests cover allowed and excluded navigation.

## Definition Of Done

- Every `promotion-testing-guide.md` "Open ..." instruction lands on the required UI.
- Every guide-required UI has visible navigation, not scroll-only discovery.
- Role users stay in their expected layout shell.
- Required actions are visible where allowed and hidden where forbidden.
- Focused controller/security/navigation tests pass.
