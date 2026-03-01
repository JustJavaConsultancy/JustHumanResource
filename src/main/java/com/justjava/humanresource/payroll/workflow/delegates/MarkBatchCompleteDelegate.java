package com.justjava.humanresource.payroll.workflow.delegates;

import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("markBatchCompleteDelegate")
@RequiredArgsConstructor
public class MarkBatchCompleteDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {

        execution.setVariable("batchCompleted", true);
    }
}