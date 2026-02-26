package com.justjava.humanresource.workflow.delegate;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component("generateReconciliationDelegate")
@RequiredArgsConstructor
public class GenerateReconciliationReportDelegate implements JavaDelegate {

    private final PayrollPeriodRepository periodRepository;
    private final PayrollRunRepository payrollRunRepository;

    @Override
    public void execute(DelegateExecution execution) {

        Long periodId = getRequiredLong(execution, "periodId");
        Long companyId = getRequiredLong(execution, "companyId");

        PayrollPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() ->
                        new IllegalStateException("Payroll period not found."));

/*
        if (!period.getCompanyId().equals(companyId)) {
            throw new IllegalStateException(
                    "Period does not belong to provided company."
            );
        }
*/

        log.info("Generating reconciliation for company {} period {}",
                companyId, periodId);

        BigDecimal totalGross =
                payrollRunRepository
                        .sumGrossByCompanyAndPeriodAndStatus(
                                companyId,
                                period.getPeriodStart(),
                                period.getPeriodEnd(),
                                PayrollRunStatus.POSTED
                        );

        BigDecimal totalDeductions =
                payrollRunRepository
                        .sumDeductionsByCompanyAndPeriodAndStatus(
                                companyId,
                                period.getPeriodStart(),
                                period.getPeriodEnd(),
                                PayrollRunStatus.POSTED
                        );

        BigDecimal totalNet =
                payrollRunRepository
                        .sumNetByCompanyAndPeriodAndStatus(
                                companyId,
                                period.getPeriodStart(),
                                period.getPeriodEnd(),
                                PayrollRunStatus.POSTED
                        );

        long employeeCount =
                payrollRunRepository
                        .countByEmployee_Department_Company_IdAndPayrollDateBetweenAndStatus(
                                companyId,
                                period.getPeriodStart(),
                                period.getPeriodEnd(),
                                PayrollRunStatus.POSTED
                        );

        execution.setVariable("reconEmployeeCount", employeeCount);
        execution.setVariable("reconGross", totalGross);
        execution.setVariable("reconDeductions", totalDeductions);
        execution.setVariable("reconNet", totalNet);

        log.info("Reconciliation complete: employees={}, gross={}, deductions={}, net={}",
                employeeCount, totalGross, totalDeductions, totalNet);
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