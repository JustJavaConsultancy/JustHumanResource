package com.justjava.humanresource.workflow.delegate.kpi;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PublishKpiResultDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {

        BigDecimal score =
                (BigDecimal) execution.getVariable("kpiAverageScore");

        if (score.compareTo(BigDecimal.valueOf(85)) >= 0) {
            execution.setVariable("eligibleForBonus", true);
        } else {
            execution.setVariable("eligibleForBonus", false);
        }
    }
}

