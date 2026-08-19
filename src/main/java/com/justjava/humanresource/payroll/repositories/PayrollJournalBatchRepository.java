package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.payroll.entity.PayrollJournalBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollJournalBatchRepository
        extends JpaRepository<PayrollJournalBatch, Long> {

    Optional<PayrollJournalBatch> findByCompanyIdAndPayrollPeriodId(Long companyId, Long payrollPeriodId);

    List<PayrollJournalBatch> findByCompanyIdOrderByPeriodStartDesc(Long companyId);
}
