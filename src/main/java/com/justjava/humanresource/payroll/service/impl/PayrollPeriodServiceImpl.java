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

        PayrollPeriod period = new PayrollPeriod();
        period.setCompanyId(companyId);
        period.setPeriodStart(periodStart);
        period.setPeriodEnd(periodEnd);
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

        /* --------------------------------------------------------
           Ensure All Runs Are POSTED
           -------------------------------------------------------- */

        long incomplete =
                payrollRunRepository
                        .countByCompanyIdAndPayrollDateBetweenAndStatusNot(
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

        /* --------------------------------------------------------
           Close Current Period
           -------------------------------------------------------- */

        current.setStatus(PayrollPeriodStatus.CLOSED);
        repository.save(current);

        /* --------------------------------------------------------
           Create Next Period (Cycle-Based)
           Uses same cycle length as previous period
           -------------------------------------------------------- */

        long cycleDays =
                current.getPeriodEnd().toEpochDay()
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
       GET OPEN PERIOD
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

    /* ============================================================
       FLOWABLE APPROVAL
       ============================================================ */

    @Override
    public void initiatePeriodCloseApproval(Long companyId) {

        PayrollPeriod open = getOpenPeriod(companyId);

        runtimeService.startProcessInstanceByKey(
                "payrollPeriodCloseProcess",
                Map.of(
                        "companyId", open.getCompanyId(),
                        "periodId", open.getId(),
                        "periodStart", open.getPeriodStart(),
                        "periodEnd", open.getPeriodEnd()
                )
        );
    }
}
