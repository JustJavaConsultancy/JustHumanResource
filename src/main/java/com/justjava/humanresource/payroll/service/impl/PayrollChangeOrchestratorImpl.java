package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.dispatcher.PayrollMessageDispatcher;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.payroll.service.PayrollChangeOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PayrollChangeOrchestratorImpl
        implements PayrollChangeOrchestrator {

    private final PayrollMessageDispatcher dispatcher;
    private final EmployeeRepository employeeRepository;

    @Override
    public void recalculateForEmployee(
            Long employeeId,
            LocalDate effectiveDate) {

        dispatcher.requestPayroll(employeeId, effectiveDate);
    }

    @Override
    public void recalculateForPayGroup(
            Long payGroupId,
            LocalDate effectiveDate) {

        employeeRepository.findAll().stream()
                .filter(e ->
                        e.getPayGroup().getId().equals(payGroupId))
                .forEach(e ->
                        dispatcher.requestPayroll(
                                e.getId(),
                                effectiveDate
                        )
                );
    }
}
