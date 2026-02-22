package com.justjava.humanresource.workflow.delegate;

import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.service.PayrollJournalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Slf4j
@Component("generateJournalDelegate")
@RequiredArgsConstructor
public class GenerateJournalDelegate implements JavaDelegate {

    private final PayrollPeriodRepository periodRepository;
    private final PayrollJournalService journalService;

    @Override
    public void execute(DelegateExecution execution) {

        Long periodId = getRequiredLong(execution, "periodId");
        Long companyId = getRequiredLong(execution, "companyId");

        PayrollPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() ->
                        new IllegalStateException("Payroll period not found."));

        if (!period.getCompanyId().equals(companyId)) {
            throw new IllegalStateException(
                    "Period does not belong to provided company."
            );
        }

        if (period.getStatus() != PayrollPeriodStatus.LOCKED) {
            throw new IllegalStateException(
                    "Journal can only be generated for LOCKED period."
            );
        }

        log.info("Generating journal entries for company {} period {}",
                companyId, periodId);

        /* ========================================================
           Idempotency Guard (Optional but Recommended)
           ======================================================== */

        if (Boolean.TRUE.equals(execution.getVariable("journalGenerated"))) {
            log.warn("Journal already generated for period {}. Skipping.",
                    periodId);
            return;
        }

        journalService.generateJournalEntries(
                companyId,
                periodId,
                period.getPeriodStart(),
                period.getPeriodEnd()
        );

        execution.setVariable("journalGenerated", true);

        log.info("Journal generation completed for period {}.", periodId);
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