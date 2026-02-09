package com.justjava.humanresource.workflow.listener;


import com.justjava.humanresource.hr.event.SalaryChangedEvent;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SalaryChangePayrollListener {

    private final RuntimeService runtimeService;

    @EventListener
    public void onSalaryChanged(SalaryChangedEvent event) {

        Map<String, Object> variables = new HashMap<>();
        variables.put("employeeId", event.getEmployee().getId());
        variables.put("payrollDate", LocalDate.now());
        variables.put("approvalRequired", true);

        runtimeService.startProcessInstanceByKey(
                "realTimePayrollProcess",
                variables
        );
    }
}
