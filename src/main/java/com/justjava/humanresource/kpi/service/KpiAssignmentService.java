package com.justjava.humanresource.kpi.service;

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

@Service
@RequiredArgsConstructor
@Transactional
public class KpiAssignmentService {

    private final KpiAssignmentRepository repository;
    private final EmployeeRepository employeeRepository;
    private final JobStepRepository jobStepRepository;
    private final KpiDefinitionRepository kpiRepository;

    public KpiAssignment assignToEmployee(
            Long employeeId,
            Long kpiId,
            BigDecimal weight
    ) {

        validateWeight(weight);

        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        KpiDefinition kpi = kpiRepository.findById(kpiId).orElseThrow();

        return repository.save(
                KpiAssignment.builder()
                        .kpi(kpi)
                        .employee(employee)
                        .weight(weight)
                        .mandatory(true)
                        .validFrom(LocalDate.now())
                        .active(true)
                        .build()
        );
    }

    public KpiAssignment assignToJobStep(
            Long jobStepId,
            Long kpiId,
            BigDecimal weight
    ) {

        validateWeight(weight);

        JobStep jobStep = jobStepRepository.findById(jobStepId).orElseThrow();
        KpiDefinition kpi = kpiRepository.findById(kpiId).orElseThrow();

        return repository.save(
                KpiAssignment.builder()
                        .kpi(kpi)
                        .jobStep(jobStep)
                        .weight(weight)
                        .mandatory(true)
                        .validFrom(LocalDate.now())
                        .active(true)
                        .build()
        );
    }

    private void validateWeight(BigDecimal weight) {
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Weight must be positive.");
    }
}

