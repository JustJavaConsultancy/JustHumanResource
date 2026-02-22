package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;

import java.time.LocalDate;

public interface PayrollPeriodService {

    /* ============================================================
       INITIALIZATION
       ============================================================ */

    /**
     * Opens the very first payroll period for a company.
     * Used during system setup or migration.
     */
    PayrollPeriod openInitialPeriod(
            Long companyId,
            LocalDate periodStart,
            LocalDate periodEnd
    );

    /* ============================================================
       PERIOD LIFECYCLE
       ============================================================ */

    /**
     * Moves OPEN → LOCKED.
     * No recalculation allowed after this.
     */
    void lockPeriod(Long companyId);

    /**
     * Moves OPEN → CLOSED
     * Then automatically creates and opens next period
     * based on company payroll cycle configuration.
     */
    void closeAndOpenNext(Long companyId);

    /**
     * Returns the currently OPEN period for a company.
     */
    PayrollPeriod getOpenPeriod(Long companyId);

    /* ============================================================
       VALIDATION
       ============================================================ */

    /**
     * Ensures payrollDate falls within the OPEN period
     * of the given company.
     */
    void validatePayrollDate(
            Long companyId,
            LocalDate payrollDate
    );

    /**
     * Returns true if payrollDate is inside OPEN period.
     */
    boolean isPayrollDateInOpenPeriod(
            Long companyId,
            LocalDate payrollDate
    );

    /**
     * Returns the period status for a given date.
     */
    PayrollPeriodStatus getPeriodStatusForDate(
            Long companyId,
            LocalDate payrollDate
    );

    /* ============================================================
       APPROVAL WORKFLOW
       ============================================================ */

    /**
     * Starts Flowable approval process
     * before final closing.
     */
    void initiatePeriodCloseApproval(
            Long companyId
    );
}