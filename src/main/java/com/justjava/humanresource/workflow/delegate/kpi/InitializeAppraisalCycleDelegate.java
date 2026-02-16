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

        YearMonth current = YearMonth.now();
        int quarter = (current.getMonthValue() - 1) / 3 + 1;

        String name = current.getYear() + " Q" + quarter;

        repository.findByPeriod(current)
                .ifPresentOrElse(
                        existing -> execution.setVariable("cycleId", existing.getId()),
                        () -> {
                            AppraisalCycle cycle =
                                    repository.save(
                                            AppraisalCycle.builder()
                                                    .name(name)
                                                    .period(current)
                                                    .active(true)
                                                    .startedAt(LocalDateTime.now())
                                                    .completed(false)
                                                    .totalEmployees(0)
                                                    .processedEmployees(0)
                                                    .build()
                                    );

                            execution.setVariable("cycleId", cycle.getId());
                        }
                );

        execution.setVariable("appraisalPeriod", current);
    }
}
