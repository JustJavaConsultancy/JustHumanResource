package com.justjava.humanresource.workflow.delegate.kpi;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
@Component
public class PublishKpiResultDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {

        // Just mark KPI evaluation completed.
        execution.setVariable("kpiEvaluationCompleted", true);
    }
}
