package com.justjava.humanresource.workflow.delegate;


import com.justjava.humanresource.core.exception.InvalidOperationException;
import com.justjava.humanresource.payroll.workflow.PayrollOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component("ApplyOtherDeductionsDelegate")
@RequiredArgsConstructor
public class ApplyOtherDeductionsDelegate implements JavaDelegate {

    private final PayrollOrchestrationService payrollService;

    @Override
    @Transactional
    public void execute(DelegateExecution execution) {

        Long payrollRunId = getRequiredPayrollRunId(execution);

        log.info("Applying OTHER deductions for payrollRunId={}, processInstanceId={}, businessKey={}",
                payrollRunId,
                execution.getProcessInstanceId(),
                execution.getProcessInstanceBusinessKey());

        /*
         * IMPORTANT:
         * This method must be idempotent because Flowable retries async jobs.
         * Your service already deletes previous deduction lines before re-applying.
         */
        payrollService.applyOtherDeductions(payrollRunId);

        log.info("Other deductions applied successfully for payrollRunId={}", payrollRunId);
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