package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.hr.dto.KpiAssignmentItemRequestDTO;
import com.justjava.humanresource.hr.dto.KpiAssignmentResponseDTO;
import com.justjava.humanresource.hr.dto.KpiBulkAssignmentRequestDTO;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.kpi.entity.KpiAssignment;
import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.repositories.KpiAssignmentRepository;
import com.justjava.humanresource.kpi.repositories.KpiDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class KpiAssignmentService {

    private final KpiAssignmentRepository repository;
    private final EmployeeRepository employeeRepository;
    private final JobStepRepository jobStepRepository;
    private final KpiDefinitionRepository kpiRepository;
    private final DepartmentRepository departmentRepository;

    @Value("${app.kpi.max-kpi-weight:1.0}")
    private BigDecimal maxKpiWeight;

    public List<KpiAssignmentResponseDTO> bulkAssign(KpiBulkAssignmentRequestDTO request) {

        if (request.getEmployeeId() == null
                && request.getJobStepId() == null
                && request.getDepartmentId() == null) {
            throw new IllegalArgumentException(
                    "Either employeeId, jobStepId, or departmentId must be provided");
        }

        Employee employee = null;
        JobStep jobStep = null;
        Department department = null;

        if (request.getEmployeeId() != null) {
            employee = employeeRepository.findById(request.getEmployeeId())
                    .orElseThrow();
        }

        if (request.getJobStepId() != null) {
            jobStep = jobStepRepository.findById(request.getJobStepId())
                    .orElseThrow();
        }

        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow();
        }

        validateKpiWeightSetting();
        validateRequestKpis(request);

        List<KpiAssignment> existingAssignments;
        if (employee != null) {
            existingAssignments = repository.findByEmployee_IdAndActiveTrue(employee.getId());
        } else if (jobStep != null) {
            existingAssignments = repository.findByJobStep_IdAndActiveTrue(jobStep.getId());
        } else {
            existingAssignments = repository.findByDepartment_IdAndActiveTrue(department.getId());
        }

        List<KpiAssignment> toSave = new ArrayList<>();
        List<KpiAssignmentResponseDTO> response = new ArrayList<>();
        BigDecimal incomingWeightToAdd = BigDecimal.ZERO;
        Set<Long> existingKpiIds = existingAssignments.stream()
                .map(a -> a.getKpi().getId())
                .collect(Collectors.toSet());

        for (KpiAssignmentItemRequestDTO item : request.getKpis()) {

            validateWeight(item.getWeight());

            KpiDefinition kpi = kpiRepository.findById(item.getKpiId())
                    .orElseThrow();

            boolean exists = existingKpiIds.contains(kpi.getId());

            if (exists) {
                continue; // skip duplicate safely
            }

            incomingWeightToAdd = incomingWeightToAdd.add(item.getWeight());
            existingKpiIds.add(kpi.getId());

            KpiAssignment assignment = KpiAssignment.builder()
                    .kpi(kpi)
                    .employee(employee)
                    .jobStep(jobStep)
                    .department(department)
                    .weight(item.getWeight())
                    .mandatory(item.isMandatory())
                    .validFrom(LocalDate.now())
                    .active(true)
                    .build();

            toSave.add(assignment);
        }

        validateTotalWeight(existingAssignments, incomingWeightToAdd);

        List<KpiAssignment> saved = repository.saveAll(toSave);

        for (KpiAssignment assignment : saved) {
            response.add(
                    KpiAssignmentResponseDTO.builder()
                            .assignmentId(assignment.getId())
                            .kpiId(assignment.getKpi().getId())
                            .kpiCode(assignment.getKpi().getCode())
                            .weight(assignment.getWeight())
                            .mandatory(assignment.isMandatory())
                            .name(assignment.getKpi().getName())
                            .build()
            );
        }

        return response;
    }
    @Transactional(readOnly = true)
    public List<KpiAssignmentResponseDTO> getAssignmentsForEmployee(Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow();

        LocalDate today = LocalDate.now();

        List<KpiAssignment> assignments =
                repository.findEffectiveAssignmentsForEmployee(
                        employeeId,
                        employee.getJobStep().getId(),
                        employee.getDepartment().getId(),
                        today
                );

        List<KpiAssignmentResponseDTO> response = new ArrayList<>();

        for (KpiAssignment assignment : assignments) {

            response.add(

                    KpiAssignmentResponseDTO.builder()
                            .assignmentId(assignment.getId())
                            .kpiId(assignment.getKpi().getId())
                            .kpiCode(assignment.getKpi().getCode())
                            .weight(assignment.getWeight())
                            .mandatory(assignment.isMandatory())
                            .name(assignment.getKpi().getName())
                            .targetValue(assignment.getKpi().getTargetValue())
                            .kpiUnit(assignment.getKpi().getUnit())
                            .build()
            );
        }

        return response;
    }
    @Transactional(readOnly = true)
    public List<KpiAssignmentResponseDTO> getAssignmentsForJobStep(Long jobStepId) {

        LocalDate today = LocalDate.now();

        List<KpiAssignment> assignments =
                repository.findEffectiveAssignmentsForJobStep(
                        jobStepId,
                        today
                );

        List<KpiAssignmentResponseDTO> response = new ArrayList<>();

        for (KpiAssignment assignment : assignments) {

            response.add(

                    KpiAssignmentResponseDTO.builder()
                            .assignmentId(assignment.getId())
                            .kpiId(assignment.getKpi().getId())
                            .kpiCode(assignment.getKpi().getCode())
                            .weight(assignment.getWeight())
                            .mandatory(assignment.isMandatory())
                            .name(assignment.getKpi().getName())
                            .build()
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<KpiAssignmentResponseDTO> getAssignmentsForDepartment(Long departmentId) {
        List<KpiAssignment> assignments =
                repository.findByDepartment_IdAndActiveTrue(departmentId);

        List<KpiAssignmentResponseDTO> response = new ArrayList<>();
        for (KpiAssignment assignment : assignments) {
            response.add(
                    KpiAssignmentResponseDTO.builder()
                            .assignmentId(assignment.getId())
                            .kpiId(assignment.getKpi().getId())
                            .kpiCode(assignment.getKpi().getCode())
                            .weight(assignment.getWeight())
                            .mandatory(assignment.isMandatory())
                            .name(assignment.getKpi().getName())
                            .build()
            );
        }
        return response;
    }

    public List<KpiAssignment> getAllAssignments() {
        return repository.findAll();
    }
        /* ==============================
       INTERNAL VALIDATION
       ============================== */

    private void validateWeight(BigDecimal weight) {
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0
                || weight.compareTo(maxKpiWeight) > 0) {
            throw new IllegalArgumentException(
                    "Weight must be greater than 0 and not exceed configured max: " + maxKpiWeight
            );
        }
    }

    private void validateTotalWeight(
            List<KpiAssignment> existingAssignments,
            BigDecimal incomingWeightToAdd
    ) {
        BigDecimal existingWeight = existingAssignments.stream()
                .map(KpiAssignment::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = existingWeight.add(incomingWeightToAdd);

        if (total.compareTo(maxKpiWeight) > 0) {
            throw new IllegalArgumentException(
                    "Total KPI weight cannot exceed configured max: " + maxKpiWeight
            );
        }
    }

    private void validateKpiWeightSetting() {
        if (maxKpiWeight == null || maxKpiWeight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Configured app.kpi.max-kpi-weight must be greater than zero.");
        }
    }

    private void validateRequestKpis(KpiBulkAssignmentRequestDTO request) {
        if (request.getKpis() == null || request.getKpis().isEmpty()) {
            throw new IllegalArgumentException("At least one KPI assignment item is required.");
        }
    }

    public BigDecimal getMaxKpiWeight() {
        validateKpiWeightSetting();
        return maxKpiWeight;
    }
}

/*****
 {
     "employeeId": 12,
     "jobStepId": null,
     "kpis": [
         {
         "kpiId": 3,
         "weight": 0.40,
         "mandatory": true
         },
         {
         "kpiId": 5,
         "weight": 0.35,
         "mandatory": true
         },
         {
         "kpiId": 7,
         "weight": 0.25,
         "mandatory": false
         }
     ]
 }
 */
