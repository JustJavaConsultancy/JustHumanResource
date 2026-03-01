package com.justjava.humanresource.dispatcher;


import java.time.LocalDate;

public interface PayrollMessageDispatcher {

    void requestPayroll(
            Long employeeId,
            LocalDate effectiveDate
    );

    void requestBatchPayroll(
            Long periodId
    );
}