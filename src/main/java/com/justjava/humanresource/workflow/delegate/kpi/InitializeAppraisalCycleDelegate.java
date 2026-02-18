package com.justjava.humanresource.workflow.delegate.kpi;

import com.justjava.humanresource.kpi.entity.AppraisalCycle;
import com.justjava.humanresource.kpi.repositories.AppraisalCycleRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;

@Component("initializeAppraisalCycleDelegate")
@RequiredArgsConstructor
@Transactional
public class InitializeAppraisalCycleDelegate implements JavaDelegate {

    private final AppraisalCycleRepository repository;

    @Override
    public void execute(DelegateExecution execution) {

        YearMonth now = YearMonth.now();

        int quarter = (now.getMonthValue() - 1) / 3 + 1;
        int year = now.getYear();

        int startMonth = (quarter - 1) * 3 + 1;

        YearMonth startPeriod = YearMonth.of(year, startMonth);
        YearMonth endPeriod = startPeriod.plusMonths(2);

        String name = year + " Q" + quarter;

        repository.findByYearAndQuarter(year, quarter)
                .ifPresentOrElse(
                        existing -> execution.setVariable("cycleId", existing.getId()),
                        () -> {

                            AppraisalCycle cycle =
                                    repository.save(
                                            AppraisalCycle.builder()
                                                    .name(name)
                                                    .year(year)
                                                    .quarter(quarter)
                                                    .startPeriod(startPeriod)
                                                    .endPeriod(endPeriod)
                                                    .active(true)
                                                    .completed(false)
                                                    .startedAt(LocalDateTime.now())
                                                    .totalEmployees(0)
                                                    .processedEmployees(0)
                                                    .build()
                                    );

                            execution.setVariable("cycleId", cycle.getId());
                        }
                );

        execution.setVariable("appraisalYear", year);
        execution.setVariable("appraisalQuarter", quarter);
    }
}
