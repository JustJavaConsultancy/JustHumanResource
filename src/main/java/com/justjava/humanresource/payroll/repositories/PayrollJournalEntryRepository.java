package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.payroll.entity.PayrollJournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollJournalEntryRepository
        extends JpaRepository<PayrollJournalEntry, Long> {

    List<PayrollJournalEntry> findByPayrollPeriodId(Long periodId);

    List<PayrollJournalEntry> findByPayrollPeriodIdAndExportedFalse(Long periodId);
}
