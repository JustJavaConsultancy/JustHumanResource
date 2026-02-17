package com.justjava.humanresource.workflow.delegate;

import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.service.PayrollJournalService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("generateJournalDelegate")
@RequiredArgsConstructor
public class GenerateJournalDelegate implements JavaDelegate {

    private final PayrollPeriodRepository periodRepository;
    private final PayrollJournalService journalService;

    @Override
    public void execute(DelegateExecution execution) {

        Long periodId = (Long) execution.getVariable("periodId");

        PayrollPeriod period = periodRepository.findById(periodId)
                .orElseThrow();

        journalService.generateJournalEntries(
                periodId,
                period.getStartDate(),
                period.getEndDate()
        );
    }
}
