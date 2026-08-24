package com.justjava.humanresource.payroll.workflow.delegates;

import com.justjava.humanresource.payroll.service.PayrollPaymentService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("verifyPayrollSuspenseDelegate")
@RequiredArgsConstructor
public class VerifyPayrollSuspenseDelegate implements JavaDelegate {
    private final PayrollPaymentService paymentService;

    @Override
    public void execute(DelegateExecution execution) {
        paymentService.verifySuspenseClearedForBatch(execution.getProcessInstanceId());
        execution.setVariable("suspenseCleared", true);
    }
}
