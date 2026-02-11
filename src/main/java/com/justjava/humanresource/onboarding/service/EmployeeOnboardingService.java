package com.justjava.humanresource.onboarding.service;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.onboarding.dto.StartEmployeeOnboardingCommand;
import com.justjava.humanresource.onboarding.entity.EmployeeOnboarding;
import com.justjava.humanresource.onboarding.enums.OnboardingStatus;
import com.justjava.humanresource.onboarding.repositories.EmployeeOnboardingRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeOnboardingService {

    private final RuntimeService runtimeService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeOnboardingRepository onboardingRepository;

    public EmployeeOnboarding startOnboarding(
            StartEmployeeOnboardingCommand command,
            String initiatedBy
    ) {

        Employee employee = Employee.builder()
                .employeeNumber(UUID.randomUUID().toString())
                .firstName(command.getFirstName())
                .lastName(command.getLastName())
                .email(command.getEmail())
                .phoneNumber(command.getPhoneNumber())
                .employmentStatus(EmploymentStatus.ONBOARDING)
                .payrollEnabled(false)
                .build();

        employeeRepository.save(employee);

        Map<String, Object> variables = new HashMap<>();
        variables.put("employeeId", employee.getId());
        variables.put("initiatedBy", initiatedBy);

        ProcessInstance processInstance =
                runtimeService.startProcessInstanceByKey(
                        "employeeOnboardingProcess",
                        variables
                );

        EmployeeOnboarding onboarding = EmployeeOnboarding.builder()
                .employee(employee)
                .processInstanceId(processInstance.getProcessInstanceId())
                .status(OnboardingStatus.INITIATED)
                .initiatedBy(initiatedBy)
                .build();

        return onboardingRepository.save(onboarding);
    }
}

