package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.core.exception.ResourceNotFoundException;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.loan.entity.LoanDisbursement;
import com.justjava.humanresource.loan.enums.LoanDisbursementMethod;
import com.justjava.humanresource.loan.enums.LoanDisbursementStatus;
import com.justjava.humanresource.loan.repository.LoanDisbursementRepository;
import com.justjava.humanresource.loan.service.LoanPayrollDisbursementService;
import com.justjava.humanresource.payroll.entity.PayrollLineItem;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.enums.PayComponentType;
import com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanPayrollDisbursementServiceImpl implements LoanPayrollDisbursementService {

    public static final String CODE_PREFIX = "LOAN_DISBURSEMENT_";

    /** Matches no real payroll period, so the "scheduled, unmapped" branch selects nothing. */
    private static final long NO_PAYROLL_PERIOD_ID = -1L;

    private final LoanDisbursementRepository loanDisbursementRepository;
    private final PayrollLineItemRepository payrollLineItemRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollPeriodService payrollPeriodService;
    private final EmployeeRepository employeeRepository;

    /* ============================================================
       APPLY (during calculateEarnings)
       ============================================================ */

    @Override
    @Transactional
    public BigDecimal applyLoanDisbursements(PayrollRun run) {

        if (run.getStatus() == PayrollRunStatus.POSTED) {
            throw new IllegalStateException("POSTED payroll cannot be modified.");
        }

        // Idempotency: never keep or duplicate a disbursement line on recalculation.
        payrollLineItemRepository.deleteLoanDisbursementLines(run.getId());

        List<LoanDisbursement> disbursements = findDisbursementsForRun(run);
        BigDecimal total = BigDecimal.ZERO;

        for (LoanDisbursement d : disbursements) {

            BigDecimal amount = d.getAmount();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            amount = amount.setScale(2, RoundingMode.HALF_UP);

            PayrollLineItem line = new PayrollLineItem();
            line.setPayrollRun(run);
            line.setEmployee(run.getEmployee());
            line.setComponentType(PayComponentType.EARNING);
            line.setComponentCode(CODE_PREFIX + d.getId());
            // TODO: swap for the loan application number if EmployeeLoanApplication exposes one
            line.setDescription("Loan Disbursement #" + d.getLoanApplicationId());
            line.setAmount(amount);
            line.setTaxable(false);
            line.setPensionable(false);
            line.setPartOfGross(false);
            line.setOutOfPayroll(false);
            payrollLineItemRepository.save(line);

            total = total.add(amount);
            log.info("Loan disbursement {} added to payroll run {} ({})",
                    d.getId(), run.getId(), amount);
        }

        return total;
    }

    /* ============================================================
       POSTING (during finalizePayroll)
       ============================================================ */

    @Override
    @Transactional
    public void markPayrollDisbursementsPosted(PayrollRun run) {

        for (LoanDisbursement d : findDisbursementsForRun(run)) {

            Optional<PayrollLineItem> lineOpt =
                    payrollLineItemRepository.findFirstByPayrollRunIdAndComponentCode(
                            run.getId(), CODE_PREFIX + d.getId());

            if (lineOpt.isEmpty()) {
                continue; // not part of this run
            }

            d.setPayrollRunId(run.getId());
            d.setPayrollLineItemId(lineOpt.get().getId());
            if (run.getPeriodStart() != null) {
                d.setDisbursementMonth(run.getPeriodStart().withDayOfMonth(1));
            }
            d.setStatus(LoanDisbursementStatus.PAID);
            if (d.getPaidAt() == null) {
                d.setPaidAt(LocalDateTime.now());
            }
            loanDisbursementRepository.save(d);
        }
    }

    /* ============================================================
       GUARD (called from final approval, Step 7/9)
       ============================================================ */

    @Override
    @Transactional(readOnly = true)
    public void assertCurrentPayrollAllowsDisbursement(Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee"));

        Long companyId = employee.getDepartment().getCompany().getId();
        PayrollPeriod openPeriod = payrollPeriodService.getOpenPeriod(companyId);

        Optional<PayrollRun> latest =
                payrollRunRepository
                        .findTopByEmployee_IdAndPeriodStartAndPeriodEndOrderByVersionNumberDesc(
                                employeeId,
                                openPeriod.getPeriodStart(),
                                openPeriod.getPeriodEnd());

        if (latest.isPresent() && latest.get().getStatus() == PayrollRunStatus.POSTED) {
            throw new IllegalStateException(
                    "The employee's current payroll is already posted. "
                            + "This loan cannot be disbursed inside the current payroll period.");
        }
    }

    /* ============================================================
       INTERNAL
       ============================================================ */

    private List<LoanDisbursement> findDisbursementsForRun(PayrollRun run) {
        return loanDisbursementRepository.findPayrollDisbursementsForRun(
                run.getEmployee().getId(),
                LoanDisbursementMethod.PAYROLL_PERIOD,
                LoanDisbursementStatus.SCHEDULED_IN_PAYROLL,
                LoanDisbursementStatus.PAID,
                resolvePayrollPeriodId(run),
                run.getPeriodStart(),
                run.getPeriodEnd());
    }

    /**
     * PayrollRun has no payroll period id, so find the company's period that contains the run's
     * [periodStart, periodEnd]. Containment (rather than exact match) keeps this working when an
     * OPEN period's end date was extended after the run was created.
     */
    private Long resolvePayrollPeriodId(PayrollRun run) {
        Employee employee = run.getEmployee();
        if (run.getPeriodStart() == null || run.getPeriodEnd() == null
                || employee == null || employee.getDepartment() == null
                || employee.getDepartment().getCompany() == null) {
            log.warn("Cannot resolve payroll period for run {}; no new loan disbursements will be attached.",
                    run.getId());
            return NO_PAYROLL_PERIOD_ID;
        }

        Long companyId = employee.getDepartment().getCompany().getId();
        return payrollPeriodRepository
                .findByCompanyIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                        companyId, run.getPeriodStart(), run.getPeriodEnd())
                .map(PayrollPeriod::getId)
                .orElseGet(() -> {
                    log.warn("No payroll period found for company {} covering {} to {}; "
                                    + "no new loan disbursements will be attached to run {}.",
                            companyId, run.getPeriodStart(), run.getPeriodEnd(), run.getId());
                    return NO_PAYROLL_PERIOD_ID;
                });
    }
}