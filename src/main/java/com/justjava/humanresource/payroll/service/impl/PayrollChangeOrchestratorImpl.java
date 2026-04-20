   package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.dispatcher.PayrollMessageDispatcher;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.payroll.service.EmployeePositionHistoryService;
import com.justjava.humanresource.payroll.service.PayrollChangeOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class PayrollChangeOrchestratorImpl
        implements PayrollChangeOrchestrator {

    private final PayrollMessageDispatcher dispatcher;
    private final EmployeeRepository employeeRepository;
    private final EmployeePositionHistoryService positionHistoryService;

    @Override
    public void recalculateForEmployee(
            Long employeeId,
            LocalDate effectiveDate) {
        updateEmployeePositionHistory(employeeId, effectiveDate);
        dispatcher.requestPayroll(employeeId, effectiveDate);
    }

    @Override
    public void recalculateForPayGroup(
            Long payGroupId,
            LocalDate effectiveDate) {

        employeeRepository.findByPayGroup_Id(payGroupId)
                .forEach(e -> {
                    updateEmployeePositionHistory(e.getId(), effectiveDate);
                    dispatcher.requestPayroll(
                            e.getId(),
                            effectiveDate
                    );
                });
    }

    @Override
    public void updateEmployeePositionHistory(Long employeeId, LocalDate effectiveDate) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        positionHistoryService.changePosition(
                employee.getId(),
                employee.getDepartment().getId(),
                employee.getJobStep().getId(),
                employee.getPayGroup().getId(),
                effectiveDate
        );
    }

}
