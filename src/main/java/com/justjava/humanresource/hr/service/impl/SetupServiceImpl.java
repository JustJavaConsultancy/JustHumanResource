package com.justjava.humanresource.hr.service.impl;

import com.justjava.humanresource.core.exception.ResourceNotFoundException;
import com.justjava.humanresource.hr.dto.CreateJobGradeWithStepsCommand;
import com.justjava.humanresource.hr.dto.CreatePayGroupCommand;
import com.justjava.humanresource.hr.dto.JobGradeResponseDTO;
import com.justjava.humanresource.hr.dto.PayGroupResponseDTO;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.JobGrade;
import com.justjava.humanresource.hr.entity.JobStep;
//import com.justjava.humanresource.hr.entity.LeaveType;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.JobGradeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
//import com.justjava.humanresource.hr.repository.LeaveTypeRepository;
import com.justjava.humanresource.hr.repository.PayGroupRepository;
import com.justjava.humanresource.hr.service.SetupService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SetupServiceImpl implements SetupService {

    @PersistenceContext
    private EntityManager entityManager;
    private final DepartmentRepository departmentRepository;
    private final JobGradeRepository jobGradeRepository;
    private final JobStepRepository jobStepRepository;
    private final PayGroupRepository payGroupRepository;
    //private final LeaveTypeRepository leaveTypeRepository;

    /* ============================================================
       DEPARTMENT SETUP
       ============================================================ */

    @Override
    public Department createDepartment(String name) {
        // Fetch next sequence value
        Long nextVal = ((Number) entityManager
                .createNativeQuery("SELECT nextval('department_code_seq')")
                .getSingleResult()).longValue();

        String formattedCode = String.format("%06d", nextVal);

        Department department = new Department();
        department.setCode(formattedCode);
        department.setName(name);

        return departmentRepository.save(department);
    }

    /* ============================================================
       JOB GRADE + JOB STEPS SETUP
       ============================================================ */

    @Override
    public JobGradeResponseDTO createJobGradeWithSteps(CreateJobGradeWithStepsCommand command) {

        if (command.getSteps() == null || command.getSteps().isEmpty()) {
            throw new IllegalArgumentException(
                    "JobGrade must contain at least one JobStep"
            );
        }

        Department department = departmentRepository
                .findById(command.getDepartmentId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Department",
                                command.getDepartmentId()
                        )
                );

        // 1️⃣ Create JobGrade
        JobGrade jobGrade = new JobGrade();
        jobGrade.setName(command.getGradeName());
        jobGrade.setDepartment(department);

        JobGrade savedGrade = jobGradeRepository.save(jobGrade);

        // 2️⃣ Create JobSteps
        List<JobStep> steps = command.getSteps()
                .stream()
                .map(stepCommand -> {

                    JobStep step = new JobStep();
                    step.setName(stepCommand.getStepName());
                    step.setBasicSalary(stepCommand.getBasicSalary());
                    step.setDepartment(department);
                    step.setJobGrade(savedGrade);

                    return jobStepRepository.save(step);
                })
                .toList();

        savedGrade.setJobSteps(steps);

        // 3️⃣ Map to Response DTO
        return JobGradeResponseDTO.builder()
                .id(savedGrade.getId())
                .name(savedGrade.getName())
                .steps(
                        steps.stream()
                                .map(step ->
                                        JobGradeResponseDTO.JobStepSummaryDTO.builder()
                                                .id(step.getId())
                                                .name(step.getName())
                                                .build()
                                )
                                .toList()
                )
                .build();
    }

    /* ============================================================
       LEAVE TYPE SETUP
       ============================================================ */

    /*   @Override
       public LeaveType createLeaveType(
               String code,
               String name,
               int entitlementDays,
               boolean paid,
               boolean requiresApproval) {

           LeaveType leaveType = LeaveType.builder()
                   .code(code)
                   .name(name)
                   .annualEntitlementDays(entitlementDays)
                   .paid(paid)
                   .requiresApproval(requiresApproval)
                   .build();

           return leaveTypeRepository.save(leaveType);
       }*/
    @Override
    public PayGroupResponseDTO createPayGroup(CreatePayGroupCommand command) {
        String uniqueCode = "PG-" + UUID.randomUUID().toString().substring(0,8).toUpperCase();
        command.setCode(uniqueCode);
        if (command.getPayFrequency() == null) {
            throw new IllegalArgumentException("PayFrequency is required");
        }

        if (payGroupRepository.existsByCode(command.getCode())) {
            throw new IllegalArgumentException("PayGroup with code already exists");
        }

        PayGroup parent = null;

        if (command.getParentId() != null) {
            parent = payGroupRepository.findById(command.getParentId())
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "PayGroup",
                                    command.getParentId()
                            ));
        }

        PayGroup payGroup = new PayGroup();
        payGroup.setCode(command.getCode());
        payGroup.setName(command.getName());
        payGroup.setPayFrequency(command.getPayFrequency());
        payGroup.setParent(parent);

        PayGroup saved = payGroupRepository.save(payGroup);

        return PayGroupResponseDTO.builder()
                .id(saved.getId())
                .code(saved.getCode())
                .name(saved.getName())
                .payFrequency(saved.getPayFrequency())
                .parentId(parent != null ? parent.getId() : null)
                .build();
    }
}



// Use the below Query to create the SEQUENCE
/*
    CREATE SEQUENCE department_code_seq
        START WITH 100001
        INCREMENT BY 1;
*/
