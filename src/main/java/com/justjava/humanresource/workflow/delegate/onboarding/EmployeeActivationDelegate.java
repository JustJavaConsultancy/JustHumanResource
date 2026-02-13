package com.justjava.humanresource.workflow.delegate.onboarding;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.onboarding.entity.EmployeeOnboarding;
import com.justjava.humanresource.onboarding.enums.OnboardingStatus;
import com.justjava.humanresource.onboarding.repositories.EmployeeOnboardingRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component("EmployeeActivationDelegate")
@RequiredArgsConstructor
public class EmployeeActivationDelegate implements JavaDelegate {

    private final EmployeeService employeeService;

    @Override
    public void execute(DelegateExecution execution) {

        Long employeeId = (Long) execution.getVariable("employeeId");

        /*
         * This call:
         * 1. Updates employment status
         * 2. Saves employee
         * 3. Publishes SalaryChangedEvent
         * 4. Payroll listener reacts automatically
         */
        employeeService.changeEmploymentStatus(
                employeeId,
                EmploymentStatus.ACTIVE,
                LocalDate.now()
        );

        execution.setVariable("employeeActivated", true);
    }
}


