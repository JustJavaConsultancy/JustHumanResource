package com.justjava.humanresource.workflow.delegate;


import com.justjava.humanresource.payroll.workflow.PayrollOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplyStatutoryDelegate implements JavaDelegate {

    private final PayrollOrchestrationService payrollService;

    @Override
    public void execute(DelegateExecution execution) {

        Long payrollRunId =
                (Long) execution.getVariable("payrollRunId");

        payrollService.applyStatutoryDeductions(payrollRunId);
    }
}
