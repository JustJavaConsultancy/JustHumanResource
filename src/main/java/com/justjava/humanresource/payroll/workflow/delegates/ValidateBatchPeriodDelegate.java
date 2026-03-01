package com.justjava.humanresource.payroll.workflow.delegates;

import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("validateBatchPeriodDelegate")
@RequiredArgsConstructor
public class ValidateBatchPeriodDelegate implements JavaDelegate {

    private final PayrollPeriodRepository repository;

    @Override
    public void execute(DelegateExecution execution) {

        Long periodId = (Long) execution.getVariable("periodId");

        PayrollPeriod period = repository.findById(periodId)
                .orElseThrow(() -> new IllegalStateException("Period not found."));

        if (period.getStatus() != PayrollPeriodStatus.OPEN) {
            throw new IllegalStateException(
                    "Batch payroll can only run in OPEN period."
            );
        }

        execution.setVariable("periodStart", period.getPeriodStart());
        execution.setVariable("periodEnd", period.getPeriodEnd());
        execution.setVariable("companyId", period.getCompanyId());
    }
}