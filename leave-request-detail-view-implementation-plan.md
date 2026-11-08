# Leave Request Detail View - Implementation Plan

## Goal

Add a full detail page for a single leave request in both portals:

- Employee portal: `templates/employees/leave.html`
- HR leave portal: `templates/leave/main.html`

The existing tables should remain summary tables. Do not add the long detail fields into those tables. Add an eye icon link instead.

The new pages must not be added to any sidebar or layout menu. They are linked only from the eye icons, although the URLs will still be directly reachable by authorized users.

## Decision: Page Instead Of Modal

Use a separate details page, not a modal. This is the better choice here because a line manager may have many pending approvals. A modal-based design often tempts the implementation to preload or keep too much row detail in the DOM. A details page fetches only one request when needed, keeps the leave tables light, and is easier to authorize and debug.

## Confirmed Project Facts

- `LeaveRequest` extends `BaseEntity`.
- `BaseEntity` provides `id`, `createdAt`, `updatedAt`, and `version`.
- `LeaveRequestRepository` already supports `findAllByOrderByCreatedAtDesc()` and `findByEmployeeIdOrderByCreatedAtDesc(Long employeeId)`.
- `LeaveWorkflowService.getMyLeaveRequests()` returns all leave requests for HR users and only the current employee's requests for regular employees.
- `FlowableTaskDTO.getVariables()` is `Map<String, Object>`.
- `FlowableTaskService.getTasksForAssignee(String assignee, String processDefinitionKey)` exists.
- `LeaveApprovalStepRepository.findByLeaveRequestIdOrderBySequenceNoAsc(Long leaveRequestId)` exists.
- `Employee.getFullName()` exists.
- There is already a `src/test/java/com/justjava/humanresource/leave/LeaveControllerTest.java`.
- `rg` is not installed in this workspace; use PowerShell search commands when needed.

## Master File Manifest

This is the complete file touch list. The coding AI should not touch unrelated files.

| # | File | Action | Introduced in |
|---|------|--------|---------------|
| 1 | `src/main/java/com/justjava/humanresource/leave/dto/LeaveRequestDetailDTO.java` | CREATE | Step 1 |
| 2 | `src/main/java/com/justjava/humanresource/leave/service/LeaveWorkflowService.java` | EDIT | Step 2 |
| 3 | `src/main/java/com/justjava/humanresource/leave/LeaveController.java` | EDIT | Step 3 |
| 4 | `src/main/java/com/justjava/humanresource/employee/EmployeeController.java` | EDIT | Step 3 |
| 5 | `src/main/resources/templates/employees/leave.html` | EDIT | Step 4 |
| 6 | `src/main/resources/templates/employees/leave-details.html` | CREATE | Step 5 |
| 7 | `src/main/resources/templates/leave/main.html` | EDIT | Step 6 |
| 8 | `src/main/resources/templates/leave/details.html` | CREATE | Step 7 |
| 9 | `src/test/java/com/justjava/humanresource/leave/LeaveControllerTest.java` | EDIT | Step 8 |

---

## Scope Rules

Do:

- Add one backend JSON endpoint for leave request details.
- Add one employee-facing details page.
- Add one HR-facing details page.
- Add an eye icon at the end of both employee leave tables.
- Add a `Request` column at the front of the HR "All Leave Requests" table.
- Add an eye icon at the end of the HR "All Leave Requests" table.

Do not:

- Change the existing employee table columns except for adding the final `Details` column.
- Change the HR "Pending Approvals" table at all.
- Add either new details page to `layout.html`, `employeeLayout.html`, or any menu.
- Touch mobile leave templates unless the user separately asks for mobile support.
- Refactor unrelated leave workflow behavior.
- Touch employee exit, payroll, accounting, KPI, or other modules.

## Fields Required On The Details Page

Show these fields, in this order:

1. Request, for example `Leave #8`
2. Leave Type
3. Period, for example `2026-09-21 to 2026-09-25`
4. Days
5. Requesting Employee, for example `Adeyemi Sunday (ID: 5)`
6. Reason
7. Stand-in Colleague, for example `Adeyemi Sunday (ID: 5)`
8. Date Created
9. Status
10. Approval Level, for example `1 / 3`

Use the raw numeric database employee id in the displayed `ID: ...` text, not `employeeNumber`.

## File Manifest

Create:

- `src/main/java/com/justjava/humanresource/leave/dto/LeaveRequestDetailDTO.java`
- `src/main/resources/templates/employees/leave-details.html`
- `src/main/resources/templates/leave/details.html`

Edit:

- `src/main/java/com/justjava/humanresource/leave/service/LeaveWorkflowService.java`
- `src/main/java/com/justjava/humanresource/leave/LeaveController.java`
- `src/main/java/com/justjava/humanresource/employee/EmployeeController.java`
- `src/main/resources/templates/employees/leave.html`
- `src/main/resources/templates/leave/main.html`
- `src/test/java/com/justjava/humanresource/leave/LeaveControllerTest.java`

## Route Summary

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/leave/requests/{id}/details` | Shared JSON detail endpoint |
| `GET` | `/employee/leave/details/{id}` | Employee portal detail page |
| `GET` | `/leave/details/{id}` | HR portal detail page |

## Authorization Rule

Allow detail access when any of these is true:

- The current user is HR, Job HR, Admin, or Restricted HR.
- The current employee created the leave request.
- The current employee currently has a pending Flowable task for that leave request.
- The current employee has a past recorded `LeaveApprovalStep` for that leave request.

Deny everyone else with:

```java
throw new IllegalStateException("You are not authorized to view this leave request.");
```

Important implementation detail: check the HR override before requiring `getCurrentEmployee()`. Some HR accounts may be able to access `/leave` without having a matching `Employee` row. If the service resolves the employee first, HR access can fail before the HR override is applied.

## Step 1 - Add Detail DTO

### Files To Give The AI

Provide these files in full:

- `src/main/java/com/justjava/humanresource/leave/entity/LeaveRequest.java`
- `src/main/java/com/justjava/humanresource/leave/dto/LeaveRequestCreateCommand.java`
- `src/main/java/com/justjava/humanresource/leave/enums/LeaveRequestStatus.java`
- `src/main/java/com/justjava/humanresource/core/entity/BaseEntity.java`

### Files To Create

- `src/main/java/com/justjava/humanresource/leave/dto/LeaveRequestDetailDTO.java`

### Exact Work

Create the detail DTO with the following structure:

Use:

```java
package com.justjava.humanresource.leave.dto;

import com.justjava.humanresource.leave.enums.LeaveRequestStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class LeaveRequestDetailDTO {
    private Long id;
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer totalDays;
    private String reason;
    private LeaveRequestStatus status;
    private Integer currentApprovalLevel;
    private Integer totalApprovalLevels;
    private LocalDateTime createdAt;
    private Long requesterId;
    private String requesterName;
    private Long standInId;
    private String standInName;
}
```

## Step 2 - Add Service Method

### Files To Give The AI

Provide these files in full:

- `src/main/java/com/justjava/humanresource/leave/service/LeaveWorkflowService.java`
- `src/main/java/com/justjava/humanresource/leave/entity/LeaveRequest.java`
- `src/main/java/com/justjava/humanresource/leave/entity/LeaveApprovalStep.java`
- `src/main/java/com/justjava/humanresource/leave/repository/LeaveRequestRepository.java`
- `src/main/java/com/justjava/humanresource/leave/repository/LeaveApprovalStepRepository.java`
- `src/main/java/com/justjava/humanresource/hr/service/EmployeeService.java`
- `src/main/java/com/justjava/humanresource/core/config/AuthenticationManager.java`
- `src/main/java/com/justjava/humanresource/leave/dto/LeaveRequestDetailDTO.java`

### Files To Edit

- `LeaveWorkflowService.java`

### Exact Work

Edit `LeaveWorkflowService.java`.

Add imports:

```java
import com.justjava.humanresource.leave.dto.LeaveRequestDetailDTO;
import java.util.Objects;
```

Add this public method:

```java
@Transactional(readOnly = true)
public LeaveRequestDetailDTO getLeaveRequestDetail(Long leaveRequestId) {
    LeaveRequest request = leaveRequestRepository.findById(leaveRequestId)
            .orElseThrow(() -> new IllegalStateException("Leave request not found"));

    if (!isHrUser()) {
        Employee current = getCurrentEmployee();
        if (!canEmployeeViewLeaveRequest(request, current)) {
            throw new IllegalStateException("You are not authorized to view this leave request.");
        }
    }

    Employee requester = employeeService.getById(request.getEmployeeId());
    Employee standIn = employeeService.getById(request.getStandInEmployeeId());

    return LeaveRequestDetailDTO.builder()
            .id(request.getId())
            .leaveType(request.getLeaveType())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .totalDays(request.getTotalDays())
            .reason(request.getReason())
            .status(request.getStatus())
            .currentApprovalLevel(request.getCurrentApprovalLevel())
            .totalApprovalLevels(request.getTotalApprovalLevels())
            .createdAt(request.getCreatedAt())
            .requesterId(requester.getId())
            .requesterName(requester.getFullName())
            .standInId(standIn.getId())
            .standInName(standIn.getFullName())
            .build();
}
```

Add this private helper:

```java
private boolean canEmployeeViewLeaveRequest(LeaveRequest request, Employee current) {
    if (current == null || current.getId() == null) {
        return false;
    }

    if (Objects.equals(request.getEmployeeId(), current.getId())) {
        return true;
    }

    boolean hasPendingTask = flowableTaskService
            .getTasksForAssignee(String.valueOf(current.getId()), "leaveApprovalProcess")
            .stream()
            .anyMatch(task -> {
                Map<String, Object> variables = task.getVariables();
                Object taskLeaveRequestId = variables != null ? variables.get("leaveRequestId") : null;
                return taskLeaveRequestId != null
                        && String.valueOf(taskLeaveRequestId).equals(String.valueOf(request.getId()));
            });

    if (hasPendingTask) {
        return true;
    }

    return leaveApprovalStepRepository.findByLeaveRequestIdOrderBySequenceNoAsc(request.getId())
            .stream()
            .anyMatch(step -> Objects.equals(step.getApproverEmployeeId(), current.getId()));
}
```

Why the `String.valueOf(...)` comparison matters: Flowable variables may come back as `Long`, `Integer`, or `String`. Raw `.equals()` can fail even when the numeric value is the same.

## Step 3 - Add Controller Routes

### Files To Give The AI

Provide these files in full:

- `src/main/java/com/justjava/humanresource/leave/LeaveController.java`
- `src/main/java/com/justjava/humanresource/employee/EmployeeController.java`
- `src/main/java/com/justjava/humanresource/leave/service/LeaveWorkflowService.java`
- `src/main/java/com/justjava/humanresource/core/config/AuthenticationManager.java`
- `src/main/java/com/justjava/humanresource/hr/service/EmployeeService.java`

### Files To Edit

- `LeaveController.java`
- `EmployeeController.java`

### Exact Work

Edit `LeaveController.java`.

Add import:

```java
import com.justjava.humanresource.leave.dto.LeaveRequestDetailDTO;
```

Add the shared JSON endpoint near `getLeaveSteps`:

```java
@GetMapping("/leave/requests/{id}/details")
@ResponseBody
public LeaveRequestDetailDTO getLeaveRequestDetails(@PathVariable Long id) {
    return leaveWorkflowService.getLeaveRequestDetail(id);
}
```

Add the HR details page route:

```java
@GetMapping("/leave/details/{id}")
public String leaveDetailsPage(@PathVariable Long id, Model model) {
    if (authenticationManager.isEmployee() && !isHrUser()) {
        return "redirect:/employee/leave/details/" + id;
    }
    model.addAttribute("title", "Leave Request Details");
    model.addAttribute("subTitle", "Full details for this leave request");
    model.addAttribute("leaveRequestId", id);
    return "leave/details";
}
```

Edit `EmployeeController.java`.

Add this route immediately after `getLeave(Model model)`:

```java
@GetMapping("/employee/leave/details/{id}")
public String getLeaveDetails(@PathVariable Long id, Model model) {
    String email = (String) authenticationManager.get("email");
    Employee loginEmployee = employeeService.getByEmail(email);
    Employee employee = employeeService.getEmployeeWithBankDetails(loginEmployee.getId());

    model.addAttribute("employee", employee);
    model.addAttribute("leaveRequestId", id);
    model.addAttribute("title", "Leave Request Details");
    model.addAttribute("subTitle", "Full details for this leave request");
    return "employees/leave-details";
}
```

## Step 4 - Employee Portal Table Links

### Files To Give The AI

Provide these files in full:

- `src/main/resources/templates/employees/leave.html`

### Files To Edit

- `templates/employees/leave.html`

### Exact Work

Edit `templates/employees/leave.html`.

In the "Leave Requests" table:

- Add a final header: `Details`
- Change its loading, empty, and error colspans from `5` to `6`.
- Add a final cell to each row:

```html
<td class="px-4 py-3">
    <a href="/employee/leave/details/${r.id}" class="text-primary-600 hover:text-primary-700 dark:text-primary-400" title="View details">
        <span class="material-icons-round text-base align-middle">visibility</span>
    </a>
</td>
```

In the "Pending Approvals" table:

- Add a final header: `Details`
- Change its loading, empty, and error colspans from `4` to `5`.
- Add a final cell after the current Action cell:

```html
<td class="px-4 py-3">
    ${leaveRequestId !== '-' ? `<a href="/employee/leave/details/${leaveRequestId}" class="text-primary-600 hover:text-primary-700 dark:text-primary-400" title="View details"><span class="material-icons-round text-base align-middle">visibility</span></a>` : ''}
</td>
```

Do not change the existing table column order or existing action buttons.

## Step 5 - Employee Detail Template

### Files To Give The AI

Provide these files in full:

- `src/main/resources/templates/layouts/employeeLayout.html`
- `src/main/resources/templates/employees/leave.html` (as reference for styling and patterns)

### Files To Create

- `src/main/resources/templates/employees/leave-details.html`

### Exact Work

Create `templates/employees/leave-details.html`.

Requirements:

- Use `layout:decorate="~{layouts/employeeLayout}"`.
- Add a top back button linking to `/employee/leave`.
- Read `leaveRequestId` from Thymeleaf using `th:inline="javascript"`.
- Fetch `/leave/requests/${leaveRequestId}/details`.
- Display the ten required fields in order.
- Use `textContent` for free text fields, especially `reason`.
- Use the existing employee `badge(status)` styling for status.
- Show a simple loading state first.
- Replace the content with a clear error state if the fetch fails.

Recommended structure:

```html
<script th:inline="javascript">
    const leaveRequestId = /*[[${leaveRequestId}]]*/ 0;
</script>
```

Date formatting:

```javascript
function formatDateTime(value) {
    return value ? String(value).replace('T', ' ') : '-';
}
```

Person formatting:

```javascript
function formatPerson(name, id) {
    return `${name || '-'}${id ? ' (ID: ' + id + ')' : ''}`;
}
```

Only status badge HTML should be assigned with `innerHTML`; all other values should use `textContent`.

## Step 6 - HR Portal Table Links

### Files To Give The AI

Provide these files in full:

- `src/main/resources/templates/leave/main.html`

### Files To Edit

- `templates/leave/main.html`

### Exact Work

Edit `templates/leave/main.html`.

Only modify the "All Leave Requests" table.

Add a first header before `Type`:

```html
<th class="text-left px-4 py-3">Request</th>
```

Add a final header after `Status`:

```html
<th class="text-left px-4 py-3">Details</th>
```

In `loadMyLeaveRequests()`:

- Change loading, empty, and error colspans from `4` to `6`.
- Add a first cell:

```html
<td class="px-4 py-3 font-medium">Leave #${r.id ?? ''}</td>
```

- Add a final details cell:

```html
<td class="px-4 py-3">
    <a href="/leave/details/${r.id}" class="text-primary-600 hover:text-primary-700" title="View details">
        <span class="material-icons-round text-base align-middle">visibility</span>
    </a>
</td>
```

Do not touch:

- HR "Pending Approvals" table header
- `loadPendingApprovals()`
- Any HR pending approval columns

## Step 7 - HR Detail Template

### Files To Give The AI

Provide these files in full:

- `src/main/resources/templates/layout.html`
- `src/main/resources/templates/leave/main.html` (as reference for styling and patterns)

### Files To Create

- `src/main/resources/templates/leave/details.html`

### Exact Work

Create `templates/leave/details.html`.

Requirements:

- Use `layout:decorate="~{layout}"`.
- Add a top back button linking to `/leave`.
- Read `leaveRequestId` from Thymeleaf using `th:inline="javascript"`.
- Fetch `/leave/requests/${leaveRequestId}/details`.
- Display the same ten fields in the same order as the employee detail page.
- Reuse the HR page's `statusBadge(status)` styling.
- Use `fetchJsonSafe()` style validation from `main.html`.
- Use `textContent` for free text fields, especially `reason`.
- Show a simple loading state and a clear error state.

Do not add this page to the HR layout menu.

## Step 8 - Tests

### Files To Give The AI

Provide these files in full:

- `src/test/java/com/justjava/humanresource/leave/LeaveControllerTest.java`
- `src/main/java/com/justjava/humanresource/leave/LeaveController.java`
- `src/main/java/com/justjava/humanresource/leave/service/LeaveWorkflowService.java`

### Files To Edit

- `LeaveControllerTest.java`

### Exact Work

Update `LeaveControllerTest.java`.

Add tests for:

- `leaveDetailsPage` redirects plain employee users to `/employee/leave/details/{id}`.
- HR users can open `leave/details`.
- `getLeaveRequestDetails(id)` delegates to `leaveWorkflowService.getLeaveRequestDetail(id)`.

If a service test class already exists when implementing this later, add focused tests for:

- Requester can view.
- Current pending approver can view.
- Past approver can view.
- HR can view without requiring employee ownership.
- Unrelated employee is denied.

If no service test convention exists, do not invent a broad new testing setup just for this change. Keep verification manual plus controller tests.

## Verification Commands

Compile:

```powershell
.\mvnw.cmd compile
```

Run the leave controller test:

```powershell
.\mvnw.cmd -Dtest=LeaveControllerTest test
```

If the project has broader leave tests later, run them too.

## Manual QA Checklist

Employee requester:

- Log in as an employee.
- Open `/employee/leave`.
- Submit a leave request with a distinctive reason.
- Confirm the request appears in "Leave Requests".
- Click the eye icon.
- Confirm all ten detail fields show correct values.
- Confirm the back button returns to `/employee/leave`.

Line manager approver:

- Log in as the assigned approver.
- Open `/employee/leave`.
- Confirm the request appears in "Pending Approvals".
- Click the eye icon.
- Confirm the detail page opens even though the approver is not the requester.
- Approve or reject the request.
- Reopen the same details URL and confirm it still opens because a `LeaveApprovalStep` now exists.

HR:

- Log in as HR.
- Open `/leave`.
- Confirm "All Leave Requests" now shows `Request` first and `Details` last.
- Confirm HR "Pending Approvals" is unchanged.
- Click the eye icon for a request HR did not create or approve.
- Confirm the HR detail page opens.
- Confirm the back button returns to `/leave`.

Negative access:

- Log in as an unrelated employee.
- Directly open `/leave/requests/{id}/details` for a request they did not create and are not approving.
- Confirm the request is rejected and no detail data is returned.

## Completion Definition

The task is complete when:

- Both detail templates exist.
- Both detail templates are linked only from the requested eye icons.
- Neither detail template is added to any menu.
- The shared JSON endpoint returns all required fields.
- Authorization allows HR, requester, current approver, and past approver.
- Authorization denies unrelated employees.
- Employee leave tables keep their existing columns and add only the final `Details` column.
- HR "All Leave Requests" adds `Request` first and `Details` last.
- HR "Pending Approvals" is unchanged.
- Compile and relevant tests pass.
