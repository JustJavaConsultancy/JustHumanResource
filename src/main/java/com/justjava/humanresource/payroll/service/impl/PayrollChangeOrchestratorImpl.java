package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.dispatcher.PayrollMessageDispatcher;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.payroll.service.EmployeePositionHistoryService;
import com.justjava.humanresource.payroll.service.PayrollChangeOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollChangeOrchestratorImpl implements PayrollChangeOrchestrator {

    private final PayrollMessageDispatcher dispatcher;
    private final EmployeeRepository employeeRepository;
    private final EmployeePositionHistoryService positionHistoryService;

    // ✅ No class-level @Transactional — each method controls its own boundary

    @Override
    @Transactional
    public void recalculateForEmployee(Long employeeId, LocalDate effectiveDate) {
        updateEmployeePositionHistory(employeeId, effectiveDate);
        dispatcher.requestPayroll(employeeId, effectiveDate);
    }

    @Override
    public void recalculateForPayGroup(Long payGroupId, LocalDate effectiveDate) {
        // Load employees outside any transaction, then process each in its own
        List<Employee> employees = employeeRepository.findByPayGroup_Id(payGroupId);
        for (Employee e : employees) {
            processSingleEmployee(e.getId(), effectiveDate);
        }
    }

    @Override
    public void recalculateForJobStep(Long jobStepId, LocalDate effectiveDate) {
        // Load employees outside any transaction, then process each in its own
        List<Employee> employees = employeeRepository.findByJobStep_Id(jobStepId);
        for (Employee e : employees) {
            processSingleEmployee(e.getId(), effectiveDate);
        }
    }

    /**
     * Processes ONE employee in its own isolated transaction (REQUIRES_NEW).
     *
     * This is the core fix: by using REQUIRES_NEW, each employee's
     * changePosition() call gets a fresh JPA session and a fresh DB transaction.
     * JPA dirty-checking state from a previous employee cannot bleed into
     * the next one, which prevents the duplicate-key collision on
     * (employee_id, effective_from, current=false).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleEmployee(Long employeeId, LocalDate effectiveDate) {
        updateEmployeePositionHistory(employeeId, effectiveDate);
        dispatcher.requestPayroll(employeeId, effectiveDate);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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