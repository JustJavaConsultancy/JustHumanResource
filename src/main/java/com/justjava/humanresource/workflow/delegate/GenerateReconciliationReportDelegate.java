package com.justjava.humanresource.workflow.delegate;

import com.justjava.humanresource.core.enums.PayrollRunStatus;

import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component("generateReconciliationDelegate")
@RequiredArgsConstructor
public class GenerateReconciliationReportDelegate implements JavaDelegate {

    private final PayrollPeriodRepository periodRepository;
    private final PayrollRunRepository payrollRunRepository;

    @Override
    public void execute(DelegateExecution execution) {

        Long periodId = (Long) execution.getVariable("periodId");

        PayrollPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() ->
                        new IllegalStateException("Payroll period not found."));

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

        long employeeCount =
                payrollRunRepository.countByPayrollDateBetweenAndStatus(
                        period.getStartDate(),
                        period.getEndDate(),
                        PayrollRunStatus.POSTED
                );

        execution.setVariable("reconEmployeeCount", employeeCount);
        execution.setVariable("reconGross", totalGross);
        execution.setVariable("reconDeductions", totalDeductions);
        execution.setVariable("reconNet", totalNet);
    }
}
