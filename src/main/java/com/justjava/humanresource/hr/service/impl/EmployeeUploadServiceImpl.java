package com.justjava.humanresource.hr.service.impl;

import com.justjava.humanresource.hr.dto.EmployeeUploadDTO;
import com.justjava.humanresource.hr.entity.*;
import com.justjava.humanresource.hr.repository.*;
import com.justjava.humanresource.hr.service.CsvParserService;
import com.justjava.humanresource.hr.service.EmployeeUploadService;
import com.justjava.humanresource.payroll.service.EmployeePositionHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.onboarding.entity.EmployeeOnboarding;
import com.justjava.humanresource.onboarding.enums.OnboardingStatus;
import com.justjava.humanresource.onboarding.repositories.EmployeeOnboardingRepository;
import com.justjava.humanresource.aau.keycloak.KeycloakAdminService;
import org.springframework.beans.factory.annotation.Value;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeUploadServiceImpl implements EmployeeUploadService {

    private final CsvParserService csvParserService;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final JobGradeRepository jobGradeRepository;
    private final JobStepRepository jobStepRepository;
    private final EmployeePositionHistoryService positionHistoryService;
    private final PayGroupRepository payGroupRepository;
    private final EmployeeService employeeService;
    private final EmployeeOnboardingRepository onboardingRepository;
    private final KeycloakAdminService keycloakAdminService;

    @Value("${keycloak.realm}")
    private String realmName;

    private static final Long DEFAULT_DEPARTMENT_ID = 1L;

    @Override
    public void uploadEmployees(MultipartFile file) {

        List<EmployeeUploadDTO> records =
                csvParserService.parse(file);

        Department department = departmentRepository.findById(DEFAULT_DEPARTMENT_ID)
                .orElseThrow(() -> new IllegalStateException("Department not found"));

        // ---------------------------------------------------------
        // 🔥 NEW: FETCH PAYGROUP (FIXED = 2)
        // ---------------------------------------------------------

        PayGroup payGroup = payGroupRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("PayGroup not found"));

        // ---------------------------------------------------------
        // 1. CACHE JOB GRADES BY GRADE NAME & JOB STEPS BY GROSS
        // ---------------------------------------------------------

        Map<String, Map<BigDecimal, JobStep>> gradeStepCache = new HashMap<>();

        for (EmployeeUploadDTO dto : records) {

            // Fetch or create job grade per grade name
            String gradeName = dto.getGrade();
            JobGrade jobGrade = jobGradeRepository
                    .findByName(gradeName)
                    .orElseGet(() -> {
                        JobGrade g = new JobGrade();
                        g.setName(gradeName);
                        g.setDepartment(department);
                        return jobGradeRepository.save(g);
                    });

            // ---------------------------------------------------------
            // 2. CACHE JOB STEPS BY GROSS AND JOB GRADE (per grade)
            // ---------------------------------------------------------

            Map<BigDecimal, JobStep> jobStepCache = gradeStepCache.computeIfAbsent(gradeName, k -> new HashMap<>());

            JobStep step = jobStepCache.computeIfAbsent(
                    dto.getGross(),
                    gross -> jobStepRepository
                            .findByGrossSalaryAndJobGrade(gross, jobGrade)
                            .orElseGet(() -> {
                                JobStep s = new JobStep();
                                s.setJobGrade(jobGrade);
                                s.setGrossSalary(gross.divide(BigDecimal.valueOf(12), 5, RoundingMode.HALF_UP));
                                s.setName("STEP-" + gross);
                                return jobStepRepository.save(s);
                            })
            );
            // -----------------------------------------------------
            // 3. CREATE EMPLOYEE
            // -----------------------------------------------------

            Employee employee = new Employee();
            employee.setFirstName(dto.getFirstName());
            employee.setLastName(dto.getSecondName());
            employee.setDepartment(department);
            employee.setEmployeeNumber("EMP-" + System.currentTimeMillis());
            employee.setEmploymentStatus(EmploymentStatus.ONBOARDING);
            employee.setStatus(RecordStatus.ACTIVE);
            employee.setJobStep(step);
            employee.setPayGroup(payGroup);
            employee.setEmail(dto.getEmail());

            employee = employeeRepository.save(employee);

            // Create Keycloak account using email as username
            String password = employeeService.generateInitialPassword(employee);
            String keycloakId = keycloakAdminService.createUser(
                    realmName,
                    dto.getEmail(),
                    dto.getEmail(),
                    password,
                    dto.getFirstName(),
                    dto.getSecondName(),
                    Map.of("employeeId", List.of(String.valueOf(employee.getId())))
            );
            employee.setKeycloakUserId(keycloakId);


            employeeService.changeEmploymentStatus(employee.getId(), EmploymentStatus.ACTIVE, LocalDate.now()); // added


            // Added:  Create onboarding record so employee appears in getAllOnboardings()
            EmployeeOnboarding onboarding = EmployeeOnboarding.builder()
                    .employee(employee)
                    .status(OnboardingStatus.INITIATED)
                    .initiatedBy("CSV-UPLOAD")
                    .build();
            onboardingRepository.save(onboarding);

            // -----------------------------------------------------
            // 🔥 4. ASSIGN POSITION (WITH PAYGROUP)
            // -----------------------------------------------------

            positionHistoryService.changePosition(
                    employee.getId(),
                    department.getId(),
                    step.getId(),
                    payGroup.getId(),
                    LocalDate.now()
            );
        }
    }
}