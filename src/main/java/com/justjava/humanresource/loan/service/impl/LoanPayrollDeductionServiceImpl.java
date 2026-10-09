package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.loan.entity.EmployeeLoanAccount;
import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.entity.LoanRepaymentSchedule;
import com.justjava.humanresource.loan.enums.LoanAccountStatus;
import com.justjava.humanresource.loan.enums.LoanRepaymentStatus;
import com.justjava.humanresource.loan.repository.EmployeeLoanAccountRepository;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.repository.LoanRepaymentScheduleRepository;
import com.justjava.humanresource.loan.service.EmployeeLoanAccountService;
import com.justjava.humanresource.loan.service.LoanNotificationService;
import com.justjava.humanresource.loan.service.LoanPayrollDeductionService;
import com.justjava.humanresource.payroll.entity.PayrollLineItem;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.enums.PayComponentType;
import com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanPayrollDeductionServiceImpl implements LoanPayrollDeductionService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final EmployeeLoanAccountRepository accounts;
    private final LoanRepaymentScheduleRepository schedules;
    private final EmployeeLoanApplicationRepository applications;
    private final PayrollLineItemRepository lineItems;
    private final EmployeeLoanAccountService accountService;
    private final LoanNotificationService notifications;

    // ------------------------------------------------------------------ calculation phase

    @Override
    @Transactional
    public BigDecimal applyLoanDeductions(PayrollRun run, BigDecimal netBeforeLoan) {
        LocalDate month = payrollMonth(run);

        // Idempotency: drop any loan lines already written for this run, then rebuild them.
        List<PayrollLineItem> stale = loanLines(run.getId());
        if (!stale.isEmpty()) {
            lineItems.deleteAll(stale);
            lineItems.flush();
        }

        BigDecimal available = netBeforeLoan == null ? ZERO : netBeforeLoan.max(ZERO);
        BigDecimal total = ZERO;

        List<EmployeeLoanAccount> employeeAccounts = accounts
                .findByEmployeeIdOrderByCreatedAtDesc(run.getEmployee().getId()).stream()
                .sorted(Comparator.comparing(EmployeeLoanAccount::getId))
                .toList();

        for (EmployeeLoanAccount account : employeeAccounts) {
            if (account.getStatus() == LoanAccountStatus.CLOSED
                    || account.getRepaymentStartMonth().isAfter(month)) {
                continue;
            }
            Optional<LoanRepaymentSchedule> rowOpt =
                    schedules.findByLoanAccountIdAndDueMonth(account.getId(), month);
            if (rowOpt.isEmpty()) {
                continue;
            }
            LoanRepaymentSchedule row = rowOpt.get();

            // paid = already deducted by an earlier run of this month (amendment): carried into this run.
            // extra = what this run can still deduct, limited by the net pay left. MISSED rows get no new deduction.
            BigDecimal paid = row.getPaidAmount();
            BigDecimal extra = row.getStatus() == LoanRepaymentStatus.MISSED
                    ? ZERO
                    : row.getOutstandingAmount().min(available.subtract(paid).max(ZERO));
            BigDecimal amount = paid.add(extra);
            if (amount.signum() <= 0) {
                continue;
            }

            PayrollLineItem line = new PayrollLineItem();
            line.setPayrollRun(run);
            line.setEmployee(run.getEmployee());
            line.setComponentType(PayComponentType.DEDUCTION);
            line.setComponentCode(LINE_CODE_PREFIX + account.getId());
            line.setDescription(describe(account, row));
            line.setAmount(amount);
            line.setTaxable(false);
            line.setPensionable(false);
            line.setPartOfGross(false);
            line.setOutOfPayroll(false);
            lineItems.save(line);

            total = total.add(amount);
            available = available.subtract(amount).max(ZERO);
        }
        return total;
    }

    // ------------------------------------------------------------------ posting phase

    @Override
    @Transactional
    public void recordPostedDeductions(PayrollRun run) {
        LocalDate month = payrollMonth(run);

        for (PayrollLineItem line : loanLines(run.getId())) {
            Long accountId = accountIdOf(line.getComponentCode());
            if (accountId == null) {
                log.warn("Ignoring loan line {} with unreadable code {}", line.getId(), line.getComponentCode());
                continue;
            }
            Optional<LoanRepaymentSchedule> rowOpt = schedules.findByLoanAccountIdAndDueMonth(accountId, month);
            if (rowOpt.isEmpty()) {
                log.warn("Loan line {} has no schedule row for account {} month {}", line.getId(), accountId, month);
                continue;
            }
            LoanRepaymentSchedule row = rowOpt.get();

            // Only the part not yet deducted by an earlier run is applied, so amendments never double count.
            BigDecimal toApply = line.getAmount().subtract(row.getPaidAmount());
            if (toApply.signum() > 0) {
                accountService.applyRepayment(row.getId(), toApply, run.getId(), line.getId());
            }
        }

        flagEarlierMissed(run.getEmployee().getId(), month);
    }

    /** Earlier installments of this employee that were never deducted (e.g. not in that month's payroll). */
    private void flagEarlierMissed(Long employeeId, LocalDate month) {
        List<Long> newlyMissed = new ArrayList<>();
        for (EmployeeLoanAccount account : accounts.findByEmployeeIdAndStatus(employeeId, LoanAccountStatus.ACTIVE)) {
            List<LoanRepaymentSchedule> open = new ArrayList<>(
                    schedules.findByLoanAccountIdAndStatus(account.getId(), LoanRepaymentStatus.PENDING));
            open.addAll(schedules.findByLoanAccountIdAndStatus(account.getId(), LoanRepaymentStatus.PARTIALLY_PAID));
            for (LoanRepaymentSchedule row : open) {
                if (row.getDueMonth().isBefore(month) && accountService.markMissed(row.getId())) {
                    newlyMissed.add(row.getId());
                }
            }
        }
        // One digest for everything this posting newly flagged; rows that were already MISSED are not included.
        if (!newlyMissed.isEmpty()) {
            notifications.notifyMissedDeductions(newlyMissed);
        }
    }

    @Override
    @Transactional
    public int flagMissedDeductions(LocalDate month) {
        LocalDate first = month.withDayOfMonth(1);
        List<LoanRepaymentSchedule> rows = schedules.findByDueMonthAndStatusIn(
                first, List.of(LoanRepaymentStatus.PENDING, LoanRepaymentStatus.PARTIALLY_PAID));
        List<Long> newlyMissed = new ArrayList<>();
        for (LoanRepaymentSchedule r : rows) {
            if (accountService.markMissed(r.getId())) {
                newlyMissed.add(r.getId());
            }
        }
        // One digest per period-close run, listing only the rows flagged by this call.
        if (!newlyMissed.isEmpty()) {
            notifications.notifyMissedDeductions(newlyMissed);
        }
        return rows.size();
    }

    // ------------------------------------------------------------------ helpers

    private List<PayrollLineItem> loanLines(Long runId) {
        return lineItems.findByPayrollRunIdAndComponentType(runId, PayComponentType.DEDUCTION).stream()
                .filter(l -> l.getComponentCode() != null && l.getComponentCode().startsWith(LINE_CODE_PREFIX))
                .toList();
    }

    private static Long accountIdOf(String code) {
        try {
            return Long.valueOf(code.substring(LINE_CODE_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate payrollMonth(PayrollRun run) {
        return YearMonth.from(run.getPayrollDate()).atDay(1);
    }

    private String describe(EmployeeLoanAccount account, LoanRepaymentSchedule row) {
        String number = applications.findById(account.getLoanApplicationId())
                .map(EmployeeLoanApplication::getApplicationNumber)
                .orElse("#" + account.getId());
        return "Loan Repayment " + number + " (installment " + row.getSequenceNumber() + ")";
    }
}