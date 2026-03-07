package com.justjava.humanresource.workflow.delegate;


import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.PaySlipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component("generatePayslipsDelegate")
@RequiredArgsConstructor
public class GeneratePayslipsDelegate implements JavaDelegate {

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PaySlipService paySlipService;

    @Override
    @Transactional
    public void execute(DelegateExecution execution) {

        Long periodId = getRequiredLong(execution, "periodId");

        PayrollPeriod period = payrollPeriodRepository.findById(periodId)
                .orElseThrow(() ->
                        new IllegalStateException("Payroll period not found"));

        log.info(
                "Generating payslips for company {} period {} -> {}",
                period.getCompanyId(),
                period.getPeriodStart(),
                period.getPeriodEnd()
        );

        /*
         * Fetch POSTED payroll runs
         */
        var runs =
                payrollRunRepository
                        .findByEmployee_Department_Company_IdAndPayrollDateBetweenAndStatus(
                                period.getCompanyId(),
                                period.getPeriodStart(),
                                period.getPeriodEnd(),
                                PayrollRunStatus.POSTED
                        );

        int generated = 0;

        for (var run : runs) {

            /*
             * Payslip service must be idempotent
             */
            if (!paySlipService.existsForPayrollRun(run.getId())) {

                paySlipService.generatePaySlip(run.getId());
                generated++;
            }
        }

        log.info(
                "Payslip generation completed. {} payslips created.",
                generated
        );
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