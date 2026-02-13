package com.justjava.humanresource.onboarding.service;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.dto.EmployeeOnboardingResponseDTO;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.service.EmployeeService;
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
    private final EmployeeService employeeService;   // ✅ use domain service
    private final EmployeeOnboardingRepository onboardingRepository;

    @Transactional
    public EmployeeOnboardingResponseDTO startOnboarding(
            StartEmployeeOnboardingCommand command,
            String initiatedBy
    ) {

        // 1️⃣ Create employee
        EmployeeDTO dto = new EmployeeDTO();
        dto.setEmployeeNumber(UUID.randomUUID().toString());
        dto.setDepartmentId(command.getDepartmentId());
        dto.setFirstName(command.getFirstName());
        dto.setLastName(command.getLastName());
        dto.setEmail(command.getEmail());
        dto.setPhoneNumber(command.getPhoneNumber());
        dto.setEmploymentStatus(EmploymentStatus.ONBOARDING);
        dto.setJobStepId(command.getJobStepId());
        dto.setPayGroupId(command.getPayGroupId());
        dto.setStatus(RecordStatus.INACTIVE);

        Employee employee = employeeService.createEmployee(dto);

        // 2️⃣ Create onboarding FIRST (without processInstanceId)
        EmployeeOnboarding onboarding = EmployeeOnboarding.builder()
                .employee(employee)
                .status(OnboardingStatus.INITIATED)
                .initiatedBy(initiatedBy)
                .build();

        onboarding = onboardingRepository.save(onboarding);

        // 🔥 IMPORTANT: Flush to DB
        onboardingRepository.flush();

        // 3️⃣ Start Flowable process AFTER save
        Map<String, Object> variables = new HashMap<>();
        variables.put("employeeId", employee.getId());
        variables.put("initiator", initiatedBy);
        variables.put("onboardingId", onboarding.getId());
        variables.put("approvalRequired", false);

        ProcessInstance processInstance =
                runtimeService.startProcessInstanceByKey(
                        "onboardingProcess",
                        variables
                );

        // 4️⃣ Update onboarding with processInstanceId
        onboarding.setProcessInstanceId(processInstance.getProcessInstanceId());

        EmployeeOnboarding saved = onboardingRepository.save(onboarding);

        return EmployeeOnboardingResponseDTO.builder()
                .id(saved.getId())
                .employeeId(saved.getEmployee().getId())
                .processInstanceId(saved.getProcessInstanceId())
                .status(saved.getStatus())
                .initiatedBy(saved.getInitiatedBy())
                .build();
    }

}
