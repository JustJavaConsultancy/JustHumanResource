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

import java.math.BigDecimal;
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
        // 1. CREATE OR FETCH JOB GRADE
        // ---------------------------------------------------------

        JobGrade jobGrade = jobGradeRepository
                .findByName("AUTO-GRADE")
                .orElseGet(() -> {
                    JobGrade g = new JobGrade();
                    g.setName("AUTO-GRADE");
                    return jobGradeRepository.save(g);
                });

        // ---------------------------------------------------------
        // 2. CACHE JOB STEPS BY GROSS
        // ---------------------------------------------------------

        Map<BigDecimal, JobStep> jobStepCache = new HashMap<>();

        for (EmployeeUploadDTO dto : records) {

            JobStep step = jobStepCache.computeIfAbsent(
                    dto.getGross(),
                    gross -> jobStepRepository
                            .findByGrossSalary(gross)
                            .orElseGet(() -> {

                                JobStep s = new JobStep();
                                s.setJobGrade(jobGrade);
                                s.setGrossSalary(gross);
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

            employee = employeeRepository.save(employee);

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