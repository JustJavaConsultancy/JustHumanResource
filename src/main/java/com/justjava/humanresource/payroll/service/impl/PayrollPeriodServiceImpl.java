package com.justjava.humanresource.payroll.service.impl;


import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.payroll.entity.PaySlip;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PaySlipRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import com.justjava.humanresource.core.enums.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.justjava.humanresource.hr.entity.Employee;
import java.util.stream.Collectors;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayrollPeriodServiceImpl implements PayrollPeriodService {

    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollPeriodRepository repository;
    private final PayrollRunRepository payrollRunRepository;
    private final RuntimeService runtimeService;
    private final PaySlipRepository paySlipRepository;
    private final EmployeeRepository employeeRepository;

    /* ============================================================
       INITIALIZATION
       ============================================================ */

    @Override
    @Transactional
    public PayrollPeriod openInitialPeriod(
            Long companyId,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {

        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("Period start/end cannot be null.");
        }

        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("Period end cannot be before start.");
        }

        if (repository.existsByCompanyIdAndStatus(
                companyId,
                PayrollPeriodStatus.OPEN)) {

            throw new IllegalStateException(
                    "Company already has an OPEN payroll period.");
        }

        if (repository.existsOverlappingPeriod(companyId, periodStart, periodEnd)) {
            throw new IllegalStateException(
                    "Payroll period overlaps an existing period for this company.");
        }

        PayrollPeriod period = new PayrollPeriod();
        period.setCompanyId(companyId);
        period.setPeriodStart(periodStart);
        period.setPeriodEnd(periodEnd);
        period.setPlannedPeriodEnd(periodEnd);
        period.setStatus(PayrollPeriodStatus.OPEN);

        return repository.save(period);
    }

    /* ============================================================
       CLOSE & OPEN NEXT
       ============================================================ */

    @Override
    @Transactional
    public void closeAndOpenNext(Long companyId) {
        PayrollPeriod current = getOpenPeriod(companyId);
        if (current == null) {
            throw new IllegalStateException("No open payroll period found.");
        }
        closeAndOpenNext(companyId, current.getPeriodEnd());
    }

    @Override
    @Transactional
    public void closeAndOpenNext(Long companyId, LocalDate actualPeriodEnd) {

        // ---------------------------------------------------------
        // 1. Get Current OPEN Period
        // ---------------------------------------------------------

        PayrollPeriod current = getOpenPeriod(companyId);

        if (current == null) {
            throw new IllegalStateException("No open payroll period found.");
        }

        validateActualPeriodEnd(current, actualPeriodEnd);

        // ---------------------------------------------------------
        // 2. Validate All Runs Are POSTED
        // ---------------------------------------------------------

        long incomplete =
                payrollRunRepository
                        .countLatestByCompanyAndPayrollDateBetweenAndStatusNot(
                                companyId,
                                current.getPeriodStart(),
                                actualPeriodEnd,
                                PayrollRunStatus.POSTED
                        );

        if (incomplete > 0) {
            throw new IllegalStateException(
                    "Cannot close period. Some payroll runs are not POSTED."
            );
        }

        // ---------------------------------------------------------
        // 3. Close Current Period
        // ---------------------------------------------------------

        current.setPeriodEnd(actualPeriodEnd);
        if (current.getPlannedPeriodEnd() == null) {
            current.setPlannedPeriodEnd(actualPeriodEnd);
        }
        current.setClosedOn(LocalDate.now());
        current.setStatus(PayrollPeriodStatus.CLOSED);
        repository.save(current);

        // ---------------------------------------------------------
        // 4. Compute Next Period Range
        // ---------------------------------------------------------

        LocalDate nextStart = actualPeriodEnd.plusDays(1);
        LocalDate nextEnd = calculateNextPeriodEnd(current, nextStart);

        PayrollPeriod next = new PayrollPeriod();
        next.setCompanyId(companyId);
        next.setPeriodStart(nextStart);
        next.setPeriodEnd(nextEnd);
        next.setPlannedPeriodEnd(nextEnd);
        next.setCycleLengthDays(current.getCycleLengthDays());
        next.setStatus(PayrollPeriodStatus.OPEN);

        PayrollPeriod savedNext = repository.save(next);
        if (savedNext != null) {
            next = savedNext;
        }

        // ---------------------------------------------------------
        // 5. Ensure New Period Has No Existing Runs (Idempotency Guard)
        // ---------------------------------------------------------

        long existingNewPeriodRuns =
                payrollRunRepository.countByEmployee_Department_Company_IdAndPayrollDateBetween(
                        companyId,
                        nextStart,
                        nextEnd
                );

        if (existingNewPeriodRuns > 0) {
            throw new IllegalStateException(
                    "Payroll runs already exist for new period. Aborting carry-forward."
            );
        }

        // ---------------------------------------------------------
        // 6. Calculate payroll for the new OPEN period
        // ---------------------------------------------------------

        if (next.getId() == null) {
            throw new IllegalStateException(
                    "New payroll period was not persisted; cannot start batch payroll.");
        }

        runtimeService.startProcessInstanceByKey(
                "batchPayrollProcess",
                "BATCH_PERIOD_" + next.getId(),
                Map.of(
                        "periodId", next.getId(),
                        "companyId", companyId,
                        "periodStart", nextStart,
                        "periodEnd", nextEnd
                )
        );
    }

    private void validateActualPeriodEnd(PayrollPeriod current, LocalDate actualPeriodEnd) {
        if (actualPeriodEnd == null) {
            throw new IllegalArgumentException("Actual period end cannot be null.");
        }

        if (actualPeriodEnd.isBefore(current.getPeriodStart())) {
            throw new IllegalArgumentException("Actual period end cannot be before period start.");
        }

        if (actualPeriodEnd.isBefore(current.getPeriodEnd())) {
            throw new IllegalArgumentException("Actual period end cannot shorten the current period.");
        }

        if (repository.existsOverlappingPeriodExcludingId(
                current.getCompanyId(),
                current.getId(),
                current.getPeriodStart(),
                actualPeriodEnd)) {
            throw new IllegalStateException(
                    "Extended payroll period overlaps an existing period for this company.");
        }
    }

    private LocalDate calculateNextPeriodEnd(PayrollPeriod previous, LocalDate nextStart) {
        if (previous.getCycleLengthDays() != null && previous.getCycleLengthDays() > 0) {
            return nextStart.plusDays(previous.getCycleLengthDays() - 1L);
        }

        return YearMonth.from(nextStart).atEndOfMonth();
    }

    @Override
    @Transactional
    public PayrollPeriod extendOpenPeriodEnd(Long companyId, LocalDate newPeriodEnd) {
        PayrollPeriod open = getOpenPeriod(companyId);

        if (open == null) {
            throw new IllegalStateException("No open payroll period found.");
        }

        validateActualPeriodEnd(open, newPeriodEnd);

        open.setPeriodEnd(newPeriodEnd);
        if (open.getPlannedPeriodEnd() == null) {
            open.setPlannedPeriodEnd(newPeriodEnd);
        }

        return repository.save(open);
    }
    /* ============================================================
       GET OPEN PERIOD
       ============================================================ */

    @Override
    public PayrollPeriod getOpenPeriod(Long companyId) {

        return repository
                .findByCompanyIdAndStatus(
                        companyId,
                        PayrollPeriodStatus.OPEN
                ).orElse(null);
    }

    /* ============================================================
       VALIDATION
       ============================================================ */

    @Override
    public void validatePayrollDate(
            Long companyId,
            LocalDate payrollDate
    ) {

        PayrollPeriod open = getOpenPeriod(companyId);

        if (payrollDate.isBefore(open.getPeriodStart())
                || payrollDate.isAfter(open.getPeriodEnd())) {

            throw new IllegalStateException(
                    "Payroll date " + payrollDate +
                            " is outside the OPEN payroll period."
            );
        }
    }

    @Override
    public boolean isPayrollDateInOpenPeriod(
            Long companyId,
            LocalDate payrollDate
    ) {

        return repository
                .findByCompanyIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                        companyId,
                        payrollDate,
                        payrollDate
                )
                .map(period -> period.getStatus() == PayrollPeriodStatus.OPEN)
                .orElse(false);
    }

    @Override
    public PayrollPeriodStatus getPeriodStatusForDate(
            Long companyId,
            LocalDate payrollDate
    ) {

        return repository
                .findByCompanyIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                        companyId,
                        payrollDate,
                        payrollDate
                )
                .map(PayrollPeriod::getStatus)
                .orElse(null);
    }
    @Override
    public PayrollPeriodStatus getCurrentPeriodStatus(Long companyId) {
        PayrollPeriod current =
                payrollPeriodRepository
                        .findByCompanyIdAndStatusIn(
                                companyId,
                                List.of(
                                        PayrollPeriodStatus.OPEN,
                                        PayrollPeriodStatus.LOCKED
                                )
                        )
                        .orElse(null);
        PayrollPeriodStatus status = current != null ? current.getStatus() : null;
        return status;
    }
    @Override
    public PayrollPeriod getCurrentPeriod(Long companyId) {
        PayrollPeriod current =
                payrollPeriodRepository
                        .findByCompanyIdAndStatusIn(
                                companyId,
                                List.of(
                                        PayrollPeriodStatus.OPEN,
                                        PayrollPeriodStatus.LOCKED
                                )
                        )
                        .orElse(null);

        return current;
    }

    @Override
    public List<PayrollPeriod> getClosedPeriods(Long companyId) {
        return payrollPeriodRepository.findByCompanyIdOrderByPeriodStartDesc(companyId)
                .stream()
                .filter(period -> period.getStatus() == PayrollPeriodStatus.CLOSED)
                .toList();
    }
    /* ============================================================
       FLOWABLE APPROVAL
       ============================================================ */


    @Override
    @Transactional
    public void initiatePeriodCloseApproval(Long companyId) {
        PayrollPeriod open = getOpenPeriod(companyId);
        if (open == null) {
            throw new IllegalStateException("No open payroll period found.");
        }
        initiatePeriodCloseApproval(companyId, open.getPeriodEnd());
    }

    @Override
    @Transactional
    public void initiatePeriodCloseApproval(Long companyId, LocalDate actualPeriodEnd) {

        PayrollPeriod open = getOpenPeriod(companyId);

        if (open == null) {
            throw new IllegalStateException("No open payroll period found.");
        }

        validateActualPeriodEnd(open, actualPeriodEnd);

        open.setPeriodEnd(actualPeriodEnd);
        if (open.getPlannedPeriodEnd() == null) {
            open.setPlannedPeriodEnd(actualPeriodEnd);
        }
        open = repository.save(open);

        List<Employee> missingDetails = employeeRepository.findEmployeesMissingBankDetails(
                companyId,
                open.getPeriodStart(),
                actualPeriodEnd
        );

        if (!missingDetails.isEmpty()) {

            String names = missingDetails.stream()
                    .map(emp -> emp.getFirstName() + " " + emp.getLastName())
                    .distinct()
                    .collect(Collectors.joining(", "));

            throw new IllegalStateException(
                    "BLOCKING ERROR: Period cannot be locked. " + missingDetails.size() +
                            " employee(s) are missing bank details: [" + names + "]."
            );
        }

        runtimeService.startProcessInstanceByKey(
                "payrollPeriodCloseProcess",
                Map.of(
                        "companyId", open.getCompanyId(),
                        "periodId", open.getId(),
                        "periodStart", open.getPeriodStart(),
                        "periodEnd", actualPeriodEnd,
                        "actualPeriodEnd", actualPeriodEnd
                )
        );
    }


    @Override
    public PayrollPeriod findById(Long id) {
        return repository.findById(id).orElse(null);
    }
}