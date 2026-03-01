package com.justjava.humanresource.dispatcher.impl;

import com.justjava.humanresource.dispatcher.PayrollMessageDispatcher;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import com.justjava.humanresource.payroll.workflow.EmployeePayrollProcessManager;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayrollMessageDispatcherImpl
        implements PayrollMessageDispatcher {

    private final RuntimeService runtimeService;
    private final EmployeePayrollProcessManager processManager;
    private final PayrollPeriodService payrollPeriodService;

    /* ============================================================
       SINGLE EMPLOYEE PAYROLL
       ============================================================ */

    @Override
    public void requestPayroll(
            Long employeeId,
            LocalDate effectiveDate) {

        processManager.ensureProcessStarted(employeeId);

        String businessKey = employeeBusinessKey(employeeId);

        ProcessInstance processInstance = runtimeService
                .createProcessInstanceQuery()
                .processDefinitionKey("employeePayrollSupervisor")
                .processInstanceBusinessKey(businessKey)
                .singleResult();

        if (processInstance == null) {
            throw new IllegalStateException(
                    "Payroll supervisor process not found for employee: "
                            + employeeId
            );
        }

        Execution execution = runtimeService
                .createExecutionQuery()
                .processInstanceId(processInstance.getId())
                .messageEventSubscriptionName("PAYROLL_MESSAGE")
                .singleResult();

        if (execution == null) {
            throw new IllegalStateException(
                    "No waiting execution found for businessKey: "
                            + businessKey
            );
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("employeeId", employeeId);
        vars.put("payrollDate", effectiveDate);
        vars.put("approvalRequired", false);
        vars.put("exit", false);

        runtimeService.messageEventReceived(
                "PAYROLL_MESSAGE",
                execution.getId(),
                vars
        );
    }

    /* ============================================================
       BATCH PAYROLL PROCESS
       ============================================================ */

    @Override
    public void requestBatchPayroll(Long companyId) {

        PayrollPeriod period = payrollPeriodService.getOpenPeriod(companyId);


        if (period.getStatus() != PayrollPeriodStatus.OPEN) {
            throw new IllegalStateException(
                    "Batch payroll can only run in OPEN period."
            );
        }

        String businessKey = batchBusinessKey(period.getId());

        // Prevent duplicate active batch process
        ProcessInstance existing = runtimeService
                .createProcessInstanceQuery()
                .processDefinitionKey("batchPayrollProcess")
                .processInstanceBusinessKey(businessKey)
                .active()
                .singleResult();

        if (existing != null) {
            throw new IllegalStateException(
                    "Batch payroll already running for period: "
                            + period.getId()
            );
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("periodId", period.getId());
        vars.put("companyId", period.getCompanyId());
        vars.put("periodStart", period.getPeriodStart());
        vars.put("periodEnd", period.getPeriodEnd());

        runtimeService.startProcessInstanceByKey(
                "batchPayrollProcess",
                businessKey,
                vars
        );
    }

    /* ============================================================
       BUSINESS KEYS
       ============================================================ */

    private String employeeBusinessKey(Long employeeId) {
        return "EMPLOYEE_" + employeeId;
    }

    private String batchBusinessKey(Long periodId) {
        return "BATCH_PERIOD_" + periodId;
    }
}