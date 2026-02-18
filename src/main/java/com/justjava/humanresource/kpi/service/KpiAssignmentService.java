package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.hr.dto.KpiAssignmentItemRequestDTO;
import com.justjava.humanresource.hr.dto.KpiAssignmentResponseDTO;
import com.justjava.humanresource.hr.dto.KpiBulkAssignmentRequestDTO;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.kpi.entity.KpiAssignment;
import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.repositories.KpiAssignmentRepository;
import com.justjava.humanresource.kpi.repositories.KpiDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class KpiAssignmentService {

    private final KpiAssignmentRepository repository;
    private final EmployeeRepository employeeRepository;
    private final JobStepRepository jobStepRepository;
    private final KpiDefinitionRepository kpiRepository;

    public List<KpiAssignmentResponseDTO> bulkAssign(KpiBulkAssignmentRequestDTO request) {

        if (request.getEmployeeId() == null && request.getJobStepId() == null) {
            throw new IllegalArgumentException("Either employeeId or jobStepId must be provided");
        }

        Employee employee = null;
        JobStep jobStep = null;

        if (request.getEmployeeId() != null) {
            employee = employeeRepository.findById(request.getEmployeeId())
                    .orElseThrow();
        }

        if (request.getJobStepId() != null) {
            jobStep = jobStepRepository.findById(request.getJobStepId())
                    .orElseThrow();
        }

        validateTotalWeight(request);

        List<KpiAssignment> toSave = new ArrayList<>();
        List<KpiAssignmentResponseDTO> response = new ArrayList<>();

        for (KpiAssignmentItemRequestDTO item : request.getKpis()) {

            validateWeight(item.getWeight());

            KpiDefinition kpi = kpiRepository.findById(item.getKpiId())
                    .orElseThrow();

            boolean exists;

            if (employee != null) {
                exists = repository
                        .findByEmployee_IdAndKpi_IdAndActiveTrue(
                                employee.getId(),
                                kpi.getId()
                        ).isPresent();
            } else {
                exists = repository
                        .findByJobStep_IdAndKpi_IdAndActiveTrue(
                                jobStep.getId(),
                                kpi.getId()
                        ).isPresent();
            }

            if (exists) {
                continue; // skip duplicate safely
            }

            KpiAssignment assignment = KpiAssignment.builder()
                    .kpi(kpi)
                    .employee(employee)
                    .jobStep(jobStep)
                    .weight(item.getWeight())
                    .mandatory(item.isMandatory())
                    .validFrom(LocalDate.now())
                    .active(true)
                    .build();

            toSave.add(assignment);
        }

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

    public List<KpiAssignment> getAllAssignments() {
        return repository.findAll();
    }
        /* ==============================
       INTERNAL VALIDATION
       ============================== */

    private void validateWeight(BigDecimal weight) {
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Weight must be positive.");
        }
    }

    private void validateTotalWeight(KpiBulkAssignmentRequestDTO request) {

        BigDecimal total = request.getKpis().stream()
                .map(KpiAssignmentItemRequestDTO::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "Total KPI weight cannot exceed 1.0 (100%)"
            );
        }
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