package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.loan.dto.LoanRepaymentPreviewResponse;
import com.justjava.humanresource.loan.dto.LoanRepaymentScheduleLineResponse;
import com.justjava.humanresource.loan.entity.EmployeeLoanAccount;
import com.justjava.humanresource.loan.entity.EmployeeLoanApplication;
import com.justjava.humanresource.loan.entity.LoanRepaymentSchedule;
import com.justjava.humanresource.loan.entity.LoanRepaymentTransaction;
import com.justjava.humanresource.loan.enums.LoanAccountStatus;
import com.justjava.humanresource.loan.enums.LoanActivityType;
import com.justjava.humanresource.loan.enums.LoanApplicationStatus;
import com.justjava.humanresource.loan.enums.LoanRepaymentStatus;
import com.justjava.humanresource.loan.enums.LoanRepaymentTransactionType;
import com.justjava.humanresource.loan.repository.EmployeeLoanAccountRepository;
import com.justjava.humanresource.loan.repository.EmployeeLoanApplicationRepository;
import com.justjava.humanresource.loan.repository.LoanRepaymentScheduleRepository;
import com.justjava.humanresource.loan.repository.LoanRepaymentTransactionRepository;
import com.justjava.humanresource.loan.service.EmployeeLoanAccountService;
import com.justjava.humanresource.loan.service.LoanActivityService;
import com.justjava.humanresource.loan.service.LoanRepaymentCalculationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeLoanAccountServiceImpl implements EmployeeLoanAccountService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final EmployeeLoanApplicationRepository applications;
    private final EmployeeLoanAccountRepository accounts;
    private final LoanRepaymentScheduleRepository schedules;
    private final LoanRepaymentTransactionRepository transactions;
    private final LoanRepaymentCalculationService calculationService;
    private final LoanActivityService activityService;

    // ------------------------------------------------------------------ activation

    @Override
    @Transactional
    public EmployeeLoanAccount activate(Long loanApplicationId) {
        EmployeeLoanApplication app = applications.findById(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException("Loan application not found: " + loanApplicationId));

        Optional<EmployeeLoanAccount> existing = accounts.findByLoanApplicationId(loanApplicationId);
        if (existing.isPresent()) {
            return repairIfPartial(app, existing.get());
        }

        if (app.getStatus() != LoanApplicationStatus.FINANCE_APPROVED
                && app.getStatus() != LoanApplicationStatus.CUSTOM_APPROVED) {
            throw new IllegalStateException("Loan " + app.getApplicationNumber()
                    + " cannot be activated from status " + app.getStatus() + ".");
        }

        // Snapshotted terms only: later product edits or a late approval cannot change the schedule.
        LoanRepaymentPreviewResponse calc = calculationService.calculate(
                app.getInterestTypeSnapshot(), app.getInterestRateSnapshot(),
                app.getRequestedAmount(), app.getRepaymentAmount(),
                app.getTenorMonths(), app.getRepaymentStartMonth());
        if (!calc.isValid()) {
            throw new IllegalStateException("Loan " + app.getApplicationNumber()
                    + " has invalid snapshotted terms: " + String.join(" ", calc.getErrors()));
        }
        if (app.getTotalRepayableAmount() != null
                && app.getTotalRepayableAmount().compareTo(calc.getTotalRepayableAmount()) != 0) {
            log.warn("Loan {}: total at submission {} differs from activation total {}; using activation total.",
                    app.getApplicationNumber(), app.getTotalRepayableAmount(), calc.getTotalRepayableAmount());
        }

        LocalDateTime now = LocalDateTime.now();

        EmployeeLoanAccount account = new EmployeeLoanAccount();
        account.setLoanApplicationId(app.getId());
        account.setLoanProductId(app.getLoanProduct().getId());
        account.setEmployeeId(app.getEmployee().getId());
        account.setPrincipalAmount(calc.getRequestedAmount());
        account.setInterestAmount(calc.getTotalInterestAmount());
        account.setTotalRepayableAmount(calc.getTotalRepayableAmount());
        account.setTotalPaidAmount(ZERO);
        account.setOutstandingBalance(calc.getTotalRepayableAmount());
        account.setRepaymentAmount(calc.getRepaymentAmount());
        account.setTenorMonths(calc.getTenorMonths());
        account.setRepaymentStartMonth(calc.getRepaymentStartMonth());
        account.setStatus(LoanAccountStatus.ACTIVE);
        account.setActivatedAt(now);
        account = accounts.save(account);

        schedules.saveAll(buildSchedule(account.getId(), calc.getLines()));

        app.setStatus(LoanApplicationStatus.ACTIVE);
        app.setActivatedAt(now);
        applications.save(app);

        activityService.recordForAccount(app.getId(), account.getId(), LoanActivityType.LOAN_ACTIVATED,
                "Loan activated. Principal " + calc.getRequestedAmount().toPlainString()
                        + ", total repayable " + calc.getTotalRepayableAmount().toPlainString()
                        + ", " + calc.getNumberOfInstallments() + " installment(s) of "
                        + calc.getRepaymentAmount().toPlainString() + " from " + calc.getRepaymentStartMonth() + ".",
                null);
        return account;
    }

    /** Retry safety: account exists but the schedule or application status was not completed. */
    private EmployeeLoanAccount repairIfPartial(EmployeeLoanApplication app, EmployeeLoanAccount account) {
        if (!schedules.existsByLoanAccountId(account.getId())) {
            LoanRepaymentPreviewResponse calc = calculationService.calculate(
                    app.getInterestTypeSnapshot(), app.getInterestRateSnapshot(),
                    app.getRequestedAmount(), app.getRepaymentAmount(),
                    app.getTenorMonths(), app.getRepaymentStartMonth());
            if (!calc.isValid()) {
                throw new IllegalStateException("Loan " + app.getApplicationNumber()
                        + " has invalid snapshotted terms: " + String.join(" ", calc.getErrors()));
            }
            schedules.saveAll(buildSchedule(account.getId(), calc.getLines()));
        }
        if (app.getStatus() == LoanApplicationStatus.FINANCE_APPROVED
                || app.getStatus() == LoanApplicationStatus.CUSTOM_APPROVED) {
            app.setStatus(LoanApplicationStatus.ACTIVE);
            if (app.getActivatedAt() == null) {
                app.setActivatedAt(account.getActivatedAt() != null ? account.getActivatedAt() : LocalDateTime.now());
            }
            applications.save(app);
        }
        return account;
    }

    private List<LoanRepaymentSchedule> buildSchedule(Long accountId, List<LoanRepaymentScheduleLineResponse> lines) {
        List<LoanRepaymentSchedule> rows = new ArrayList<>(lines.size());
        for (LoanRepaymentScheduleLineResponse line : lines) {
            LoanRepaymentSchedule row = new LoanRepaymentSchedule();
            row.setLoanAccountId(accountId);
            row.setSequenceNumber(line.getSequenceNumber());
            row.setDueMonth(line.getDueMonth());
            row.setExpectedAmount(line.getExpectedAmount());
            row.setPrincipalPortion(line.getPrincipalPortion());
            row.setInterestPortion(line.getInterestPortion());
            row.setPaidAmount(ZERO);
            row.setOutstandingAmount(line.getExpectedAmount());
            row.setStatus(LoanRepaymentStatus.PENDING);
            rows.add(row);
        }
        return rows;
    }

    // ------------------------------------------------------------------ queries

    @Override
    public List<EmployeeLoanAccount> getActiveLoansByEmployee(Long employeeId) {
        return accounts.findByEmployeeIdAndStatus(employeeId, LoanAccountStatus.ACTIVE).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
    }

    @Override
    public BigDecimal getOutstandingBalance(Long employeeId) {
        return accounts.findByEmployeeIdAndStatus(employeeId, LoanAccountStatus.ACTIVE).stream()
                .map(EmployeeLoanAccount::getOutstandingBalance)
                .reduce(ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------ repayment

    @Override
    @Transactional
    public LoanRepaymentTransaction applyRepayment(Long repaymentScheduleId, BigDecimal amount,
                                                   Long payrollRunId, Long payrollLineItemId) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Repayment amount must be greater than zero.");
        }
        BigDecimal paid = amount.setScale(2, RoundingMode.HALF_UP);

        LoanRepaymentSchedule row = schedules.findById(repaymentScheduleId)
                .orElseThrow(() -> new EntityNotFoundException("Repayment schedule row not found: " + repaymentScheduleId));

        if (payrollRunId != null) {
            Optional<LoanRepaymentTransaction> already =
                    transactions.findByRepaymentScheduleIdAndPayrollRunId(repaymentScheduleId, payrollRunId);
            if (already.isPresent()) {
                return already.get(); // payroll retry / recalculation
            }
        }

        EmployeeLoanAccount account = accounts.findById(row.getLoanAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Loan account not found: " + row.getLoanAccountId()));
        if (account.getStatus() != LoanAccountStatus.ACTIVE) {
            throw new IllegalStateException("Loan account " + account.getId() + " is " + account.getStatus() + ".");
        }
        if (row.getStatus() == LoanRepaymentStatus.PAID) {
            throw new IllegalStateException("Installment " + row.getSequenceNumber() + " is already fully paid.");
        }
        if (paid.compareTo(row.getOutstandingAmount()) > 0) {
            throw new IllegalArgumentException("Repayment " + paid.toPlainString()
                    + " exceeds the installment outstanding amount " + row.getOutstandingAmount().toPlainString() + ".");
        }

        LocalDateTime now = LocalDateTime.now();

        row.setPaidAmount(row.getPaidAmount().add(paid));
        row.setOutstandingAmount(row.getOutstandingAmount().subtract(paid));
        row.setStatus(row.getOutstandingAmount().signum() == 0
                ? LoanRepaymentStatus.PAID : LoanRepaymentStatus.PARTIALLY_PAID);
        row.setPayrollRunId(payrollRunId);
        row.setPayrollLineItemId(payrollLineItemId);
        row.setDeductedAt(now);
        row.setMissedAt(null);
        schedules.save(row);

        LoanRepaymentTransaction txn = new LoanRepaymentTransaction();
        txn.setLoanAccountId(account.getId());
        txn.setRepaymentScheduleId(row.getId());
        txn.setEmployeeId(account.getEmployeeId());
        txn.setPayrollRunId(payrollRunId);
        txn.setPayrollLineItemId(payrollLineItemId);
        txn.setAmount(paid);
        txn.setTransactionMonth(row.getDueMonth());
        txn.setTransactionType(LoanRepaymentTransactionType.PAYROLL_DEDUCTION);
        txn = transactions.save(txn);

        account.setTotalPaidAmount(account.getTotalPaidAmount().add(paid));
        BigDecimal outstanding = account.getTotalRepayableAmount().subtract(account.getTotalPaidAmount());
        account.setOutstandingBalance(outstanding.signum() < 0 ? ZERO : outstanding);

        activityService.recordForAccount(account.getLoanApplicationId(), account.getId(),
                LoanActivityType.PAYROLL_DEDUCTION_APPLIED,
                "Payroll deduction of " + paid.toPlainString() + " applied to installment "
                        + row.getSequenceNumber() + " (" + row.getDueMonth() + ").", null);

        if (account.getOutstandingBalance().signum() == 0) {
            account.setStatus(LoanAccountStatus.COMPLETED);
            account.setCompletedAt(now);
            applications.findById(account.getLoanApplicationId()).ifPresent(app -> {
                app.setStatus(LoanApplicationStatus.COMPLETED);
                applications.save(app);
            });
            activityService.recordForAccount(account.getLoanApplicationId(), account.getId(),
                    LoanActivityType.LOAN_COMPLETED, "Loan fully repaid.", null);
        }
        accounts.save(account);
        return txn;
    }

    @Override
    @Transactional
    public void markMissed(Long repaymentScheduleId) {
        LoanRepaymentSchedule row = schedules.findById(repaymentScheduleId)
                .orElseThrow(() -> new EntityNotFoundException("Repayment schedule row not found: " + repaymentScheduleId));
        if (row.getStatus() != LoanRepaymentStatus.PENDING
                && row.getStatus() != LoanRepaymentStatus.PARTIALLY_PAID) {
            return; // PAID or already MISSED
        }
        EmployeeLoanAccount account = accounts.findById(row.getLoanAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Loan account not found: " + row.getLoanAccountId()));
        if (account.getStatus() != LoanAccountStatus.ACTIVE) {
            return;
        }
        row.setStatus(LoanRepaymentStatus.MISSED);
        row.setMissedAt(LocalDateTime.now());
        schedules.save(row);

        activityService.recordForAccount(account.getLoanApplicationId(), account.getId(),
                LoanActivityType.PAYROLL_DEDUCTION_MISSED,
                "Installment " + row.getSequenceNumber() + " (" + row.getDueMonth()
                        + ") was due but not deducted. Outstanding " + row.getOutstandingAmount().toPlainString() + ".",
                null);
    }
}
