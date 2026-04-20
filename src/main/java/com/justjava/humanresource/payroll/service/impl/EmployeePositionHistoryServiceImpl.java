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
import java.util.Optional;

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
            // Ensure status is ACTIVE for the existing record if it was somehow different
            EmployeePositionHistory existing = getCurrentPosition(employeeId);
            if (existing.getStatus() != com.justjava.humanresource.core.enums.RecordStatus.ACTIVE) {
                existing.setStatus(com.justjava.humanresource.core.enums.RecordStatus.ACTIVE);
                positionRepository.save(existing);
            }
            return existing;
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
                        .status(com.justjava.humanresource.core.enums.RecordStatus.ACTIVE)
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

        // ---------------------------------------------------------
        // 🔥 HANDLE EXISTING POSITION (SAFE & UNAMBIGUOUS)
        // ---------------------------------------------------------

        List<EmployeePositionHistory> currentPositions =
                positionRepository.findAllByEmployee_IdAndCurrentTrue(employeeId);

        for (EmployeePositionHistory current : currentPositions) {
            // If the current record started on the same day, update it instead of creating a new one
            // This avoids unique constraint violations on (employee_id, effective_from)
            if (current.getEffectiveFrom().equals(effectiveDate)) {
                current.setDepartment(department);
                current.setJobStep(jobStep);
                current.setPayGroup(payGroup);
                current.setStatus(com.justjava.humanresource.core.enums.RecordStatus.ACTIVE);
                current.setCurrent(true); // Re-assert current just in case
                return positionRepository.save(current);
            }

            current.setEffectiveTo(effectiveDate.minusDays(1));
            current.setCurrent(false);
            current.setStatus(com.justjava.humanresource.core.enums.RecordStatus.INACTIVE);
            positionRepository.save(current);
        }

        // ---------------------------------------------------------
        // CREATE NEW POSITION
        // ---------------------------------------------------------

        EmployeePositionHistory newPosition =
                EmployeePositionHistory.builder()
                        .employee(employee)
                        .department(department)
                        .jobStep(jobStep)
                        .payGroup(payGroup)
                        .effectiveFrom(effectiveDate)
                        .current(true)
                        .status(com.justjava.humanresource.core.enums.RecordStatus.ACTIVE)
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
