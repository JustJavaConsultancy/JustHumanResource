package com.justjava.humanresource.orgStructure.services;

import com.justjava.humanresource.hr.dto.*;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobGrade;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.orgStructure.dto.CompanyStructureDTO;
import com.justjava.humanresource.orgStructure.dto.DepartmentStructureDTO;
import com.justjava.humanresource.orgStructure.entity.Company;
import com.justjava.humanresource.orgStructure.repositories.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyStructureService {

    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public CompanyStructureDTO getCompanyStructure(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Company not found: " + companyId));

        List<DepartmentStructureDTO> departments = departmentRepository
                .findByCompany_IdOrderByNameAsc(companyId)
                .stream()
                .map(this::toDepartmentStructureDTO)
                .toList();

        return CompanyStructureDTO.builder()
                .id(company.getId())
                .name(company.getName())
                .code(company.getCode())
                .status(company.getStatus())
                .parentCompanyId(company.getParentCompany() != null ? company.getParentCompany().getId() : null)
                .departments(departments)
                .build();
    }

    private DepartmentStructureDTO toDepartmentStructureDTO(Department department) {
        List<EmployeeSummaryDTO> employees = employeeRepository
                .findByDepartmentIdForStructure(department.getId())
                .stream()
                .map(this::toEmployeeSummaryDTO)
                .toList();

        Employee departmentHead = department.getDepartmentHead();

        return DepartmentStructureDTO.builder()
                .id(department.getId())
                .code(department.getCode())
                .name(department.getName())
                .parentDepartmentId(department.getParentDepartment() != null ? department.getParentDepartment().getId() : null)
                .effectiveFrom(department.getEffectiveFrom())
                .effectiveTo(department.getEffectiveTo())
                .status(department.getStatus())
                .departmentHeadId(departmentHead != null ? departmentHead.getId() : null)
                .departmentHeadName(departmentHead != null ? departmentHead.getFullName() : null)
                .employees(employees)
                .build();
    }

    private EmployeeSummaryDTO toEmployeeSummaryDTO(Employee employee) {
        JobStep jobStep = employee.getJobStep();
        JobGrade jobGrade = jobStep != null ? jobStep.getJobGrade() : null;
        PayGroup payGroup = employee.getPayGroup();

        return EmployeeSummaryDTO.builder()
                .id(employee.getId())
                .employeeNumber(employee.getEmployeeNumber())
                .fullName(employee.getFullName())
                .email(employee.getEmail())
                .phoneNumber(employee.getPhoneNumber())
                .employmentStatus(employee.getEmploymentStatus())
                .status(employee.getStatus())
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