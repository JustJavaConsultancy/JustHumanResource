package com.justjava.humanresource.loan.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.loan.dto.EmployeeLoanExposureResponse;
import com.justjava.humanresource.loan.dto.LoanApprovalContextResponse;
import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.entity.EmployeeLoanApprovalStep;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Current-user resolution, salary snapshot, exposure and "who may see this application" rules. */
public interface LoanEmployeeContextService {

    enum ViewerRole { EMPLOYEE, HR, FINANCE, CUSTOM_APPROVER }

    /** Point-in-time employee facts copied onto an application. */
    record EmployeeSnapshot(Long departmentId, String departmentName,
                            String jobGradeName, String jobStepName, BigDecimal grossSalary) {
    }

    /** Employee linked to the logged-in Keycloak user (by email). */
    Employee getCurrentEmployee();

    /** Gross salary comes from the employee's job step; zero when no job step is assigned. */
    EmployeeSnapshot snapshot(Employee employee);

    /**
     * Active loans, other pending applications and missed deductions. Pass the application being
     * viewed as {@code excludeApplicationId} so it is not counted against itself (may be null).
     */
    EmployeeLoanExposureResponse getExposure(Long employeeId, Long excludeApplicationId);

    /** Informational context for HR, Finance and custom approvers. Never auto-rejects. */
    LoanApprovalContextResponse buildApprovalContext(EmployeeLoanApplication application);

    /**
     * Role under which the current user may see the application: owner, HR/admin, Finance, or an
     * assigned custom approver. Non-owners never see another employee's DRAFT.
     *
     * @throws org.springframework.security.access.AccessDeniedException otherwise
     */
    ViewerRole requireViewAccess(EmployeeLoanApplication application);

    /** True when the employee holds the current custom step, or has already decided one on this application. */
    boolean isAssignedCustomApprover(EmployeeLoanApplication application, Long employeeId);

    /** Custom approval steps that are the current actionable step of a PENDING_CUSTOM_APPROVAL application. */
    List<EmployeeLoanApprovalStep> findAssignedCustomTasks(Long employeeId);

    /** id to full name; ids that do not resolve are omitted. */
    Map<Long, String> employeeNames(Collection<Long> employeeIds);
}
