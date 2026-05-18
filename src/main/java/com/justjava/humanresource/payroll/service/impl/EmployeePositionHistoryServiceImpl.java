package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.core.enums.RecordStatus;
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
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        boolean alreadyExists = positionRepository.existsByEmployee_IdAndCurrentTrue(employeeId);

        if (alreadyExists) {
            EmployeePositionHistory existing = getCurrentPosition(employeeId);
            if (existing.getStatus() != RecordStatus.ACTIVE) {
                existing.setStatus(RecordStatus.ACTIVE);
                positionRepository.save(existing);
            }
            return existing;
        }

        LocalDate effectiveDate = employee.getDateOfHire() != null
                ? employee.getDateOfHire()
                : LocalDate.now();

        EmployeePositionHistory history = EmployeePositionHistory.builder()
                .employee(employee)
                .department(employee.getDepartment())
                .jobStep(employee.getJobStep())
                .payGroup(employee.getPayGroup())
                .effectiveFrom(effectiveDate)
                .current(true)
                .status(RecordStatus.ACTIVE)
                .build();

        return positionRepository.save(history);
    }

    /* =========================================================
       CHANGE POSITION (Promotion / Transfer / Recalculation)
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
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));

        JobStep jobStep = jobStepRepository.findById(jobStepId)
                .orElseThrow(() -> new ResourceNotFoundException("JobStep", jobStepId));

        PayGroup payGroup = payGroupRepository.findById(payGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("PayGroup", payGroupId));

        // ---------------------------------------------------------
        // STEP 1: Load ALL records for this employee on this date
        // (could be current=true, current=false, or both — we handle each case)
        // ---------------------------------------------------------
        List<EmployeePositionHistory> recordsOnSameDate =
                positionRepository.findAllByEmployeeIdAndEffectiveFrom(employeeId, effectiveDate);

        // Is there already a current=true record starting on this exact date?
        Optional<EmployeePositionHistory> existingCurrentOnDate = recordsOnSameDate.stream()
                .filter(EmployeePositionHistory::isCurrent)
                .findFirst();

        if (existingCurrentOnDate.isPresent()) {
            // Same-day update: just overwrite the existing current record in place.
            // This is safe — no new row, no constraint violation.
            EmployeePositionHistory existing = existingCurrentOnDate.get();
            existing.setDepartment(department);
            existing.setJobStep(jobStep);
            existing.setPayGroup(payGroup);
            existing.setStatus(RecordStatus.ACTIVE);
            existing.setCurrent(true);
            return positionRepository.save(existing);
        }

        // ---------------------------------------------------------
        // STEP 2: Close any current=true records that started BEFORE today
        // ---------------------------------------------------------
        List<EmployeePositionHistory> currentPositions =
                positionRepository.findAllByEmployee_IdAndCurrentTrue(employeeId);

        for (EmployeePositionHistory current : currentPositions) {
            current.setEffectiveTo(effectiveDate.minusDays(1));
            current.setCurrent(false);
            current.setStatus(RecordStatus.INACTIVE);
            positionRepository.save(current);
        }

        // ---------------------------------------------------------
        // STEP 3: Check if closing those records just produced a
        // current=false record on this same date (the duplicate key scenario).
        // If so, update it in place rather than inserting a new current=true row.
        //
        // WHY THIS HAPPENS: When recalculation loops over multiple job steps,
        // the first pass already closed+saved a current=false for today.
        // The second pass would try to insert another current=true for today,
        // but Postgres sees (employee_id, effective_from, current=true) is new
        // while (employee_id, effective_from, current=false) already exists —
        // that part is fine. The real collision is when a THIRD pass tries to
        // close the current=true from pass 2, creating a second current=false
        // for the same date. We prevent that by reusing the existing one.
        // ---------------------------------------------------------
        Optional<EmployeePositionHistory> existingInactiveOnDate = recordsOnSameDate.stream()
                .filter(r -> !r.isCurrent())
                .findFirst();

        if (existingInactiveOnDate.isPresent()) {
            // A current=false record already exists for this date.
            // That means a previous recalculation pass already closed a record here.
            // We must NOT create another current=false. Instead, promote this
            // existing inactive record back to current=true with the new values.
            EmployeePositionHistory existing = existingInactiveOnDate.get();
            existing.setDepartment(department);
            existing.setJobStep(jobStep);
            existing.setPayGroup(payGroup);
            existing.setEffectiveTo(null);
            existing.setCurrent(true);
            existing.setStatus(RecordStatus.ACTIVE);
            return positionRepository.save(existing);
        }

        // ---------------------------------------------------------
        // STEP 4: Clean path — no records exist for this date yet.
        // Insert a fresh current=true record.
        // ---------------------------------------------------------
        EmployeePositionHistory newPosition = EmployeePositionHistory.builder()
                .employee(employee)
                .department(department)
                .jobStep(jobStep)
                .payGroup(payGroup)
                .effectiveFrom(effectiveDate)
                .current(true)
                .status(RecordStatus.ACTIVE)
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
        EmployeePositionHistory position = positionRepository
                .findByEmployee_IdAndCurrentTrue(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Active Position for Employee", employeeId));
        return mapper.toDto(position);
    }
}