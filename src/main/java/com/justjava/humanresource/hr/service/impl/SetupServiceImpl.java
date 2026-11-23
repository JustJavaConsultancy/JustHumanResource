package com.justjava.humanresource.hr.service.impl;

import com.justjava.humanresource.core.exception.ResourceNotFoundException;
import com.justjava.humanresource.hr.dto.CreateJobGradeWithStepsCommand;
import com.justjava.humanresource.hr.dto.CreatePayGroupCommand;
import com.justjava.humanresource.hr.dto.JobGradeResponseDTO;
import com.justjava.humanresource.hr.dto.PayGroupResponseDTO;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.JobGrade;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobGradeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.hr.repository.PayGroupRepository;
import com.justjava.humanresource.hr.service.SetupService;
import com.justjava.humanresource.payroll.service.PayrollChangeOrchestrator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SetupServiceImpl implements SetupService {

    @PersistenceContext
    private EntityManager entityManager;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final JobGradeRepository jobGradeRepository;
    private final JobStepRepository jobStepRepository;
    private final PayGroupRepository payGroupRepository;
    private final PayrollChangeOrchestrator payrollChangeOrchestrator;

    /* ============================================================
       DEPARTMENT SETUP
       ============================================================ */

    @Override
    public Department createDepartment(String name) {
        Long nextVal = ((Number) entityManager
                .createNativeQuery("SELECT nextval('department_code_seq')")
                .getSingleResult()).longValue();

        String formattedCode = String.format("%06d", nextVal);

        Department department = new Department();
        department.setCode(formattedCode);
        department.setName(name);

        return departmentRepository.save(department);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    /* ============================================================
       JOB GRADE + JOB STEPS SETUP
       ============================================================ */

    @Override
    public JobGradeResponseDTO createJobGradeWithSteps(CreateJobGradeWithStepsCommand command) {

        if (command.getSteps() == null || command.getSteps().isEmpty()) {
            throw new IllegalArgumentException("JobGrade must contain at least one JobStep");
        }

        Department department = departmentRepository
                .findById(command.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department"));

        JobGrade jobGrade = new JobGrade();
        jobGrade.setName(command.getGradeName());
        jobGrade.setDepartment(department);
        JobGrade savedGrade = jobGradeRepository.save(jobGrade);

        List<JobStep> steps = command.getSteps()
                .stream()
                .map(stepCommand -> {
                    JobStep step = new JobStep();
                    step.setName(stepCommand.getStepName());
                    BigDecimal divisor = stepCommand.isAnnual() ? BigDecimal.valueOf(12) : BigDecimal.ONE;
                    step.setBasicSalary(stepCommand.getBasicSalary() != null
                            ? stepCommand.getBasicSalary().divide(divisor, 5, RoundingMode.HALF_UP) : null);
                    step.setGrossSalary(stepCommand.getGrossSalary() != null
                            ? stepCommand.getGrossSalary().divide(divisor, 5, RoundingMode.HALF_UP) : null);
                    step.setDepartment(department);
                    step.setJobGrade(savedGrade);
                    return jobStepRepository.save(step);
                })
                .toList();

        savedGrade.setJobSteps(steps);

        return JobGradeResponseDTO.builder()
                .id(savedGrade.getId())
                .name(savedGrade.getName())
                .steps(steps.stream()
                        .map(step -> JobGradeResponseDTO.JobStepSummaryDTO.builder()
                                .id(step.getId())
                                .name(step.getName())
                                .basicSalary(step.getBasicSalary())
                                .build())
                        .toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobGradeResponseDTO> getAllJobGrades() {
        return jobGradeRepository.findAll().stream()
                .map(grade -> JobGradeResponseDTO.builder()
                        .id(grade.getId())
                        .name(grade.getName())
                        .departmentName(grade.getDepartment().getName())
                        .steps(grade.getJobSteps().stream()
                                .map(step -> JobGradeResponseDTO.JobStepSummaryDTO.builder()
                                        .id(step.getId())
                                        .name(step.getName())
                                        .basicSalary(step.getBasicSalary())
                                        .grossSalary(step.getGrossSalary())
                                        .build())
                                .toList())
                        .build())
                .toList();
    }

    /* ============================================================
       PAY GROUP SETUP
       ============================================================ */

    @Override
    public PayGroupResponseDTO createPayGroup(CreatePayGroupCommand command) {
        String uniqueCode = "PG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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
                    .orElseThrow(() -> new ResourceNotFoundException("PayGroup"));
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

    @Override
    public PayGroupResponseDTO updatePayGroup(Long id, CreatePayGroupCommand command) {
        PayGroup payGroup = payGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PayGroup"));

        PayGroup parent = null;
        if (command.getParentId() != null) {
            parent = payGroupRepository.findById(command.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("PayGroup"));
        }

        payGroup.setName(command.getName());
        payGroup.setPayFrequency(command.getPayFrequency());
        payGroup.setParent(parent);
        PayGroup saved = payGroupRepository.save(payGroup);

        payrollChangeOrchestrator.recalculateForPayGroup(saved.getId(), LocalDate.now());

        return PayGroupResponseDTO.builder()
                .id(saved.getId())
                .code(saved.getCode())
                .name(saved.getName())
                .payFrequency(saved.getPayFrequency())
                .parentId(parent != null ? parent.getId() : null)
                .build();
    }

    /* ============================================================
       UPDATE JOB GRADE WITH STEPS
       ============================================================ */

    @Override
    public JobGradeResponseDTO updateJobGradeWithSteps(Long gradeId, CreateJobGradeWithStepsCommand command) {

        if (command.getSteps() == null || command.getSteps().isEmpty()) {
            throw new IllegalArgumentException("JobGrade must contain at least one JobStep");
        }

        JobGrade jobGrade = jobGradeRepository.findById(gradeId)
                .orElseThrow(() -> new ResourceNotFoundException("JobGrade"));

        Department department = departmentRepository.findById(command.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department"));

        jobGrade.setName(command.getGradeName());
        jobGrade.setDepartment(department);
        jobGradeRepository.save(jobGrade);

        Map<Long, JobStep> existingStepsById = new HashMap<>();
        for (JobStep step : jobStepRepository.findByJobGrade(jobGrade)) {
            existingStepsById.put(step.getId(), step);
        }
        List<CreateJobGradeWithStepsCommand.JobStepCommand> incomingSteps = command.getSteps();

        List<JobStep> resultSteps = new ArrayList<>();
        List<Long> stepIdsToRecalculate = new ArrayList<>();

        for (CreateJobGradeWithStepsCommand.JobStepCommand stepCmd : incomingSteps) {
            BigDecimal divisor = stepCmd.isAnnual() ? BigDecimal.valueOf(12) : BigDecimal.ONE;

            BigDecimal basic = stepCmd.getBasicSalary() != null
                    ? stepCmd.getBasicSalary().divide(divisor, 5, RoundingMode.HALF_UP) : null;
            BigDecimal gross = stepCmd.getGrossSalary() != null
                    ? stepCmd.getGrossSalary().divide(divisor, 5, RoundingMode.HALF_UP) : null;

            if (stepCmd.getId() != null) {
                JobStep existing = existingStepsById.remove(stepCmd.getId());
                if (existing == null) {
                    throw new IllegalArgumentException(
                            "JobStep " + stepCmd.getId() + " does not belong to this grade or was submitted twice");
                }

                boolean payChanged = !sameAmount(existing.getBasicSalary(), basic)
                        || !sameAmount(existing.getGrossSalary(), gross);
                boolean departmentChanged = existing.getDepartment() == null
                        || !Objects.equals(existing.getDepartment().getId(), department.getId());
                existing.setName(stepCmd.getStepName());
                existing.setBasicSalary(basic);
                existing.setGrossSalary(gross);
                existing.setDepartment(department);
                JobStep saved = jobStepRepository.save(existing);
                resultSteps.add(saved);
                if (payChanged || departmentChanged) {
                    stepIdsToRecalculate.add(saved.getId());
                }
            } else {
                JobStep newStep = new JobStep();
                newStep.setName(stepCmd.getStepName());
                newStep.setBasicSalary(basic);
                newStep.setGrossSalary(gross);
                newStep.setDepartment(department);
                newStep.setJobGrade(jobGrade);
                JobStep saved = jobStepRepository.save(newStep);
                resultSteps.add(saved);
            }
        }

        for (JobStep removedStep : existingStepsById.values()) {
            if (!employeeRepository.findByJobStep_Id(removedStep.getId()).isEmpty()) {
                throw new IllegalArgumentException(
                        "Cannot remove job step " + removedStep.getName()
                                + " while employees are assigned to it");
            }
            // Retain the step row for position history and other historical references.
            removedStep.setJobGrade(null);
            jobStepRepository.save(removedStep);
        }

        for (Long stepId : stepIdsToRecalculate) {
            payrollChangeOrchestrator.recalculateForJobStep(stepId, LocalDate.now());
        }

        return JobGradeResponseDTO.builder()
                .id(jobGrade.getId())
                .name(jobGrade.getName())
                .steps(resultSteps.stream()
                        .map(step -> JobGradeResponseDTO.JobStepSummaryDTO.builder()
                                .id(step.getId())
                                .name(step.getName())
                                .basicSalary(step.getBasicSalary())
                                .grossSalary(step.getGrossSalary())
                                .build())
                        .toList())
                .build();
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
}