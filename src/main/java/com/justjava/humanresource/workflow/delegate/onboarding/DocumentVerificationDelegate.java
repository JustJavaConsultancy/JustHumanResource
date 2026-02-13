package com.justjava.humanresource.workflow.delegate.onboarding;

import com.justjava.humanresource.onboarding.entity.EmployeeOnboarding;
import com.justjava.humanresource.onboarding.enums.OnboardingStatus;
import com.justjava.humanresource.onboarding.repositories.EmployeeOnboardingRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("DocumentVerificationDelegate")
@RequiredArgsConstructor
public class DocumentVerificationDelegate implements JavaDelegate {

    private final EmployeeOnboardingRepository onboardingRepository;

    @Override
    public void execute(DelegateExecution execution) {

        Long onboardingId = (Long) execution.getVariable("onboardingId");

        EmployeeOnboarding onboarding =
                onboardingRepository.findById(onboardingId)
                        .orElseThrow();

        onboarding.setStatus(OnboardingStatus.DOCS_VERIFIED);
        onboardingRepository.save(onboarding);
    }
}

