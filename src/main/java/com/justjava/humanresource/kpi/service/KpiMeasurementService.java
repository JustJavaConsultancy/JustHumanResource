package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.entity.KpiMeasurement;
import com.justjava.humanresource.kpi.repositories.KpiAssignmentRepository;
import com.justjava.humanresource.kpi.repositories.KpiDefinitionRepository;
import com.justjava.humanresource.kpi.repositories.KpiMeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Transactional
public class KpiMeasurementService {

    private final KpiMeasurementRepository measurementRepository;
    private final KpiAssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final KpiDefinitionRepository kpiRepository;

    public KpiMeasurement recordMeasurement(
            Long employeeId,
            Long kpiId,
            BigDecimal actualValue,
            YearMonth period
    ) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow();

        KpiDefinition kpi = kpiRepository.findById(kpiId)
                .orElseThrow();

        validateAssignmentExists(employee, kpi);

        // prevent duplicate measurement for same period
        measurementRepository.findByEmployeeAndKpiAndPeriod(employee, kpi, period)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Measurement already exists for period " + period
                    );
                });

        BigDecimal score = calculateScore(actualValue, kpi.getTargetValue());

        return measurementRepository.save(
                KpiMeasurement.builder()
                        .employee(employee)
                        .kpi(kpi)
                        .actualValue(actualValue)
                        .score(score)
                        .period(period)
                        .recordedAt(LocalDateTime.now())
                        .build()
        );
    }

    private void validateAssignmentExists(Employee employee, KpiDefinition kpi) {

        boolean assigned = assignmentRepository
                .existsEffectiveAssignment(employee.getId(), employee.getJobStep().getId(), kpi.getId());

        if (!assigned) {
            throw new IllegalStateException("KPI not assigned to employee or role.");
        }
    }

    private BigDecimal calculateScore(BigDecimal actual, BigDecimal target) {

        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0)
            return BigDecimal.ZERO;

        return actual
                .divide(target, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .min(BigDecimal.valueOf(100));
    }
}
