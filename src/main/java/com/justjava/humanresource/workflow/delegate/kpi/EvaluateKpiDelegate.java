package com.justjava.humanresource.workflow.delegate.kpi;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.kpi.entity.KpiAssignment;
import com.justjava.humanresource.kpi.entity.KpiMeasurement;
import com.justjava.humanresource.kpi.repositories.KpiAssignmentRepository;
import com.justjava.humanresource.kpi.repositories.KpiMeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EvaluateKpiDelegate implements JavaDelegate {

    private final KpiAssignmentRepository assignmentRepository;
    private final KpiMeasurementRepository measurementRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public void execute(DelegateExecution execution) {

        Long employeeId = (Long) execution.getVariable("employeeId");
        YearMonth period =
                (YearMonth) execution.getVariable("evaluationPeriod");

        Employee employee =
                employeeRepository.findById(employeeId).orElseThrow();

        List<KpiAssignment> assignments =
                assignmentRepository.findEffectiveAssignmentsForEmployee(
                        employeeId,
                        //employee.getJobStep().getId(),
                        period.atEndOfMonth()
                );

        List<KpiMeasurement> measurements =
                measurementRepository.findByEmployee_IdAndPeriod(employeeId, period);

        BigDecimal weightedTotal = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (KpiAssignment assignment : assignments) {

            BigDecimal weight = assignment.getWeight();
            totalWeight = totalWeight.add(weight);

            BigDecimal score =
                    measurements.stream()
                            .filter(m -> m.getKpi().getId()
                                    .equals(assignment.getKpi().getId()))
                            .map(KpiMeasurement::getScore)
                            .findFirst()
                            .orElse(BigDecimal.ZERO);

            weightedTotal = weightedTotal.add(score.multiply(weight));
        }

        BigDecimal finalScore =
                totalWeight.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : weightedTotal.divide(totalWeight, 2, RoundingMode.HALF_UP);

        execution.setVariable("kpiAverageScore", finalScore);
    }
}
