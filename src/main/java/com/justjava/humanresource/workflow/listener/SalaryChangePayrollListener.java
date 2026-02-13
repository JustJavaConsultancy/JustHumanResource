package com.justjava.humanresource.workflow.listener;


import com.justjava.humanresource.dispatcher.PayrollMessageDispatcher;
import com.justjava.humanresource.hr.event.SalaryChangedEvent;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SalaryChangePayrollListener {

    //private final RuntimeService runtimeService;
    private final PayrollMessageDispatcher  payrollMessageDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalaryChanged(SalaryChangedEvent event) {
        payrollMessageDispatcher.requestPayroll(
                event.getEmployee().getId(),
                LocalDate.now()
        );
    }
}
