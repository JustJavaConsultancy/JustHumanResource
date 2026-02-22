package com.justjava.humanresource.workflow.delegate;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component("validatePeriodDelegate")
@RequiredArgsConstructor
public class ValidatePeriodDelegate implements JavaDelegate {

    private final PayrollPeriodRepository periodRepository;
    private final PayrollRunRepository payrollRunRepository;

    @Override
    public void execute(DelegateExecution execution) {

        Long periodId = getRequiredLong(execution, "periodId");
        Long companyId = getRequiredLong(execution, "companyId");

        PayrollPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() ->
                        new IllegalStateException("Payroll period not found."));

        if (!period.getCompanyId().equals(companyId)) {
            throw new IllegalStateException(
                    "Payroll period does not belong to company."
            );
        }

        if (period.getStatus() != PayrollPeriodStatus.OPEN
                && period.getStatus() != PayrollPeriodStatus.LOCKED) {

            throw new IllegalStateException(
                    "Only OPEN or LOCKED period can be validated."
            );
        }

        log.info("Validating payroll period {} for company {}",
                periodId, companyId);

        /* ============================================================
           VALIDATE PAYROLL COMPLETION
           ============================================================ */

        long postedCount =
                payrollRunRepository
                        .countByEmployee_Department_Company_IdAndPayrollDateBetweenAndStatus(
                                companyId,
                                period.getPeriodStart(),
                                period.getPeriodEnd(),
                                PayrollRunStatus.POSTED
                        );

        long incomplete =
                payrollRunRepository
                        .countByEmployee_Department_Company_IdAndPayrollDateBetweenAndStatusNot(
                                companyId,
                                period.getPeriodStart(),
                                period.getPeriodEnd(),
                                PayrollRunStatus.POSTED
                        );

        if (postedCount == 0) {
            throw new IllegalStateException(
                    "No POSTED payroll runs found for this period."
            );
        }

        if (incomplete > 0) {
            throw new IllegalStateException(
                    "Some payroll runs are not POSTED."
            );
        }

        /* ============================================================
           FINANCIAL RECONCILIATION
           ============================================================ */

        BigDecimal totalGross =
                payrollRunRepository.sumGrossByCompanyAndPeriodAndStatus(
                        companyId,
                        period.getPeriodStart(),
                        period.getPeriodEnd(),
                        PayrollRunStatus.POSTED
                );

        BigDecimal totalDeductions =
                payrollRunRepository.sumDeductionsByCompanyAndPeriodAndStatus(
                        companyId,
                        period.getPeriodStart(),
                        period.getPeriodEnd(),
                        PayrollRunStatus.POSTED
                );

        BigDecimal totalNet =
                payrollRunRepository.sumNetByCompanyAndPeriodAndStatus(
                        companyId,
                        period.getPeriodStart(),
                        period.getPeriodEnd(),
                        PayrollRunStatus.POSTED
                );

        if (totalGross.compareTo(
                totalDeductions.add(totalNet)) != 0) {

            throw new IllegalStateException(
                    "Payroll imbalance detected during validation."
            );
        }

        /* ============================================================
           PASS VARIABLES TO FLOWABLE
           ============================================================ */

        execution.setVariable("employeeCount", postedCount);
        execution.setVariable("totalGross", totalGross);
        execution.setVariable("totalDeductions", totalDeductions);
        execution.setVariable("totalNet", totalNet);

        log.info("Payroll period {} successfully validated.", periodId);
    }

    private Long getRequiredLong(
            DelegateExecution execution,
            String variableName) {

        Object value = execution.getVariable(variableName);

        if (!(value instanceof Long)) {
            throw new IllegalStateException(
                    "Missing or invalid process variable: " + variableName
            );
        }

        return (Long) value;
    }
}