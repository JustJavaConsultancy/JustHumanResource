package com.justjava.humanresource.hr.controller;

import com.justjava.humanresource.hr.dto.JobGradeSummaryDTO;
import com.justjava.humanresource.hr.dto.JobStepSummaryDTO;
import com.justjava.humanresource.hr.dto.PayGroupSummaryDTO;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobGrade;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.orgStructure.dto.EmployeeDepartmentDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeDepartmentController {

    private final EmployeeRepository employeeRepository;

    @GetMapping("/department")
    @ResponseBody
    public EmployeeDepartmentDTO getDepartmentByEmployeeEmail(@RequestParam String email) {
        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No employee found with email: " + email));

        Department department = employee.getDepartment();
        if (department == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee has no department assigned");
        }

        JobStep jobStep = employee.getJobStep();
        JobGrade jobGrade = jobStep != null ? jobStep.getJobGrade() : null;
        PayGroup payGroup = employee.getPayGroup();

        return EmployeeDepartmentDTO.builder()
                .id(department.getId())
                .code(department.getCode())
                .name(department.getName())
                .companyId(department.getCompany() != null ? department.getCompany().getId() : null)
                .parentDepartmentId(department.getParentDepartment() != null ? department.getParentDepartment().getId() : null)
                .effectiveFrom(department.getEffectiveFrom())
                .effectiveTo(department.getEffectiveTo())
                .status(department.getStatus())
                .jobGrade(jobGrade == null ? null : JobGradeSummaryDTO.builder()
                        .id(jobGrade.getId())
                        .name(jobGrade.getName())
                        .build())
                .jobStep(jobStep == null ? null : JobStepSummaryDTO.builder()
                        .id(jobStep.getId())
                        .stepName(jobStep.getName())
                        .grossSalary(jobStep.getGrossSalary())
                        .build())
                .payGroup(payGroup == null ? null : PayGroupSummaryDTO.builder()
                        .id(payGroup.getId())
                        .code(payGroup.getCode())
                        .name(payGroup.getName())
                        .payFrequency(payGroup.getPayFrequency())
                        .status(payGroup.getStatus())
                        .build())
                .build();
    }
}