package com.justjava.humanresource.workflow.delegate;

import com.justjava.humanresource.common.exception.InvalidOperationException;
import com.justjava.humanresource.payroll.workflow.PayrollOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class InitializePayrollDelegate implements JavaDelegate {

    private final PayrollOrchestrationService payrollService;

    @Override
    public void execute(DelegateExecution execution) {

        Long employeeId = getRequiredLong(execution, "employeeId");
        LocalDate payrollDate = getRequiredLocalDate(execution, "payrollDate");

        String processInstanceId = execution.getProcessInstanceId();
        String businessKey = execution.getProcessInstanceBusinessKey();

        log.info("Initializing payroll for employeeId={}, payrollDate={}, businessKey={}",
                employeeId, payrollDate, businessKey);

        /*
         * Idempotency Protection:
         * If payrollRunId already exists (async retry),
         * do not create a second run.
         */
        if (execution.hasVariable("payrollRunId")) {
            log.warn("Payroll already initialized for employeeId={}, skipping duplicate initialization",
                    employeeId);
            return;
        }

        Long payrollRunId = payrollService.initializePayrollRun(
                employeeId,
                payrollDate,
                processInstanceId
        );

        execution.setVariable("payrollRunId", payrollRunId);

        log.info("PayrollRun created with id={}", payrollRunId);
    }

    /* =========================
     * INTERNAL SAFE ACCESSORS
     * ========================= */

    private Long getRequiredLong(DelegateExecution execution, String variableName) {
        Object value = execution.getVariable(variableName);

        if (!(value instanceof Long)) {
            throw new InvalidOperationException(
                    "Missing or invalid process variable: " + variableName
            );
        }
        return (Long) value;
    }

    private LocalDate getRequiredLocalDate(DelegateExecution execution, String variableName) {
        Object value = execution.getVariable(variableName);

        if (!(value instanceof LocalDate)) {
            throw new InvalidOperationException(
                    "Missing or invalid process variable: " + variableName
            );
        }
        return (LocalDate) value;
    }
}
