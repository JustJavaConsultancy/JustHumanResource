package com.justjava.humanresource.workflow.delegate;

import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.service.PayrollAuditService;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Slf4j
@Component("closePeriodDelegate")
@RequiredArgsConstructor
public class ClosePeriodDelegate implements JavaDelegate {

    private final PayrollPeriodService payrollPeriodService;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollAuditService auditService;

    @Override
    public void execute(DelegateExecution execution) {

        Long periodId = getRequiredLong(execution, "periodId");
        Long companyId = getRequiredLong(execution, "companyId");
        String approvedBy = (String) execution.getVariable("approvedBy");

        PayrollPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() ->
                        new IllegalStateException("Payroll period not found."));

/*        if (!period.getCompanyId().equals(companyId)) {
            throw new IllegalStateException(
                    "Period does not belong to provided company."
            );
        }*/

        if (period.getStatus() != PayrollPeriodStatus.OPEN
                && period.getStatus() != PayrollPeriodStatus.LOCKED) {

            throw new IllegalStateException(
                    "Only OPEN or LOCKED period can be closed."
            );
        }

        log.info("Closing payroll period {} for company {}",
                periodId, companyId);

        /* ============================================================
           CLOSE AND OPEN NEXT PERIOD (SERVICE CONTROLLED)
           ============================================================ */

        payrollPeriodService.closeAndOpenNext(period.getCompanyId());

        /* ============================================================
           AUDIT TRAIL
           ============================================================ */

        auditService.log(
                "PayrollPeriod",
                periodId,
                "CLOSE",
                approvedBy != null ? approvedBy : "SYSTEM",
                "FINANCE",
                "Period closed via approval workflow"
        );

        log.info("Payroll period {} successfully closed.", periodId);
    }

    private Long getRequiredLong(
            DelegateExecution execution,
            String variableName) {

        Object value = execution.getVariable(variableName);

        if (!(value instanceof Long)) {
            throw new IllegalStateException(
                    "Missing or invalid process variable: " + variableName
            );
        }

        return (Long) value;
    }
}