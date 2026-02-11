package com.justjava.humanresource.workflow.delegate.onboarding;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.onboarding.entity.EmployeeOnboarding;
import com.justjava.humanresource.onboarding.enums.OnboardingStatus;
import com.justjava.humanresource.onboarding.repositories.EmployeeOnboardingRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class EmployeeActivationDelegate implements JavaDelegate {

    private final EmployeeRepository employeeRepository;
    private final EmployeeOnboardingRepository onboardingRepository;

    @Override
    @Transactional
    public void execute(DelegateExecution execution) {

        EmployeeOnboarding onboarding =
                onboardingRepository
                        .findByProcessInstanceId(execution.getProcessInstanceId())
                        .orElseThrow();

        Employee employee = onboarding.getEmployee();

        employee.setEmploymentStatus(EmploymentStatus.CONFIRMED);
        employee.setPayrollEnabled(true);

        onboarding.setStatus(OnboardingStatus.COMPLETED);
        onboarding.setCompletedAt(LocalDateTime.now());

        employeeRepository.save(employee);
        onboardingRepository.save(onboarding);

        // 🔔 payroll-safe event hook
        execution.setVariable("employeeActivated", true);
    }
}

