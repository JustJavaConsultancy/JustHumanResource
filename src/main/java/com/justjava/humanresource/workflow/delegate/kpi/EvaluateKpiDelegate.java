package com.justjava.humanresource.workflow.delegate.kpi;

import com.justjava.humanresource.kpi.entity.KpiMeasurement;
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

    private final KpiMeasurementRepository measurementRepository;

    @Override
    public void execute(DelegateExecution execution) {

        Long employeeId = (Long) execution.getVariable("employeeId");
        YearMonth period = (YearMonth) execution.getVariable("evaluationPeriod");

        List<KpiMeasurement> measurements =
                measurementRepository.findByEmployee_IdAndPeriod(employeeId, period);

        BigDecimal totalScore = measurements.stream()
                .map(KpiMeasurement::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageScore =
                measurements.isEmpty()
                        ? BigDecimal.ZERO
                        : totalScore.divide(
                        BigDecimal.valueOf(measurements.size()),
                        2,
                        RoundingMode.HALF_UP);

        execution.setVariable("kpiAverageScore", averageScore);
    }
}

