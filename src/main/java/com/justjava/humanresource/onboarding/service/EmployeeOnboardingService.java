package com.justjava.humanresource.onboarding.service;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.core.exception.ResourceNotFoundException;
import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.dto.EmployeeOnboardingResponseDTO;
import com.justjava.humanresource.hr.entity.*;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.hr.repository.PayGroupRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeOnboardingService {

    private final EmployeeRepository employeeRepository;
    private final RuntimeService runtimeService;
    private final EmployeeService employeeService;   // domain service
    private final EmployeeOnboardingRepository onboardingRepository;
    private final DepartmentRepository departmentRepository;
    private final JobStepRepository jobStepRepository;
    private final PayGroupRepository payGroupRepository;

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

        dto.setNinNumber(command.getNinNumber());
        dto.setBvnNumber(command.getBvnNumber());
        dto.setNextOfKinName(command.getNextOfKinName());
        dto.setNextOfKinPhoneNumber(command.getNextOfKinPhoneNumber());
        dto.setNextOfKinEmail(command.getNextOfKinEmail());
        dto.setNextOfKinAddress(command.getNextOfKinAddress());
        dto.setGuarantorName(command.getGuarantorName());
        dto.setGuarantorPhoneNumber(command.getGuarantorPhoneNumber());
        dto.setGuarantorEmail(command.getGuarantorEmail());
        dto.setGuarantorAddress(command.getGuarantorAddress());
        dto.setGuarantorNinNumber(command.getGuarantorNinNumber());

        dto.setEmploymentStatus(String.valueOf(EmploymentStatus.ONBOARDING));
        dto.setJobStepId(command.getJobStepId());
        dto.setPayGroupId(command.getPayGroupId());
        dto.setStatus(String.valueOf(RecordStatus.INACTIVE));

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

    @Transactional
    public List<Employee> getAllOnboardings() {
        List<EmployeeOnboarding> onboardings = onboardingRepository.findAll();
        return onboardings.stream()
                .map(EmployeeOnboarding::getEmployee)
                .toList();
    }


    @Transactional
    public void updateEmployee(Long id, EmployeeDTO dto) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found",id));

        if (dto.getFirstName() != null) employee.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) employee.setLastName(dto.getLastName());
        if (dto.getEmail() != null) employee.setEmail(dto.getEmail());
        if (dto.getPhoneNumber() != null) employee.setPhoneNumber(dto.getPhoneNumber());
        if (dto.getEmploymentStatus() != null) {
            employee.setEmploymentStatus(
                    EmploymentStatus.valueOf(dto.getEmploymentStatus().toUpperCase())
            );
        }

        if (dto.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found", dto.getDepartmentId()));
            employee.setDepartment(dept);
        }
        if (dto.getJobStepId() != null) {
            JobStep step = jobStepRepository.findById(dto.getJobStepId())
                    .orElseThrow(() -> new ResourceNotFoundException("JobStep not found", dto.getJobStepId()));
            employee.setJobStep(step);
        }
        if (dto.getPayGroupId() != null) {
            PayGroup payGroup = payGroupRepository.findById(dto.getPayGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("PayGroup not found", dto.getPayGroupId()));
            employee.setPayGroup(payGroup);
        }

        // identity fields
        if (dto.getNinNumber() != null) employee.setNinNumber(dto.getNinNumber());
        if (dto.getBvnNumber() != null) employee.setBvnNumber(dto.getBvnNumber());

        // next of kin fields
        if (dto.getNextOfKinName() != null) employee.setNextOfKinName(dto.getNextOfKinName());
        if (dto.getNextOfKinPhoneNumber() != null) employee.setNextOfKinPhoneNumber(dto.getNextOfKinPhoneNumber());
        if (dto.getNextOfKinEmail() != null) employee.setNextOfKinEmail(dto.getNextOfKinEmail());
        if (dto.getNextOfKinAddress() != null) employee.setNextOfKinAddress(dto.getNextOfKinAddress());

        // guarantor fields
        if (dto.getGuarantorName() != null) employee.setGuarantorName(dto.getGuarantorName());
        if (dto.getGuarantorPhoneNumber() != null) employee.setGuarantorPhoneNumber(dto.getGuarantorPhoneNumber());
        if (dto.getGuarantorEmail() != null) employee.setGuarantorEmail(dto.getGuarantorEmail());
        if (dto.getGuarantorAddress() != null) employee.setGuarantorAddress(dto.getGuarantorAddress());
        if (dto.getGuarantorNinNumber() != null) employee.setGuarantorNinNumber(dto.getGuarantorNinNumber());

        employeeRepository.save(employee); // explicit save

        // Handle bank details — delegate to EmployeeService which manages
        // deactivating old records and creating a new active one
        boolean hasBankData = (dto.getAccountName() != null && !dto.getAccountName().isBlank())
                || (dto.getBankName() != null && !dto.getBankName().isBlank())
                || (dto.getAccountNumber() != null && !dto.getAccountNumber().isBlank());

        if (hasBankData) {
            employeeService.updateBankDetails(id, dto);
        }
    }
}