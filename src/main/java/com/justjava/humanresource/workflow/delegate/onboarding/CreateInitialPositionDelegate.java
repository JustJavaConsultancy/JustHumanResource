package com.justjava.humanresource.workflow.delegate.onboarding;


import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeePositionHistory;
import com.justjava.humanresource.hr.repository.EmployeePositionHistoryRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.payroll.service.EmployeePositionHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Component("createInitialPositionDelegate")
@RequiredArgsConstructor
@Transactional
public class CreateInitialPositionDelegate implements JavaDelegate {

    private final EmployeeRepository employeeRepository;
    private final EmployeePositionHistoryService positionHistoryService;

    @Override
    public void execute(DelegateExecution execution) {

        Long employeeId = (Long) execution.getVariable("employeeId");

        if (employeeId == null) {
            throw new IllegalStateException("Missing process variable: employeeId");
        }
        positionHistoryService.createInitialPosition(employeeId);
    }
}
