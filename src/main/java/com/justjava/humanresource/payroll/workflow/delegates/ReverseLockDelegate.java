package com.justjava.humanresource.payroll.workflow.delegates;

import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PaySlipRepository;
import com.justjava.humanresource.payroll.repositories.PayrollJournalEntryRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component("reverseLockDelegate")
@RequiredArgsConstructor
public class ReverseLockDelegate implements JavaDelegate {

    private final PayrollPeriodRepository periodRepository;
    private final PayrollJournalEntryRepository journalRepository;
    private final PaySlipRepository paySlipRepository;

    @Override
    @Transactional
    public void execute(DelegateExecution execution) {

        Long periodId = (Long) execution.getVariable("periodId");
        Long companyId = (Long) execution.getVariable("companyId");

        log.info("Reversing lock and cleaning up for period {} and company {}", periodId, companyId);

        // 1. Reset Payroll Period status to OPEN
        PayrollPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalStateException("Payroll period not found for ID: " + periodId));

        if (period.getStatus() == PayrollPeriodStatus.LOCKED || period.getStatus() == PayrollPeriodStatus.CLOSED) {
             period.setStatus(PayrollPeriodStatus.OPEN);
             periodRepository.save(period);
             log.info("Payroll period {} status set back to OPEN", periodId);
        }

        // 2. Delete generated Journal Entries
        var journalEntries = journalRepository.findByPayrollPeriodId(periodId);
        if (!journalEntries.isEmpty()) {
            journalRepository.deleteAll(journalEntries);
            log.info("Deleted {} journal entries for period {}", journalEntries.size(), periodId);
        }

        // 3. Delete generated Payslips
        var paySlips = paySlipRepository.findLatestForCompanyAndPeriod(
                companyId, 
                period.getPeriodStart(), 
                period.getPeriodEnd()
        );
        if (!paySlips.isEmpty()) {
            paySlipRepository.deleteAll(paySlips);
            log.info("Deleted {} payslips for period {}", paySlips.size(), periodId);
        }

        // 4. Reset process variables
        execution.setVariable("journalGenerated", false);
        execution.setVariable("reconEmployeeCount", 0L);
        execution.setVariable("reconGross", null);
        execution.setVariable("reconDeductions", null);
        execution.setVariable("reconNet", null);
        
        log.info("Reversal of lock for period {} completed successfully", periodId);
    }
}
