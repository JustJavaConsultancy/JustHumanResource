package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.payroll.entity.*;
import com.justjava.humanresource.payroll.statutory.entity.PayeTaxBand;
import com.justjava.humanresource.payroll.statutory.entity.PensionScheme;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PayrollSetupService {

    /* =========================
     * PAYE
     * ========================= */
    PayeTaxBand createPayeTaxBand(PayeTaxBand band);
    List<PayeTaxBand> getActivePayeBands(LocalDate date);
    void validatePayeConfiguration(LocalDate date);

    /* =========================
     * PENSION
     * ========================= */
    PensionScheme createPensionScheme(PensionScheme scheme);
    List<PensionScheme> getActivePensionSchemes();

    /* =========================
     * ALLOWANCES
     * ========================= */
    Allowance createAllowance(Allowance allowance);
    List<Allowance> getActiveAllowances();

    /* =========================
     * DEDUCTIONS
     * ========================= */
    Deduction createDeduction(Deduction deduction);
    List<Deduction> getActiveDeductions();

    /* ============================================================
   BULK PAYGROUP CONFIGURATION
   ============================================================ */

    List<PayGroupAllowanceResponse> addAllowancesToPayGroup(
            Long payGroupId,
            List<AllowanceAttachmentRequest> requests
    );

    List<PayGroupDeductionResponse> addDeductionsToPayGroup(
            Long payGroupId,
            List<DeductionAttachmentRequest> requests
    );

/* ============================================================
   BULK EMPLOYEE CONFIGURATION
   ============================================================ */

    List<EmployeeAllowanceResponse> addAllowancesToEmployee(
            Long employeeId,
            List<AllowanceAttachmentRequest> requests
    );

    List<EmployeeDeductionResponse> addDeductionsToEmployee(
            Long employeeId,
            List<DeductionAttachmentRequest> requests
    );


    /* =========================
     * SYSTEM READINESS
     * ========================= */
    void validatePayrollSystemReadiness(LocalDate payrollDate);
    PayGroupAllowance addAllowanceToPayGroup(
            Long payGroupId,
            Long allowanceId,
            BigDecimal overrideAmount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    );

    PayGroupDeduction addDeductionToPayGroup(
            Long payGroupId,
            Long deductionId,
            BigDecimal overrideAmount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    );

    /* ============================================================
       EMPLOYEE CONFIGURATION
       ============================================================ */

    EmployeeAllowance addAllowanceToEmployee(
            Long employeeId,
            Long allowanceId,
            boolean overridden,
            BigDecimal overrideAmount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    );

    EmployeeDeduction addDeductionToEmployee(
            Long employeeId,
            Long deductionId,
            boolean overridden,
            BigDecimal overrideAmount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    );
}
