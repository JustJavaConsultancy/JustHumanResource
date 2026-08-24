package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "payroll_accounting_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payroll_accounting_settings_company",
                columnNames = "company_id"
        )
)
public class PayrollAccountingSettings extends BaseEntity {
    public static final String DEFAULT_RESIDUAL_ADJUSTMENT_EXPENSE_ACCOUNT_CODE = "5090";
    public static final String DEFAULT_RESIDUAL_ADJUSTMENT_EXPENSE_ACCOUNT_NAME = "Payroll Residual Adjustment Expense";
    public static final String DEFAULT_RESIDUAL_CLEARING_ACCOUNT_CODE = "2190";
    public static final String DEFAULT_RESIDUAL_CLEARING_ACCOUNT_NAME = "Payroll Residual Clearing";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String suspenseAccountCode = "2000";

    @Column(nullable = false)
    private String suspenseAccountName = "Payroll Suspense";

    @Column(nullable = false)
    private String salaryPayableAccountCode = "2000";

    @Column(nullable = false)
    private String salaryPayableAccountName = "Salary Payable";

    @Column(nullable = false)
    private String bankAccountCode = "1000";

    @Column(nullable = false)
    private String bankAccountName = "Bank/Cash";

    @Column(nullable = false)
    private String defaultEarningExpenseAccountCode = "5000";

    @Column(nullable = false)
    private String defaultEarningExpenseAccountName = "Salary Expense";

    @Column(nullable = false)
    private String defaultDeductionPayableAccountCode = "2100";

    @Column(nullable = false)
    private String defaultDeductionPayableAccountName = "Payroll Deductions Payable";

    @Column(nullable = false)
    private String payePayableAccountCode = "2110";

    @Column(nullable = false)
    private String payePayableAccountName = "PAYE Tax Payable";

    @Column(nullable = false)
    private String pensionPayableAccountCode = "2120";

    @Column(nullable = false)
    private String pensionPayableAccountName = "Pension Payable";

    @Column(nullable = false)
    private String employerPensionExpenseAccountCode = "5010";

    @Column(nullable = false)
    private String employerPensionExpenseAccountName = "Employer Pension Expense";

    @Column(nullable = false)
    private String employerPensionPayableAccountCode = "2120";

    @Column(nullable = false)
    private String employerPensionPayableAccountName = "Pension Payable";

    private String residualAdjustmentExpenseAccountCode = DEFAULT_RESIDUAL_ADJUSTMENT_EXPENSE_ACCOUNT_CODE;

    private String residualAdjustmentExpenseAccountName = DEFAULT_RESIDUAL_ADJUSTMENT_EXPENSE_ACCOUNT_NAME;

    private String residualClearingAccountCode = DEFAULT_RESIDUAL_CLEARING_ACCOUNT_CODE;

    private String residualClearingAccountName = DEFAULT_RESIDUAL_CLEARING_ACCOUNT_NAME;
}
