package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.entity.PayrollJournalEntry;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollJournalEntryRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollJournalService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollJournalEntryRepository journalRepository;

    private final PayrollPeriodRepository payrollPeriodRepository;
    @Transactional
    public void generateJournalEntries(
            Long companyId,
            Long periodId,
            LocalDate start,
            LocalDate end) {

    /* ============================================================
       1️⃣ VALIDATE PERIOD OWNERSHIP & STATUS
       ============================================================ */

        PayrollPeriod period = payrollPeriodRepository.findById(periodId)
                .orElseThrow(() ->
                        new IllegalStateException("Payroll period not found."));

        if (!period.getCompanyId().equals(companyId)) {
            throw new IllegalStateException(
                    "Period does not belong to provided company."
            );
        }

//        if (period.getStatus() != PayrollPeriodStatus.LOCKED) {
//            throw new IllegalStateException(
//                    "Journal can only be generated for LOCKED period."
//            );
//        }

    /* ============================================================
       2️⃣ IDEMPOTENCY CHECK (COMPANY + PERIOD)
       ============================================================ */

        List<PayrollJournalEntry> existing =
                journalRepository
                        .findByCompanyIdAndPayrollPeriodId(companyId, periodId);

        if (!existing.isEmpty()) {
            throw new IllegalStateException(
                    "Journal entries already exist for this period.");
        }

    /* ============================================================
       3️⃣ ENSURE POSTED RUNS EXIST (COMPANY SCOPED)
       ============================================================ */

        long employeeCount =
                payrollRunRepository
                        .countByEmployee_Department_Company_IdAndPayrollDateBetweenAndStatus(
                                companyId,
                                period.getPeriodStart(),
                                period.getPeriodEnd(),
                                PayrollRunStatus.POSTED
                        );

        if (employeeCount == 0) {
            throw new IllegalStateException(
                    "No POSTED payroll runs found for period.");
        }

    /* ============================================================
       4️⃣ COMPANY-SCOPED AGGREGATION
       ============================================================ */

        BigDecimal totalGross =
                payrollRunRepository
                        .sumGrossByCompanyAndPeriodAndStatus(
                                companyId,
                                period.getPeriodStart(),
                                period.getPeriodEnd(),
                                PayrollRunStatus.POSTED
                        );

        BigDecimal totalDeductions =
                payrollRunRepository
                        .sumDeductionsByCompanyAndPeriodAndStatus(
                                companyId,
                                period.getPeriodStart(),
                                period.getPeriodEnd(),
                                PayrollRunStatus.POSTED
                        );

        BigDecimal totalNet =
                payrollRunRepository
                        .sumNetByCompanyAndPeriodAndStatus(
                                companyId,
                                period.getPeriodStart(),
                                period.getPeriodEnd(),
                                PayrollRunStatus.POSTED
                        );

        totalGross = safe(totalGross);
        totalDeductions = safe(totalDeductions);
        totalNet = safe(totalNet);

    /* ============================================================
       5️⃣ FINANCIAL BALANCE VALIDATION
       ============================================================ */

        if (totalGross.compareTo(totalDeductions.add(totalNet)) != 0) {
            throw new IllegalStateException(
                    "Journal imbalance detected. Payroll snapshot corrupted.");
        }

    /* ============================================================
       6️⃣ CREATE BALANCED JOURNAL ENTRIES
       ============================================================ */

        saveEntry(companyId, periodId,
                "5000",
                "Salary Expense",
                totalGross,
                BigDecimal.ZERO,
                "Payroll salary expense");

        saveEntry(companyId, periodId,
                "2100",
                "Payroll Deductions Payable",
                BigDecimal.ZERO,
                totalDeductions,
                "Payroll deductions liability");

        saveEntry(companyId, periodId,
                "2000",
                "Salary Payable",
                BigDecimal.ZERO,
                totalNet,
                "Net salary payable");
    }
    /* ============================================================
       SAFE NULL HANDLER
       ============================================================ */

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /* ============================================================
       ENTRY CREATION
       ============================================================ */

    private void saveEntry(Long comppanyId,Long periodId,
                           String accountCode,
                           String accountName,
                           BigDecimal debit,
                           BigDecimal credit,
                           String description) {

        PayrollJournalEntry entry = new PayrollJournalEntry();
        entry.setCompanyId(comppanyId);
        entry.setPayrollPeriodId(periodId);
        entry.setAccountCode(accountCode);
        entry.setAccountName(accountName);
        entry.setDebitAmount(debit);
        entry.setCreditAmount(credit);
        entry.setDescription(description);
        entry.setExported(false);

        journalRepository.save(entry);
    }
}
