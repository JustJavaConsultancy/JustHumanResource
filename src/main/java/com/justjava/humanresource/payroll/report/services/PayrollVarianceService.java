package com.justjava.humanresource.payroll.report.services;

import com.justjava.humanresource.payroll.report.dto.PayrollVarianceDTO;

import java.time.LocalDate;
import java.util.List;

public interface PayrollVarianceService {

    /**
     * Generates a variance report comparing each employee's payroll in the
     * current period against the immediately preceding period.
     *
     * <p>All employees with a POSTED run in the current period are included,
     * even when net variance is zero.</p>
     *
     * @param companyId          company to scope the report to
     * @param currentPeriodStart first day of the period to report on
     * @param currentPeriodEnd   last day of the period to report on
     * @param employeeId         optional — restricts the report to one employee
     * @param departmentId       optional — restricts the report to one department
     */
    List<PayrollVarianceDTO> generateVarianceReport(
            Long      companyId,
            LocalDate currentPeriodStart,
            LocalDate currentPeriodEnd,
            Long      employeeId,
            Long      departmentId
    );
}
