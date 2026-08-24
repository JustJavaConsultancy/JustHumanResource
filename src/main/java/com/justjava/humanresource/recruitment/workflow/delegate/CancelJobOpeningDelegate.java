package com.justjava.humanresource.recruitment.workflow.delegate;

import com.justjava.humanresource.recruitment.enums.JobOpeningStatus;
import com.justjava.humanresource.recruitment.repository.JobOpeningRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("cancelJobOpeningDelegate") @RequiredArgsConstructor
public class CancelJobOpeningDelegate implements JavaDelegate {
    private final JobOpeningRepository repository;
    public void execute(DelegateExecution execution) {
        Long id = ((Number) execution.getVariable("jobOpeningId")).longValue();
        var opening = repository.findById(id).orElseThrow();
        opening.setStatus(JobOpeningStatus.CANCELLED);
        repository.save(opening);
    }
}
