package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayrollPeriodServiceImpl implements PayrollPeriodService {

    private final PayrollPeriodRepository repository;
    private final PayrollRunRepository payrollRunRepository;
    private final RuntimeService runtimeService;

    /* ============================================================
       INITIAL SETUP (FIRST PERIOD ONLY)
       ============================================================ */

    @Override
    @Transactional
    public PayrollPeriod openInitialPeriod(
            Long companyId,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {

        if (repository.existsByCompanyIdAndStatus(
                companyId,
                PayrollPeriodStatus.OPEN)) {

            throw new IllegalStateException(
                    "Company already has an OPEN payroll period."
            );
        }

        if (repository.existsByCompanyIdAndPeriodStartAndPeriodEnd(
                companyId,
                periodStart,
                periodEnd)) {

            throw new IllegalStateException(
                    "Payroll period already exists for this range."
            );
        }

        PayrollPeriod period = new PayrollPeriod();
        period.setCompanyId(companyId);
        period.setPeriodStart(periodStart);
        period.setPeriodEnd(periodEnd);
        period.setStatus(PayrollPeriodStatus.OPEN);

        return repository.save(period);
    }

    /* ============================================================
       LOCK PERIOD (NO MORE RECALCULATION)
       ============================================================ */

    //@Override
    //@Transactional
    private void lockPeriod(Long companyId) {

        PayrollPeriod open = getOpenPeriod(companyId);
        open.setStatus(PayrollPeriodStatus.LOCKED);
        repository.save(open);
    }

    /* ============================================================
       CLOSE & OPEN NEXT (MANUAL CONTROL)
       ============================================================ */

    @Override
    @Transactional
    public void closeAndOpenNext(Long companyId) {

        PayrollPeriod current = getOpenPeriod(companyId);

        long incomplete =
                payrollRunRepository.countByEmployee_Department_Company_IdAndPayrollDateBetweenAndStatus(
                        companyId,
                        current.getPeriodStart(),
                        current.getPeriodEnd(),
                        PayrollRunStatus.POSTED
                );

        if (incomplete > 0) {
            throw new IllegalStateException(
                    "Cannot close period. Some payroll runs are not POSTED."
            );
        }

        current.setStatus(PayrollPeriodStatus.CLOSED);
        repository.save(current);

        /* --------------------------------------------------------
           DEFAULT NEXT PERIOD STRATEGY
           (Same cycle length as previous)
           -------------------------------------------------------- */

        long cycleDays =
                current.getPeriodEnd()
                        .toEpochDay()
                        - current.getPeriodStart().toEpochDay()
                        + 1;

        LocalDate nextStart = current.getPeriodEnd().plusDays(1);
        LocalDate nextEnd = nextStart.plusDays(cycleDays - 1);

        PayrollPeriod next = new PayrollPeriod();
        next.setCompanyId(companyId);
        next.setPeriodStart(nextStart);
        next.setPeriodEnd(nextEnd);
        next.setStatus(PayrollPeriodStatus.OPEN);

        repository.save(next);
    }

    /* ============================================================
       GET OPEN PERIOD (COMPANY SCOPED)
       ============================================================ */

    @Override
    public PayrollPeriod getOpenPeriod(Long companyId) {

        return repository
                .findByCompanyIdAndStatus(
                        companyId,
                        PayrollPeriodStatus.OPEN
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No OPEN payroll period found for company."
                        ));
    }

    /* ============================================================
       VALIDATE PAYROLL DATE
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
                .map(p -> p.getStatus() == PayrollPeriodStatus.OPEN)
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

    /* ============================================================
       FLOWABLE APPROVAL
       ============================================================ */

    @Transactional
    public void initiatePeriodCloseApproval(Long companyId) {

        PayrollPeriod open = getOpenPeriod(companyId);

        if (open.getStatus() != PayrollPeriodStatus.OPEN) {
            throw new IllegalStateException(
                    "Only OPEN period can be submitted for approval."
            );
        }

    /* ============================================================
       1️⃣ Ensure all payroll runs are POSTED (company scoped)
       ============================================================ */

        long incomplete =
                payrollRunRepository
                        .countByEmployee_Department_Company_IdAndPayrollDateBetweenAndStatusNot(
                                companyId,
                                open.getPeriodStart(),
                                open.getPeriodEnd(),
                                PayrollRunStatus.POSTED
                        );

        if (incomplete > 0) {
            throw new IllegalStateException(
                    "Cannot submit period for approval. Some payroll runs are not POSTED."
            );
        }

    /* ============================================================
       2️⃣ Lock period BEFORE starting workflow
       ============================================================ */

        open.setStatus(PayrollPeriodStatus.LOCKED);
        repository.save(open);

    /* ============================================================
       3️⃣ Start approval workflow
       ============================================================ */

        runtimeService.startProcessInstanceByKey(
                "payrollPeriodCloseProcess",
                Map.of(
                        "companyId", companyId,
                        "periodId", open.getId()
                )
        );
    }
}