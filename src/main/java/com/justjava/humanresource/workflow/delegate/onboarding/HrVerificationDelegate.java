package com.justjava.humanresource.workflow.delegate.onboarding;

import com.justjava.humanresource.onboarding.entity.EmployeeOnboarding;
import com.justjava.humanresource.onboarding.enums.OnboardingStatus;
import com.justjava.humanresource.onboarding.repositories.EmployeeOnboardingRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HrVerificationDelegate implements JavaDelegate {

    private final EmployeeOnboardingRepository onboardingRepository;

    @Override
    public void execute(DelegateExecution execution) {

        String processInstanceId = execution.getProcessInstanceId();

        EmployeeOnboarding onboarding =
                onboardingRepository.findByProcessInstanceId(processInstanceId)
                        .orElseThrow();

        onboarding.setStatus(OnboardingStatus.HR_VERIFIED);
        onboardingRepository.save(onboarding);
    }
}

