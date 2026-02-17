package com.justjava.humanresource.workflow.delegate;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component("validatePeriodDelegate")
@RequiredArgsConstructor
public class ValidatePeriodDelegate implements JavaDelegate {

    private final PayrollPeriodRepository periodRepository;
    private final PayrollRunRepository payrollRunRepository;

    @Override
    public void execute(DelegateExecution execution) {

        Long periodId = (Long) execution.getVariable("periodId");

        PayrollPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() ->
                        new IllegalStateException("Payroll period not found."));

        if (period.getStatus() != PayrollPeriodStatus.OPEN) {
            throw new IllegalStateException(
                    "Only OPEN period can be validated.");
        }

        long postedCount =
                payrollRunRepository.countByPayrollDateBetweenAndStatus(
                        period.getStartDate(),
                        period.getEndDate(),
                        PayrollRunStatus.POSTED
                );

        long incomplete =
                payrollRunRepository.countByPayrollDateBetweenAndStatusNot(
                        period.getStartDate(),
                        period.getEndDate(),
                        PayrollRunStatus.POSTED
                );

        if (postedCount == 0) {
            throw new IllegalStateException(
                    "No POSTED payroll runs found for this period.");
        }

        if (incomplete > 0) {
            throw new IllegalStateException(
                    "Some payroll runs are not POSTED.");
        }

        // Optimized DB aggregation
        BigDecimal totalGross =
                payrollRunRepository.sumGrossByPeriodAndStatus(
                        period.getStartDate(),
                        period.getEndDate(),
                        PayrollRunStatus.POSTED
                );

        BigDecimal totalDeductions =
                payrollRunRepository.sumDeductionsByPeriodAndStatus(
                        period.getStartDate(),
                        period.getEndDate(),
                        PayrollRunStatus.POSTED
                );

        BigDecimal totalNet =
                payrollRunRepository.sumNetByPeriodAndStatus(
                        period.getStartDate(),
                        period.getEndDate(),
                        PayrollRunStatus.POSTED
                );

        if (totalGross.compareTo(totalDeductions.add(totalNet)) != 0) {
            throw new IllegalStateException(
                    "Payroll imbalance detected during validation.");
        }

        // Store values for approval visibility
        execution.setVariable("employeeCount", postedCount);
        execution.setVariable("totalGross", totalGross);
        execution.setVariable("totalDeductions", totalDeductions);
        execution.setVariable("totalNet", totalNet);
    }
}
