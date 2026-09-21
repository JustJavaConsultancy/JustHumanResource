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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        validateTotalWeight(existingAssignments, toSave);
        validateHierarchyWeights(existingAssignments, toSave);

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
                            .parentDefinitionId(getParentDefinitionId(assignment.getKpi()))
                            .parentKpi(hasActiveChildDefinitions(assignment.getKpi()))
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
                            .parentDefinitionId(getParentDefinitionId(assignment.getKpi()))
                            .parentKpi(hasActiveChildDefinitions(assignment.getKpi()))
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
                            .parentDefinitionId(getParentDefinitionId(assignment.getKpi()))
                            .parentKpi(hasActiveChildDefinitions(assignment.getKpi()))
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
                            .parentDefinitionId(getParentDefinitionId(assignment.getKpi()))
                            .parentKpi(hasActiveChildDefinitions(assignment.getKpi()))
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
            List<KpiAssignment> incomingAssignments
    ) {
        List<KpiAssignment> combinedAssignments = new ArrayList<>(existingAssignments);
        combinedAssignments.addAll(incomingAssignments);

        BigDecimal total = calculateEffectiveTotalWeight(combinedAssignments);

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

    public BigDecimal calculateEffectiveTotalWeightFromResponses(List<KpiAssignmentResponseDTO> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Set<Long> assignedKpiIds = assignments.stream()
                .map(KpiAssignmentResponseDTO::getKpiId)
                .collect(Collectors.toSet());

        return assignments.stream()
                .filter(assignment -> assignment.getParentDefinitionId() == null
                        || !assignedKpiIds.contains(assignment.getParentDefinitionId()))
                .map(KpiAssignmentResponseDTO::getWeight)
                .filter(weight -> weight != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateEffectiveTotalWeight(List<KpiAssignment> assignments) {
        Set<Long> assignedKpiIds = assignments.stream()
                .map(assignment -> assignment.getKpi().getId())
                .collect(Collectors.toSet());

        return assignments.stream()
                .filter(assignment -> {
                    KpiDefinition parent = assignment.getKpi().getParentDefinition();
                    return parent == null || !assignedKpiIds.contains(parent.getId());
                })
                .map(KpiAssignment::getWeight)
                .filter(weight -> weight != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Long getParentDefinitionId(KpiDefinition kpi) {
        return kpi.getParentDefinition() != null ? kpi.getParentDefinition().getId() : null;
    }

    private boolean hasActiveChildDefinitions(KpiDefinition kpi) {
        return kpi.getChildren() != null && kpi.getChildren().stream().anyMatch(KpiDefinition::isActive);
    }

    private void validateHierarchyWeights(
            List<KpiAssignment> existingAssignments,
            List<KpiAssignment> incomingAssignments
    ) {
        List<KpiAssignment> combinedAssignments = new ArrayList<>(existingAssignments);
        combinedAssignments.addAll(incomingAssignments);

        Map<Long, KpiAssignment> assignmentsByKpiId = new HashMap<>();
        for (KpiAssignment assignment : combinedAssignments) {
            assignmentsByKpiId.put(assignment.getKpi().getId(), assignment);
        }

        for (KpiAssignment assignment : combinedAssignments) {
            KpiDefinition parent = assignment.getKpi().getParentDefinition();
            if (parent != null && !assignmentsByKpiId.containsKey(parent.getId())) {
                throw new IllegalArgumentException(
                        "Child KPI '" + assignment.getKpi().getName()
                                + "' requires parent KPI '" + parent.getName()
                                + "' to be assigned in the same scope."
                );
            }
        }

        for (KpiAssignment assignment : combinedAssignments) {
            KpiDefinition parentKpi = assignment.getKpi();
            boolean hasChildDefinitions = hasActiveChildDefinitions(parentKpi);

            if (!hasChildDefinitions) {
                continue;
            }

            BigDecimal childWeightTotal = combinedAssignments.stream()
                    .filter(childAssignment -> {
                        KpiDefinition childParent = childAssignment.getKpi().getParentDefinition();
                        return childParent != null && parentKpi.getId().equals(childParent.getId());
                    })
                    .map(KpiAssignment::getWeight)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (childWeightTotal.compareTo(assignment.getWeight()) != 0) {
                throw new IllegalArgumentException(
                        "Child KPI weights for parent '" + parentKpi.getName()
                                + "' must equal parent weight " + assignment.getWeight()
                                + ". Current child total is " + childWeightTotal + "."
                );
            }
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
