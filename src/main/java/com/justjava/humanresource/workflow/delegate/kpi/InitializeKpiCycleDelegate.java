package com.justjava.humanresource.workflow.delegate.kpi;

import com.justjava.humanresource.kpi.entity.KpiEvaluationCycle;
import com.justjava.humanresource.kpi.repositories.KpiEvaluationCycleRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;

@Component("initializeKpiCycleDelegate")
@RequiredArgsConstructor
@Transactional
public class InitializeKpiCycleDelegate implements JavaDelegate {

    private final KpiEvaluationCycleRepository repository;

    @Override
    public void execute(DelegateExecution execution) {

        YearMonth period = YearMonth.now();//.minusMonths(1);

        System.out.println("Checking for existing KPI cycle for period: " + period);
        repository.findByPeriod(period)
                .ifPresentOrElse(
                        existing ->
                                execution.setVariable("cycleId", existing.getId()),
                        () -> {
                            KpiEvaluationCycle cycle =
                                    repository.save(
                                            KpiEvaluationCycle.builder()
                                                    .period(period)
                                                    .started(true)
                                                    .startedAt(LocalDateTime.now())
                                                    .totalEmployees(0)
                                                    .processedEmployees(0)
                                                    .completed(false)
                                                    .build()
                                    );
                            execution.setVariable("cycleId", cycle.getId());
                        }
                );

        System.out.println(" Initialized KPI cycle for period:======" + period);
        execution.setVariable("evaluationPeriod", period);
    }
}
