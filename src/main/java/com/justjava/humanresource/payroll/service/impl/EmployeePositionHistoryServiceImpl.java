package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.core.exception.ResourceNotFoundException;
import com.justjava.humanresource.hr.dto.EmployeePositionHistoryDTO;
import com.justjava.humanresource.hr.entity.*;
import com.justjava.humanresource.hr.mapper.EmployeePositionHistoryMapper;
import com.justjava.humanresource.hr.repository.*;

import com.justjava.humanresource.payroll.service.EmployeePositionHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeePositionHistoryServiceImpl
        implements EmployeePositionHistoryService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final JobStepRepository jobStepRepository;
    private final PayGroupRepository payGroupRepository;
    private final EmployeePositionHistoryRepository positionRepository;
    private final EmployeePositionHistoryMapper mapper;



    /* =========================================================
       CREATE INITIAL POSITION (Onboarding)
       ========================================================= */

    @Override
    public EmployeePositionHistory createInitialPosition(Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee", employeeId));

        boolean alreadyExists =
                positionRepository.existsByEmployee_IdAndCurrentTrue(employeeId);

        if (alreadyExists) {
            return getCurrentPosition(employeeId);
        }

        LocalDate effectiveDate =
                employee.getDateOfHire() != null
                        ? employee.getDateOfHire()
                        : LocalDate.now();

        EmployeePositionHistory history =
                EmployeePositionHistory.builder()
                        .employee(employee)
                        .department(employee.getDepartment())
                        .jobStep(employee.getJobStep())
                        .payGroup(employee.getPayGroup())
                        .effectiveFrom(effectiveDate)
                        .current(true)
                        .build();

        return positionRepository.save(history);
    }

    /* =========================================================
       CHANGE POSITION (Promotion / Transfer)
       ========================================================= */

    @Override
    public EmployeePositionHistory changePosition(
            Long employeeId,
            Long departmentId,
            Long jobStepId,
            Long payGroupId,
            LocalDate effectiveDate
    ) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee", employeeId));

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Department", departmentId));

        JobStep jobStep = jobStepRepository.findById(jobStepId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("JobStep", jobStepId));

        PayGroup payGroup = payGroupRepository.findById(payGroupId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("PayGroup", payGroupId));

        // Close existing position
        EmployeePositionHistory current =
                getCurrentPosition(employeeId);

        current.setEffectiveTo(effectiveDate.minusDays(1));
        current.setCurrent(false);
        positionRepository.save(current);

        // Create new position
        EmployeePositionHistory newPosition =
                EmployeePositionHistory.builder()
                        .employee(employee)
                        .department(department)
                        .jobStep(jobStep)
                        .payGroup(payGroup)
                        .effectiveFrom(effectiveDate)
                        .current(true)
                        .build();

        return positionRepository.save(newPosition);
    }

    /* =========================================================
       QUERIES
       ========================================================= */

    @Override
    @Transactional(readOnly = true)
    public List<EmployeePositionHistoryDTO> getActivePositions() {

        return positionRepository
                .findByCurrentTrueAndEffectiveToIsNull()
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeePositionHistory getCurrentPosition(Long employeeId) {
        return positionRepository
                .findByEmployee_IdAndCurrentTrue(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Active Position for Employee", employeeId));
    }
    @Override
    @Transactional(readOnly = true)
    public EmployeePositionHistoryDTO getCurrentPositionAPI(Long employeeId) {

        EmployeePositionHistory position =
                positionRepository
                        .findByEmployee_IdAndCurrentTrue(employeeId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Active Position for Employee",
                                        employeeId
                                ));

        return mapper.toDto(position);
    }

}
