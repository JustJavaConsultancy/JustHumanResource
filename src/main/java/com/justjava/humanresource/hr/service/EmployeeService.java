package com.justjava.humanresource.hr.service;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;

import java.time.LocalDate;

public interface EmployeeService {

    /* =========================
     * EXISTING (UNCHANGED)
     * ========================= */

    Employee createEmployee(EmployeeDTO employee);

    Employee getByEmployeeNumber(String employeeNumber);

    /* =========================
     * REFINED, EXPLICIT INTENT
     * ========================= */

    /**
     * Change employee pay group (promotion, transfer, reclassification).
     * Triggers payroll recalculation via domain event.
     */
    Employee changePayGroup(
            Long employeeId,
            PayGroup newPayGroup,
            LocalDate effectiveDate
    );

    /**
     * Change employee job step (salary change).
     * Triggers payroll recalculation via domain event.
     */
    Employee changeJobStep(
            Long employeeId,
            Long newJobStepId,
            LocalDate effectiveDate
    );

    /**
     * Activate / deactivate employee without deleting history.
     * Payroll reacts only if status impacts pay.
     */
    Employee changeEmploymentStatus(
            Long employeeId,
            EmploymentStatus newStatus,
            LocalDate effectiveDate
    );
}
