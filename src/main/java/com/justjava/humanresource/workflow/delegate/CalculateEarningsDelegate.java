package com.justjava.humanresource.workflow.delegate;

import com.justjava.humanresource.core.exception.InvalidOperationException;
import com.justjava.humanresource.payroll.workflow.PayrollOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Slf4j
@Component("CalculateEarningsDelegate")
@RequiredArgsConstructor
public class CalculateEarningsDelegate implements JavaDelegate {
    private final PayrollOrchestrationService payrollService;

    @Override
    public void execute(DelegateExecution execution) {

        Long payrollRunId = getRequiredPayrollRunId(execution);

        log.info("Calculating earnings for payrollRunId={}, processInstanceId={}",
                payrollRunId,
                execution.getProcessInstanceId());

        /*
         * Idempotency Guard:
         * If earnings already calculated (retry scenario),
         * orchestration layer will enforce lifecycle rules.
         */
        payrollService.calculateEarnings(payrollRunId);

        log.info("Earnings calculation completed for payrollRunId={}", payrollRunId);
    }

    private Long getRequiredPayrollRunId(DelegateExecution execution) {

        Object value = execution.getVariable("payrollRunId");

        if (!(value instanceof Long)) {
            throw new InvalidOperationException(
                    "Missing or invalid process variable: payrollRunId"
            );
        }

        return (Long) value;
    }
}
