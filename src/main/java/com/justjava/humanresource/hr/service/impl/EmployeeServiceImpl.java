package com.justjava.humanresource.hr.service.impl;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.core.exception.ResourceNotFoundException;
import com.justjava.humanresource.dispatcher.PayrollMessageDispatcher;
import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeePositionHistory;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.event.SalaryChangedEvent;
import com.justjava.humanresource.hr.mapper.EmployeeMapper;
import com.justjava.humanresource.hr.repository.EmployeePositionHistoryRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.hr.repository.PayGroupRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.payroll.event.PayGroupChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final JobStepRepository jobStepRepository;
    private final PayrollMessageDispatcher payrollMessageDispatcher;
    private final EmployeeMapper employeeMapper;
    private final PayGroupRepository  payGroupRepository;
    private final EmployeePositionHistoryRepository employeePositionHistoryRepository;


    /* =========================
     * EXISTING BEHAVIOR (UNCHANGED)
     * ========================= */

    @Override
    public Employee createEmployee(EmployeeDTO dto) {
        Employee employee = employeeMapper.toEntity(dto);
        return employeeRepository.save(employee);
    }
    @Override
    public List<EmployeeDTO> getAllEmployees() {
        List<Employee> employees = employeeRepository.findAll();
        return employeeMapper.toDtoList(employees);
    }
    public EmployeeDTO createAndActivateEmployee(EmployeeDTO dto) {

        Employee employee = createEmployee(dto);

        return changeEmploymentStatus(
                employee.getId(),
                EmploymentStatus.ACTIVE,
                LocalDate.now()
        );
    }

    @Override
    public Employee getByEmployeeNumber(String employeeNumber) {
        return employeeRepository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee", employeeNumber));
    }
    @Override
    public Employee getByEmail(String email) {
        return employeeRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee", email));
    }

    /* =========================
     * REFINED, INTENT-BASED METHODS
     * ========================= */

    @Override
    public EmployeeDTO changePayGroup(
            Long employeeId,
            PayGroup newPayGroup,
            LocalDate effectiveDate) {

        Employee employee = getById(employeeId);

        employee.setPayGroup(newPayGroup);

        Employee saved = employeeRepository.save(employee);

        payrollMessageDispatcher.requestPayroll(saved.getId(), effectiveDate);
/*        eventPublisher.publishEvent(
                new PayGroupChangedEvent(saved, effectiveDate)
        );*/

        return employeeMapper.toDto(saved);
    }

    @Override
    public EmployeeDTO changeJobStep(
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
        payrollMessageDispatcher.requestPayroll(saved.getId(), effectiveDate);
        /*
         * Salary is derived from JobStep,
         * so this is a salary change event.
         */
/*        eventPublisher.publishEvent(
                new SalaryChangedEvent(saved, effectiveDate)
        );*/
        return employeeMapper.toDto(saved);
    }
    @Transactional
    public void changePosition(
            Long employeeId,
            Long jobStepId,
            Long payGroupId,
            LocalDate effectiveFrom) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow();

        JobStep jobStep = jobStepRepository.findById(jobStepId)
                .orElseThrow();

        PayGroup payGroup = payGroupRepository.findById(payGroupId)
                .orElseThrow();

    /* ============================================================
       1️⃣ Close Existing Active Position
       ============================================================ */

        Optional<EmployeePositionHistory> current =
                employeePositionHistoryRepository
                        .resolvePosition(
                                employeeId,
                                effectiveFrom.minusDays(1),
                                RecordStatus.ACTIVE
                        );

        current.ifPresent(existing -> {
            existing.setEffectiveTo(effectiveFrom.minusDays(1));
            existing.setStatus(RecordStatus.INACTIVE);
            employeePositionHistoryRepository.save(existing);
        });

    /* ============================================================
       2️⃣ Insert New Position
       ============================================================ */

        EmployeePositionHistory newPosition =
                new EmployeePositionHistory();

        newPosition.setEmployee(employee);
        newPosition.setJobStep(jobStep);
        newPosition.setPayGroup(payGroup);
        newPosition.setEffectiveFrom(effectiveFrom);
        newPosition.setStatus(RecordStatus.ACTIVE);

        employeePositionHistoryRepository.save(newPosition);

    /* ============================================================
       3️⃣ Trigger Payroll Recalculation
       ============================================================ */

        payrollMessageDispatcher.requestPayroll(employeeId, effectiveFrom);
/*        eventPublisher.publishEvent(
                new SalaryChangedEvent(employee)
        );*/
    }

    @Override
    public EmployeeDTO changeEmploymentStatus(
            Long employeeId,
            EmploymentStatus newStatus,
            LocalDate effectiveDate) {

        Employee employee = getById(employeeId);

        employee.setEmploymentStatus(newStatus);

        Employee saved = employeeRepository.save(employee);
        payrollMessageDispatcher.requestPayroll(saved.getId(), effectiveDate);

        /*
         * Status change MAY impact payroll depending on rules.
         * We still publish SalaryChangedEvent to keep downstream logic simple.
         */
/*        eventPublisher.publishEvent(
                new SalaryChangedEvent(saved, effectiveDate)
        );*/

        return employeeMapper.toDto(saved);
    }

    @Override
    public String generateInitialPassword(Employee employee) {
        return "password!";
    }

    /* =========================
     * INTERNAL HELPER
     * ========================= */


    @Override
    public Employee getById(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee", employeeId));
    }

    @Override
    public Employee save(Employee employee) {
        return employeeRepository.save(employee);
    }
}
