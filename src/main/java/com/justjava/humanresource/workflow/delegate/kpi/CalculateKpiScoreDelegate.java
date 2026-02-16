package com.justjava.humanresource.workflow.delegate.kpi;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.entity.KpiMeasurement;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import com.justjava.humanresource.kpi.repositories.KpiMeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import java.math.RoundingMode;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Component("calculateKpiScoreDelegate")
@RequiredArgsConstructor
public class CalculateKpiScoreDelegate implements JavaDelegate {

    private final KpiMeasurementRepository measurementRepository;
    private final EmployeeAppraisalRepository appraisalRepository;
    private final EmployeeService employeeService;

    @Override
    public void execute(DelegateExecution execution) {

        Long employeeId = (Long) execution.getVariable("employeeId");
        Employee employee = employeeService.getByEmployeeNumber(String.valueOf(employeeId));
        YearMonth period = (YearMonth) execution.getVariable("period");

        List<KpiMeasurement> measurements =
                measurementRepository.findByEmployee_IdAndPeriod(employeeId, period);

        BigDecimal averageScore =
                measurements.stream()
                        .map(KpiMeasurement::getScore)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(
                                BigDecimal.valueOf(Math.max(measurements.size(), 1)),
                                2,
                                RoundingMode.HALF_UP
                        );

        EmployeeAppraisal appraisal =
                appraisalRepository.save(
                        EmployeeAppraisal.builder()
                                .employee(employee)
                                .kpiScore(averageScore)
                                .build()
                );

        execution.setVariable("appraisalId", appraisal.getId());
    }
}
