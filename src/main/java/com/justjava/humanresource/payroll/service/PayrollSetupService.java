package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.payroll.dto.FutureEmployeeAllowanceDTO;
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
    PayeTaxBand updateTax(Long id, PayeTaxBand incoming);
    List<PayeTaxBand> getActivePayeBands(LocalDate date);
    void validatePayeConfiguration(LocalDate date);
    public List<FutureEmployeeAllowanceDTO> getFutureAllowancesForEmployee(Long employeeId);

    /* =========================
     * PENSION
     * ========================= */
    PensionScheme createPensionScheme(PensionScheme scheme);
    PensionScheme update(Long id, PensionScheme incoming);
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

    void deactivateRemovedAllowancesFromPayGroup(Long payGroupId, List<Long> activeAllowanceIds);
    void deactivateRemovedDeductionsFromPayGroup(Long payGroupId, List<Long> activeDeductionIds);
    void deactivateRemovedTaxReliefsFromPayGroup(Long payGroupId, List<Long> activeTaxReliefIds);

/* ============================================================
   BULK EMPLOYEE CONFIGURATION
   ============================================================ */

    List<EmployeeAllowanceResponse> addAllowancesToEmployee(
            Long employeeId,
            List<AllowanceAttachmentRequest> requests
    );
    List<EmployeeAllowanceResponse> getAllowancesForEmployee(Long employeeId);
    List<EmployeeDeductionResponse> addDeductionsToEmployee(
            Long employeeId,
            List<DeductionAttachmentRequest> requests
    );
    List<EmployeeDeductionResponse> getDeductionsForEmployee(Long employeeId);

    void deactivateRemovedAllowancesFromEmployee(Long employeeId, List<Long> activeAllowanceIds);
    void deactivateRemovedDeductionsFromEmployee(Long employeeId, List<Long> activeDeductionIds);
    void deactivateRemovedTaxReliefsFromEmployee(Long employeeId, List<Long> activeTaxReliefIds);

    List<PayGroup>getAllPayGroups();

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

    /* =========================
     * TAX RELIEF
     * ========================= */
    TaxRelief createTaxRelief(TaxRelief relief);
    List<TaxRelief> getActiveTaxReliefs();

/* ============================================================
   BULK PAYGROUP TAX RELIEF CONFIGURATION
   ============================================================ */

    List<PayGroupTaxReliefResponse> addTaxReliefsToPayGroup(
            Long payGroupId,
            List<TaxReliefAttachmentRequest> requests
    );

/* ============================================================
   BULK EMPLOYEE TAX RELIEF CONFIGURATION
   ============================================================ */

    List<EmployeeTaxReliefResponse> addTaxReliefsToEmployee(
            Long employeeId,
            List<TaxReliefAttachmentRequest> requests
    );

    List<EmployeeTaxReliefResponse> getTaxReliefsForEmployee(Long employeeId);

/* ============================================================
   SINGLE ATTACHMENT (LOW LEVEL)
   ============================================================ */

    PayGroupTaxRelief addTaxReliefToPayGroup(
            Long payGroupId,
            Long taxReliefId,
            BigDecimal overrideAmount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    );

    EmployeeTaxRelief addTaxReliefToEmployee(
            Long employeeId,
            Long taxReliefId,
            boolean overridden,
            BigDecimal overrideAmount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    );
}