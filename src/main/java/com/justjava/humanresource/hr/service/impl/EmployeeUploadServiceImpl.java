package com.justjava.humanresource.hr.service.impl;

import com.justjava.humanresource.hr.dto.EmployeeUploadDTO;
import com.justjava.humanresource.hr.entity.*;
import com.justjava.humanresource.hr.repository.*;
import com.justjava.humanresource.hr.service.CsvParserService;
import com.justjava.humanresource.hr.service.EmployeeUploadService;
import com.justjava.humanresource.payroll.service.EmployeePositionHistoryService;
import com.justjava.humanresource.payroll.service.PayrollChangeOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
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
    private final PayrollChangeOrchestrator payrollChangeOrchestrator;
    private final EmployeeBankDetailRepository employeeBankDetailRepository;

    private final ApplicationContext applicationContext;

    @Value("${keycloak.realm}")
    private String realmName;

    private static final Long DEFAULT_DEPARTMENT_ID = 1L;


    @Override
    public void uploadEmployees(MultipartFile file) {

        List<EmployeeUploadDTO> records = csvParserService.parse(file);

        // ── PHASE 1: Validate — zero DB writes here ──────────────────────────
        List<String> conflictingEmails = findAllConflicts(records);

        if (!conflictingEmails.isEmpty()) {
            // Build a detailed error message and throw — nothing has been saved yet,
            // so the ID sequence is completely untouched.
            String message = buildConflictErrorMessage(conflictingEmails);
            throw new DuplicateEmailUploadException(message, conflictingEmails);
        }

        // ── PHASE 2: All rows are clean — persist them ────────────────────────
        Department department = departmentRepository.findById(DEFAULT_DEPARTMENT_ID)
                .orElseThrow(() -> new IllegalStateException("Department not found"));

        PayGroup payGroup = payGroupRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("PayGroup not found"));

        Map<String, JobGrade> gradeCache = new HashMap<>();
        Map<String, JobStep> stepCache  = new HashMap<>();
        List<Long> savedIds             = new ArrayList<>();

        for (EmployeeUploadDTO dto : records) {

            JobGrade jobGrade = gradeCache.computeIfAbsent(dto.getGrade(), gradeName ->
                    jobGradeRepository.findByName(gradeName).orElseGet(() -> {
                        JobGrade g = new JobGrade();
                        g.setName(gradeName);
                        g.setDepartment(department);
                        return jobGradeRepository.save(g);
                    }));

            String stepKey = dto.getGrade() + "|" + dto.getGross().toPlainString();
            JobStep step = stepCache.computeIfAbsent(stepKey, k ->
                    jobStepRepository.findByGrossSalaryAndJobGrade(
                            dto.getGross().divide(BigDecimal.valueOf(12), 5, RoundingMode.HALF_UP), jobGrade
                    ).orElseGet(() -> {
                        JobStep s = new JobStep();
                        s.setJobGrade(jobGrade);
                        s.setGrossSalary(dto.getGross().divide(BigDecimal.valueOf(12), 5, RoundingMode.HALF_UP));
                        s.setName("STEP-" + dto.getGross());
                        return jobStepRepository.save(s);
                    }));

            Long employeeId = applicationContext
                    .getBean(EmployeeUploadServiceImpl.class)
                    .saveSingleEmployee(dto, department, payGroup, step);
            savedIds.add(employeeId);
        }

        // ── PHASE 3: Payroll recalculation after all employees are committed ──
        for (Long employeeId : savedIds) {
            try {
                payrollChangeOrchestrator.recalculateForEmployee(employeeId, LocalDate.now());
            } catch (Exception e) {
                System.err.println("CSV upload: payroll recalculation failed for ID "
                        + employeeId + " — " + e.getMessage());
            }
        }
    }


    private List<String> findAllConflicts(List<EmployeeUploadDTO> records) {

        List<String> conflicting = new ArrayList<>();

        // A) Intra-CSV duplicates — case-sensitive exact match
        Map<String, Long> frequencyInCsv = records.stream()
                .map(EmployeeUploadDTO::getEmail)
                .collect(Collectors.groupingBy(email -> email, Collectors.counting()));

        frequencyInCsv.forEach((email, count) -> {
            if (count > 1) conflicting.add(email + " (appears " + count + "x in the file)");
        });

        // B) Emails already in the database — case-sensitive
        List<String> emailsInFile = records.stream()
                .map(EmployeeUploadDTO::getEmail)
                .distinct()
                .collect(Collectors.toList());

        for (String email : emailsInFile) {
            if (employeeRepository.findByEmail(email).isPresent()) {
                // Avoid listing it twice if it was already flagged as a CSV duplicate
                String label = email + " (already exists in the system)";
                boolean alreadyListed = conflicting.stream()
                        .anyMatch(c -> c.startsWith(email + " (appears"));
                if (alreadyListed) {
                    // Replace the entry to include both reasons
                    conflicting.replaceAll(c ->
                            c.startsWith(email + " (appears")
                                    ? email + " (duplicate in file AND already exists in the system)"
                                    : c);
                } else {
                    conflicting.add(label);
                }
            }
        }

        return conflicting;
    }

    private String buildConflictErrorMessage(List<String> conflicts) {
        StringBuilder sb = new StringBuilder();
        sb.append("Upload failed — the following ").append(conflicts.size())
                .append(" email conflict(s) must be resolved before re-uploading:\n");
        for (int i = 0; i < conflicts.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(conflicts.get(i)).append("\n");
        }
        sb.append("Please fix the CSV file and try again. No employees were saved.");
        return sb.toString();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long saveSingleEmployee(EmployeeUploadDTO dto, Department department,
                                   PayGroup payGroup, JobStep step) {
        if (dto.getEmail() != null) dto.setEmail(dto.getEmail().toLowerCase());

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

        String password   = employeeService.generateInitialPassword(employee);
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

        try {
            employeeService.changeEmploymentStatus(employee.getId(), EmploymentStatus.ACTIVE, LocalDate.now());
        } catch (Exception e) {
            System.err.println("CSV upload: employment status change failed for "
                    + dto.getEmail() + " — " + e.getMessage()
                    + ". Employee saved as ONBOARDING; payroll will recalculate after upload.");
        }

        EmployeeOnboarding onboarding = EmployeeOnboarding.builder()
                .employee(employee)
                .status(OnboardingStatus.INITIATED)
                .initiatedBy("CSV-UPLOAD")
                .build();
        onboardingRepository.save(onboarding);

        // Save bank details if all three fields are provided
        String  accountName   = dto.getAccountName();
        String  bankName      = dto.getBankName();
        String  accountNumber = dto.getAccountNumber();
        boolean hasBankDetails = accountName   != null && !accountName.isBlank()
                && bankName      != null && !bankName.isBlank()
                && accountNumber != null && !accountNumber.isBlank();

        if (hasBankDetails) {
            EmployeeBankDetail bankDetail = EmployeeBankDetail.builder()
                    .employee(employee)
                    .accountName(accountName)
                    .bankName(bankName)
                    .accountNumber(accountNumber)
                    .primaryAccount(true)
                    .effectiveFrom(LocalDate.now())
                    .status(RecordStatus.ACTIVE)
                    .build();
            employeeBankDetailRepository.save(bankDetail);
        }

        return employee.getId();
    }


    public static class DuplicateEmailUploadException extends RuntimeException {

        private final List<String> conflictingEmails;

        public DuplicateEmailUploadException(String message, List<String> conflictingEmails) {
            super(message);
            this.conflictingEmails = Collections.unmodifiableList(conflictingEmails);
        }

        public List<String> getConflictingEmails() {
            return conflictingEmails;
        }
    }
}