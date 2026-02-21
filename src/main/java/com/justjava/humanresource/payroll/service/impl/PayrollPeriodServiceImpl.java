package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayrollPeriodServiceImpl implements PayrollPeriodService {

    private final PayrollPeriodRepository repository;
    private final PayrollRunRepository  payrollRunRepository;
    private final RuntimeService runtimeService;

    /* ============================================================
       OPEN PERIOD
       ============================================================ */

    @Override
    @Transactional
    public PayrollPeriod openPeriod(YearMonth yearMonth) {
        System.out.println(" The YearMonth==>"+yearMonth);

        repository.findByStatus(PayrollPeriodStatus.OPEN)
                .ifPresent(p ->
                { throw new IllegalStateException(
                        "Another period is already OPEN."); });

        repository.findByYearAndMonthAndStatus(
                yearMonth.getYear(),
                yearMonth.getMonthValue(),
                PayrollPeriodStatus.OPEN
        ).ifPresent(p ->
        { throw new IllegalStateException(
                "Period already exists."); });

        //PayrollPeriod currentOpen = getCurrentOpenPeriod();
        YearMonth next =
                YearMonth.of(yearMonth.getYear(), yearMonth.getMonth())
                        .plusMonths(1);

        if(isNewSetup()){
            next =YearMonth.of(yearMonth.getYear(), yearMonth.getMonth());
        }

        PayrollPeriod nextPeriod = new PayrollPeriod();
        nextPeriod.setYear(next.getYear());
        nextPeriod.setMonth(next.getMonthValue());
        nextPeriod.setStartDate(next.atDay(1));
        nextPeriod.setEndDate(next.atEndOfMonth());
        nextPeriod.setStatus(PayrollPeriodStatus.OPEN);
        return repository.save(nextPeriod);
    }

    public boolean isNewSetup() {
        return repository.count() == 0;
    }
    /* ============================================================
       CLOSE PERIOD
       ============================================================ */

    @Override
    @Transactional
    public void closeCurrentPeriod() {

        PayrollPeriod period = repository
                .findByStatus(PayrollPeriodStatus.OPEN)
                .orElseThrow(() ->
                        new IllegalStateException("No OPEN period found."));

        long incomplete =
                payrollRunRepository.countByPayrollDateBetweenAndStatusNot(
                        period.getStartDate(),
                        period.getEndDate(),
                        PayrollRunStatus.POSTED
                );

        if (incomplete > 0) {
            throw new IllegalStateException(
                    "Cannot close period. Some payroll runs are not POSTED.");
        }
        period.setStatus(PayrollPeriodStatus.CLOSED);
        repository.save(period);
    }

    /* ============================================================
       GET OPEN PERIOD
       ============================================================ */

    @Override
    public PayrollPeriod getCurrentOpenPeriod() {

        return repository
                .findByStatus(PayrollPeriodStatus.OPEN)
                .orElse(null);
    }

    @Override
    public PayrollPeriodStatus getPeriodStatusForDate(LocalDate date) {


        PayrollPeriod period = getCurrentOpenPeriod();
        if(period==null)
            return null;
        return period.getStatus();

//        return repository
//                .findByStartDateLessThanEqualAndEndDateGreaterThanEqual(
//                        date,
//                        date
//                )
//                .map(PayrollPeriod::getStatus)
//                .orElse(null);
   }
    /* ============================================================
       VALIDATE PAYROLL DATE
       ============================================================ */

    @Override
    public void validatePayrollDate(LocalDate payrollDate) {

        PayrollPeriod open = getCurrentOpenPeriod();

        if (payrollDate.isBefore(open.getStartDate())
                || payrollDate.isAfter(open.getEndDate())) {

            throw new IllegalStateException(
                    "Payroll date " + payrollDate +
                            " is outside the OPEN payroll period.");
        }
    }
    @Override
    @Transactional
    public void closeCurrentPeriodAndOpenNext() {

        PayrollPeriod current = repository
                .findByStatus(PayrollPeriodStatus.OPEN)
                .orElseThrow(() ->
                        new IllegalStateException("No OPEN payroll period."));

    /* ============================================================
       1️⃣ Ensure All Runs Are POSTED
       ============================================================ */

        long incomplete =
                payrollRunRepository.countByPayrollDateBetweenAndStatusNot(
                        current.getStartDate(),
                        current.getEndDate(),
                        PayrollRunStatus.POSTED
                );

        if (incomplete > 0) {
            throw new IllegalStateException(
                    "Cannot close period. Some payroll runs are not POSTED.");
        }

    /* ============================================================
       2️⃣ Close Current
       ============================================================ */

        current.setStatus(PayrollPeriodStatus.CLOSED);
        repository.save(current);

    /* ============================================================
       3️⃣ Determine Next Period
       ============================================================ */

        YearMonth next =
                YearMonth.of(current.getYear(), current.getMonth())
                        .plusMonths(1);

        repository.findByYearAndMonth(
                next.getYear(),
                next.getMonthValue()
        ).ifPresent(p -> {
            throw new IllegalStateException(
                    "Next period already exists.");
        });

    /* ============================================================
       4️⃣ Open Next Period
       ============================================================ */

        PayrollPeriod nextPeriod = new PayrollPeriod();
        nextPeriod.setYear(next.getYear());
        nextPeriod.setMonth(next.getMonthValue());
        nextPeriod.setStartDate(next.atDay(1));
        nextPeriod.setEndDate(next.atEndOfMonth());
        nextPeriod.setStatus(PayrollPeriodStatus.OPEN);

        repository.save(nextPeriod);
    }

    public void initiatePeriodCloseApproval(Long periodId) {

        runtimeService.startProcessInstanceByKey(
                "payrollPeriodCloseProcess",
                Map.of("periodId", periodId)
        );
    }
    public void initiateClosePeriod() {
        runtimeService.startProcessInstanceByKey(
                "payrollPeriodCloseProcess",
                Map.of("periodId", getCurrentOpenPeriod().getId())
        );
    }
    @Override
    public boolean isPayrollDateInOpenPeriod(LocalDate payrollDate) {

        return repository
                .findByStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        payrollDate,
                        payrollDate
                )
                .map(period -> period.getStatus() == PayrollPeriodStatus.OPEN)
                .orElse(false);
    }

}
