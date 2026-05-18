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
                .orElseThrow(() -> new ResourceNotFoundException("Department", command.getDepartmentId()));

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
                    .orElseThrow(() -> new ResourceNotFoundException("PayGroup", command.getParentId()));
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
                .orElseThrow(() -> new ResourceNotFoundException("PayGroup", id));

        PayGroup parent = null;
        if (command.getParentId() != null) {
            parent = payGroupRepository.findById(command.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("PayGroup", command.getParentId()));
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
                .orElseThrow(() -> new ResourceNotFoundException("JobGrade", gradeId));

        Department department = departmentRepository.findById(command.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", command.getDepartmentId()));

        jobGrade.setName(command.getGradeName());
        jobGrade.setDepartment(department);
        jobGradeRepository.save(jobGrade);

        List<JobStep> existingSteps = new ArrayList<>(jobStepRepository.findByJobGrade(jobGrade));
        List<CreateJobGradeWithStepsCommand.JobStepCommand> incomingSteps = command.getSteps();

        List<JobStep> resultSteps = new ArrayList<>();
        // ✅ Track only updated (existing) steps that need recalculation.
        // New steps have no employees so recalculating them is a no-op that
        // still triggers the duplicate-key collision on other employees.
        List<Long> stepIdsToRecalculate = new ArrayList<>();

        for (int i = 0; i < incomingSteps.size(); i++) {
            CreateJobGradeWithStepsCommand.JobStepCommand stepCmd = incomingSteps.get(i);
            BigDecimal divisor = stepCmd.isAnnual() ? BigDecimal.valueOf(12) : BigDecimal.ONE;

            BigDecimal basic = stepCmd.getBasicSalary() != null
                    ? stepCmd.getBasicSalary().divide(divisor, 5, RoundingMode.HALF_UP) : null;
            BigDecimal gross = stepCmd.getGrossSalary() != null
                    ? stepCmd.getGrossSalary().divide(divisor, 5, RoundingMode.HALF_UP) : null;

            if (i < existingSteps.size()) {
                // Updating an existing step — employees may be on it, recalculate
                JobStep existing = existingSteps.get(i);
                existing.setName(stepCmd.getStepName());
                existing.setBasicSalary(basic);
                existing.setGrossSalary(gross);
                existing.setDepartment(department);
                JobStep saved = jobStepRepository.save(existing);
                resultSteps.add(saved);
                stepIdsToRecalculate.add(saved.getId()); // ← only existing steps
            } else {
                // Brand new step — no employees on it yet, skip recalculation
                JobStep newStep = new JobStep();
                newStep.setName(stepCmd.getStepName());
                newStep.setBasicSalary(basic);
                newStep.setGrossSalary(gross);
                newStep.setDepartment(department);
                newStep.setJobGrade(jobGrade);
                JobStep saved = jobStepRepository.save(newStep);
                resultSteps.add(saved);
                // ✅ Do NOT add to stepIdsToRecalculate — new step has no employees
            }
        }

        // Handle surplus steps (grade had more steps than incoming)
        if (existingSteps.size() > incomingSteps.size()) {
            List<JobStep> toRemove = existingSteps.subList(incomingSteps.size(), existingSteps.size());
            for (JobStep surplus : toRemove) {
                try {
                    Long stepId = surplus.getId();
                    jobStepRepository.delete(surplus);
                    jobStepRepository.flush();
                    payrollChangeOrchestrator.recalculateForJobStep(stepId, LocalDate.now());
                } catch (Exception e) {
                    surplus.setJobGrade(null);
                    jobStepRepository.save(surplus);
                    payrollChangeOrchestrator.recalculateForJobStep(surplus.getId(), LocalDate.now());
                }
            }
        }

        // ✅ Only recalculate for existing steps that were updated (not new ones)
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
}