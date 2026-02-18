package com.justjava.humanresource.hr.service;

import com.justjava.humanresource.hr.dto.DepartmentSummaryDTO;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import com.justjava.humanresource.payroll.repositories.PaySlipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final PaySlipRepository paySlipRepository;
    private final EmployeeAppraisalRepository appraisalRepository;

    /* ============================================================
       GET ALL DEPARTMENT SUMMARIES
       ============================================================ */

    public List<DepartmentSummaryDTO> getDepartmentSummaries() {

        List<Department> departments = departmentRepository.findAll();

        return departments.stream()
                .map(this::buildSummary)
                .toList();
    }

    /* ============================================================
       INTERNAL BUILDER
       ============================================================ */

    private DepartmentSummaryDTO buildSummary(Department department) {

        Long departmentId = department.getId();

        Long totalEmployees =
                employeeRepository.countEmployeesByDepartment(departmentId);

        BigDecimal totalGross =
                paySlipRepository.sumLatestGrossByDepartment(departmentId);

        Double avgScore =
                appraisalRepository.averageFinalScoreByDepartment(departmentId);

        BigDecimal averageKpiScore =
                avgScore != null
                        ? BigDecimal.valueOf(avgScore)
                        .setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        return DepartmentSummaryDTO.builder()
                .departmentId(departmentId)
                .departmentName(department.getName())
                .totalEmployees(totalEmployees)
                .totalGrossSalary(
                        totalGross != null ? totalGross : BigDecimal.ZERO
                )
                .averageKpiScore(averageKpiScore)
                .build();
    }
}