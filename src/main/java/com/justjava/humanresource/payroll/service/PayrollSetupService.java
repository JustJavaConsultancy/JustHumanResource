package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.payroll.entity.Allowance;
import com.justjava.humanresource.payroll.entity.Deduction;
import com.justjava.humanresource.payroll.statutory.entity.PayeTaxBand;
import com.justjava.humanresource.payroll.statutory.entity.PensionScheme;

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

    /* =========================
     * SYSTEM READINESS
     * ========================= */
    void validatePayrollSystemReadiness(LocalDate payrollDate);
}
