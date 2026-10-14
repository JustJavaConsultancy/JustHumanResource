package com.justjava.humanresource.workflow.listener;

import com.justjava.humanresource.utils.AfterCommitExecutor;
import com.justjava.humanresource.utils.PayrollLockEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;


@Slf4j
@Component("financeLockApprovalTaskListener")
@RequiredArgsConstructor
public class FinanceLockApprovalTaskListener implements TaskListener {

    private final PayrollLockEmailService payrollLockEmailService;
    private final AfterCommitExecutor afterCommitExecutor;

    @Override
    public void notify(DelegateTask delegateTask) {
        log.info("Finance lock listener fired for task {} (process instance {}).",
                delegateTask.getId(), delegateTask.getProcessInstanceId());

        Long companyId = toLong(delegateTask.getVariable("companyId"));
        Long periodId = toLong(delegateTask.getVariable("periodId"));
        Long employeeCount = toLong(delegateTask.getVariable("employeeCount"));

        // Same variables the finance "Lock Approval" page displays.
        BigDecimal totalGross = toBigDecimal(delegateTask.getVariable("totalGross"));
        BigDecimal totalDeductions = toBigDecimal(delegateTask.getVariable("totalDeductions"));
        BigDecimal totalNet = toBigDecimal(delegateTask.getVariable("totalNet"));

        if (periodId == null) {
            log.warn("Finance lock listener: 'periodId' variable missing or not numeric on task {} - email skipped.",
                    delegateTask.getId());
            return;
        }

        Integer count = employeeCount != null ? employeeCount.intValue() : null;
        afterCommitExecutor.runAfterCommit(
                () -> payrollLockEmailService.notifyFinanceOfficersPeriodLocked(
                        companyId, periodId, count, totalGross, totalDeductions, totalNet)
        );
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal b) {
            return b;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}