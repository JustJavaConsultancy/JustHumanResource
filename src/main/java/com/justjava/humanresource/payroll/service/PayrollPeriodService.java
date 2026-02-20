package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollPeriodStatus;

import java.time.LocalDate;
import java.time.YearMonth;

public interface PayrollPeriodService {

    PayrollPeriod openPeriod(YearMonth yearMonth);

    void closeCurrentPeriod();
    void closeCurrentPeriodAndOpenNext();
    PayrollPeriod getCurrentOpenPeriod();

    public void initiatePeriodCloseApproval(Long periodId);

    PayrollPeriodStatus getPeriodStatusForDate(LocalDate date);

    void validatePayrollDate(java.time.LocalDate payrollDate);
    public boolean isPayrollDateInOpenPeriod(LocalDate payrollDate);
}
