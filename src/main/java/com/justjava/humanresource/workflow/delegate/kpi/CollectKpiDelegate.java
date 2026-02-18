package com.justjava.humanresource.workflow.delegate.kpi;

import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
@RequiredArgsConstructor
public class CollectKpiDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        // Period already passed from orchestrator.
        // This delegate should trigger actual KPI data collection logic.
        // Example:
        // call external API, compute system metrics, etc.

        // For now, do nothing.    }
    }
}

