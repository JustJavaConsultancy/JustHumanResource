package com.justjava.humanresource.workflow.delegate.kpi;

import com.justjava.humanresource.kpi.repositories.KpiMeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.YearMonth;


@Component
@RequiredArgsConstructor
public class EvaluateKpiDelegate implements JavaDelegate {

    private final KpiMeasurementRepository measurementRepository;

    @Override
    public void execute(DelegateExecution execution) {

        Long employeeId = Long.valueOf(
                execution.getVariable("employeeId").toString()
        );

        YearMonth period =
                (YearMonth) execution.getVariable("evaluationPeriod");
        System.out.println("Evaluating KPIs for employee " + employeeId + " for period " + period);
        boolean hasMeasurements =
                !measurementRepository
                        .findByEmployee_IdAndPeriod(employeeId, period)
                        .isEmpty();

        if (!hasMeasurements) {
            throw new IllegalStateException(
                    "No KPI measurements found for employee " + employeeId
            );
        }

        execution.setVariable("kpiEvaluated", true);
    }
}
