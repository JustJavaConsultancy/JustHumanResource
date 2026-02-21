package com.justjava.humanresource.workflow.delegate;

import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.service.PayrollAuditService;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("closePeriodDelegate")
@RequiredArgsConstructor
public class ClosePeriodDelegate implements JavaDelegate {

    private final PayrollPeriodService payrollPeriodService;
    private final PayrollPeriodRepository repository;
    private final PayrollAuditService auditService;

    @Override
    public void execute(DelegateExecution execution) {

        Long periodId = (Long) execution.getVariable("periodId");
        String approvedBy = (String) execution.getVariable("approvedBy");

/*
        PayrollPeriod period = repository.findById(periodId)
                .orElseThrow();

        period.setStatus(PayrollPeriodStatus.CLOSED);
        repository.save(period);
*/


        payrollPeriodService.closeCurrentPeriod();
        auditService.log(
                "PayrollPeriod",
                periodId,
                "CLOSE",
                "HR",
                "FINANCE",
                "Period closed via approval workflow"
        );
    }
}
