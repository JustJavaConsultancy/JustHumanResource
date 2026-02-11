package com.justjava.humanresource.hr.service.impl;

import com.justjava.humanresource.common.enums.EmploymentStatus;
import com.justjava.humanresource.common.exception.ResourceNotFoundException;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.event.SalaryChangedEvent;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.payroll.event.PayGroupChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final JobStepRepository jobStepRepository;
    private final ApplicationEventPublisher eventPublisher;

    /* =========================
     * EXISTING BEHAVIOR (UNCHANGED)
     * ========================= */

    @Override
    public Employee createEmployee(Employee employee) {
        Employee saved = employeeRepository.save(employee);

        /*
         * First salary assignment is treated as a salary change.
         * This intentionally triggers first payroll.
         */
        eventPublisher.publishEvent(
                new SalaryChangedEvent(saved, LocalDate.now())
        );

        return saved;
    }

    @Override
    public Employee getByEmployeeNumber(String employeeNumber) {
        return employeeRepository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee", employeeNumber));
    }

    /* =========================
     * REFINED, INTENT-BASED METHODS
     * ========================= */

    @Override
    public Employee changePayGroup(
            Long employeeId,
            PayGroup newPayGroup,
            LocalDate effectiveDate) {

        Employee employee = getById(employeeId);

        employee.setPayGroup(newPayGroup);

        Employee saved = employeeRepository.save(employee);

        eventPublisher.publishEvent(
                new PayGroupChangedEvent(saved, effectiveDate)
        );

        return saved;
    }

    @Override
    public Employee changeJobStep(
            Long employeeId,
            Long newJobStepId,
            LocalDate effectiveDate) {

        Employee employee = getById(employeeId);

        JobStep newJobStep = jobStepRepository
                .findById(newJobStepId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("JobStep", newJobStepId));

        employee.setJobStep(newJobStep);

        Employee saved = employeeRepository.save(employee);

        /*
         * Salary is derived from JobStep,
         * so this is a salary change event.
         */
        eventPublisher.publishEvent(
                new SalaryChangedEvent(saved, effectiveDate)
        );

        return saved;
    }

    @Override
    public Employee changeEmploymentStatus(
            Long employeeId,
            EmploymentStatus newStatus,
            LocalDate effectiveDate) {

        Employee employee = getById(employeeId);

        employee.setEmploymentStatus(newStatus);

        Employee saved = employeeRepository.save(employee);

        /*
         * Status change MAY impact payroll depending on rules.
         * We still publish SalaryChangedEvent to keep downstream logic simple.
         */
        eventPublisher.publishEvent(
                new SalaryChangedEvent(saved, effectiveDate)
        );

        return saved;
    }

    /* =========================
     * INTERNAL HELPER
     * ========================= */

    private Employee getById(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee", employeeId));
    }
}
