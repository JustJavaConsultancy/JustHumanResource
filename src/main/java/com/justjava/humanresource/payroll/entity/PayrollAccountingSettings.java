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
}
