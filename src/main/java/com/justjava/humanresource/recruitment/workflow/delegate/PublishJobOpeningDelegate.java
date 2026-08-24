package com.justjava.humanresource.recruitment.workflow.delegate;

import com.justjava.humanresource.recruitment.service.RecruitmentService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("publishJobOpeningDelegate") @RequiredArgsConstructor
public class PublishJobOpeningDelegate implements JavaDelegate {
    private final RecruitmentService recruitmentService;
    public void execute(DelegateExecution execution) {
        recruitmentService.publish(((Number) execution.getVariable("jobOpeningId")).longValue());
    }
}
