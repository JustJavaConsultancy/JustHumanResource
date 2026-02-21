package com.justjava.humanresource.hr.service;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;

import java.time.LocalDate;
import java.util.List;

public interface EmployeeService {

    /* =========================
     * EXISTING (UNCHANGED)
     * ========================= */

    Employee createEmployee(EmployeeDTO employee);
    EmployeeDTO createAndActivateEmployee(EmployeeDTO dto);
    List<EmployeeDTO> getAllEmployees();
    Employee getByEmployeeNumber(String employeeNumber);

    /* =========================
     * REFINED, EXPLICIT INTENT
     * ========================= */

    /**
     * Change employee pay group (promotion, transfer, reclassification).
     * Triggers payroll recalculation via domain event.
     */
    EmployeeDTO changePayGroup(
            Long employeeId,
            PayGroup newPayGroup,
            LocalDate effectiveDate
    );

    /**
     * Change employee job step (salary change).
     * Triggers payroll recalculation via domain event.
     */
    EmployeeDTO changeJobStep(
            Long employeeId,
            Long newJobStepId,
            LocalDate effectiveDate
    );

    public void changePosition(Long employeeId,Long jobStepId,Long payGroupId,LocalDate effectiveFrom);
    /**
     * Activate / deactivate employee without deleting history.
     * Payroll reacts only if status impacts pay.
     */
    EmployeeDTO changeEmploymentStatus(
            Long employeeId,
            EmploymentStatus newStatus,
            LocalDate effectiveDate
    );
}
