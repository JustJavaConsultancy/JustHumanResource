package com.justjava.humanresource.workflow.delegate;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.core.exception.InvalidOperationException;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.workflow.PayrollOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component("FinalizePayrollDelegate")
@RequiredArgsConstructor
public class FinalizePayrollDelegate implements JavaDelegate {

    private final PayrollOrchestrationService payrollService;
    private final PayrollRunRepository payrollRunRepository;

    @Override
    @Transactional
    public void execute(DelegateExecution execution) {

        Long payrollRunId = getRequiredPayrollRunId(execution);

        log.info(
                "Finalizing payroll for payrollRunId={}, processInstanceId={}, businessKey={}",
                payrollRunId,
                execution.getProcessInstanceId(),
                execution.getProcessInstanceBusinessKey()
        );

        PayrollRun run = payrollRunRepository.findById(payrollRunId)
                .orElseThrow(() ->
                        new InvalidOperationException(
                                "PayrollRun not found: " + payrollRunId
                        ));

        /*
         * Idempotency Guard
         */
        if (run.getStatus() == PayrollRunStatus.POSTED) {

            log.warn(
                    "PayrollRun {} already POSTED. Skipping duplicate finalization.",
                    payrollRunId
            );

            return;
        }

        payrollService.finalizePayroll(payrollRunId);

        log.info("PayrollRun {} finalized successfully.", payrollRunId);
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