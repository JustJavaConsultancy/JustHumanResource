package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.entity.PayrollJournalEntry;
import com.justjava.humanresource.payroll.repositories.PayrollJournalEntryRepository;
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

    @Transactional
    public void generateJournalEntries(Long periodId,
                                       LocalDate start,
                                       LocalDate end) {

        /* ============================================================
           1️⃣ IDEMPOTENCY CHECK
           Prevent duplicate journal generation
           ============================================================ */

        List<PayrollJournalEntry> existing =
                journalRepository.findByPayrollPeriodId(periodId);

        if (!existing.isEmpty()) {
            throw new IllegalStateException(
                    "Journal entries already exist for this period.");
        }

        /* ============================================================
           2️⃣ ENSURE THERE ARE POSTED RUNS
           ============================================================ */

        long employeeCount =
                payrollRunRepository.countByPayrollDateBetweenAndStatus(
                        start,
                        end,
                        PayrollRunStatus.POSTED
                );

        if (employeeCount == 0) {
            throw new IllegalStateException(
                    "No POSTED payroll runs found for period.");
        }

        /* ============================================================
           3️⃣ DATABASE-LEVEL AGGREGATION (OPTIMIZED)
           ============================================================ */

        BigDecimal totalGross =
                payrollRunRepository.sumGrossByPeriodAndStatus(
                        start,
                        end,
                        PayrollRunStatus.POSTED
                );

        BigDecimal totalDeductions =
                payrollRunRepository.sumDeductionsByPeriodAndStatus(
                        start,
                        end,
                        PayrollRunStatus.POSTED
                );

        BigDecimal totalNet =
                payrollRunRepository.sumNetByPeriodAndStatus(
                        start,
                        end,
                        PayrollRunStatus.POSTED
                );

        totalGross = safe(totalGross);
        totalDeductions = safe(totalDeductions);
        totalNet = safe(totalNet);

        /* ============================================================
           4️⃣ FINANCIAL BALANCE VALIDATION
           Gross = Deductions + Net
           ============================================================ */

        if (totalGross.compareTo(totalDeductions.add(totalNet)) != 0) {
            throw new IllegalStateException(
                    "Journal imbalance detected. Payroll snapshot corrupted.");
        }

        /* ============================================================
           5️⃣ CREATE BALANCED ACCOUNTING ENTRIES
           ============================================================ */

        saveEntry(periodId,
                "5000",
                "Salary Expense",
                totalGross,
                BigDecimal.ZERO,
                "Payroll salary expense");

        saveEntry(periodId,
                "2100",
                "Payroll Deductions Payable",
                BigDecimal.ZERO,
                totalDeductions,
                "Payroll deductions liability");

        saveEntry(periodId,
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

    private void saveEntry(Long periodId,
                           String accountCode,
                           String accountName,
                           BigDecimal debit,
                           BigDecimal credit,
                           String description) {

        PayrollJournalEntry entry = new PayrollJournalEntry();
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
