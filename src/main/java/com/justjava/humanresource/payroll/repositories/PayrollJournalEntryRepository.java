package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.payroll.entity.PayrollJournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PayrollJournalEntryRepository
        extends JpaRepository<PayrollJournalEntry, Long> {

    List<PayrollJournalEntry> findByPayrollPeriodId(Long periodId);

    List<PayrollJournalEntry> findByPayrollPeriodIdAndExportedFalse(Long periodId);

    List<PayrollJournalEntry> findByCompanyIdAndPayrollPeriodId(Long companyId, Long periodId);


    List<PayrollJournalEntry> findByCompanyIdAndExportedFalse(Long companyId);

    @Query("""
           SELECT pje
           FROM PayrollJournalEntry pje
           WHERE pje.companyId = :companyId
           AND pje.exported = false
           """)
    List<PayrollJournalEntry> findUnexportedByCompany(
            @Param("companyId") Long companyId
    );

    @Query("""
           SELECT pje
           FROM PayrollJournalEntry pje
           WHERE pje.companyId = :companyId
           AND pje.payrollPeriodId = :periodId
           """)
    List<PayrollJournalEntry> findByCompanyAndPeriod(
            @Param("companyId") Long companyId,
            @Param("periodId") Long periodId
    );
}
