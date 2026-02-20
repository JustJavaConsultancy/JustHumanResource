package com.justjava.humanresource.orgStructure.services.impl;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.orgStructure.dto.*;
import com.justjava.humanresource.orgStructure.enums.ReportingType;
import com.justjava.humanresource.orgStructure.repositories.CompanyRepository;
import com.justjava.humanresource.orgStructure.repositories.EmployeeReportingLineRepository;
import com.justjava.humanresource.orgStructure.services.OrganogramService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.justjava.humanresource.orgStructure.entity.*;
@Service
@RequiredArgsConstructor
@Transactional
public class OrganogramServiceImpl implements OrganogramService {

    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeReportingLineRepository reportingRepository;

    /* =====================================================
       COMPANY MANAGEMENT
       ===================================================== */

    @Override
    public CompanyDTO createCompany(CompanyDTO dto) {

        Company parent = null;

        if (dto.getParentCompanyId() != null) {
            parent = companyRepository.findById(dto.getParentCompanyId())
                    .orElseThrow();
        }

        Company company = Company.builder()
                .name(dto.getName())
                .code(dto.getCode())
                .parentCompany(parent)
                .status(RecordStatus.ACTIVE)
                .build();

        companyRepository.save(company);

        return mapToCompanyDTO(company);
    }

    /* =====================================================
       DEPARTMENT MANAGEMENT
       ===================================================== */

    @Override
    public DepartmentDTO createDepartment(DepartmentDTO dto) {

        Company company = companyRepository.findById(dto.getCompanyId())
                .orElseThrow();

        Department parent = null;

        if (dto.getParentDepartmentId() != null) {
            parent = departmentRepository.findById(dto.getParentDepartmentId())
                    .orElseThrow();
        }

        Department dept = new Department();
        dept.setCode(dto.getCode());
        dept.setName(dto.getName());
        dept.setCompany(company);
        dept.setParentDepartment(parent);
        dept.setEffectiveFrom(dto.getEffectiveFrom());
        dept.setStatus(RecordStatus.ACTIVE);

        departmentRepository.save(dept);

        return mapToDepartmentDTO(dept);
    }

    @Override
    public DepartmentDTO moveDepartment(Long departmentId, Long newParentId) {

        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow();

        Department newParent = departmentRepository.findById(newParentId)
                .orElseThrow();

        dept.setParentDepartment(newParent);

        return mapToDepartmentDTO(dept);
    }

    @Override
    public void deactivateDepartment(Long departmentId, LocalDate effectiveTo) {

        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow();

        dept.setEffectiveTo(effectiveTo);
        dept.setStatus(RecordStatus.INACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentTreeDTO> getFullDepartmentStructure() {

        List<Department> departments = departmentRepository.findAll();

        Map<Long, DepartmentTreeDTO> map = new HashMap<>();

        for (Department d : departments) {
            map.put(d.getId(),
                    DepartmentTreeDTO.builder()
                            .departmentId(d.getId())
                            .name(d.getName())
                            .code(d.getCode())
                            .children(new ArrayList<>())
                            .build());
        }

        List<DepartmentTreeDTO> roots = new ArrayList<>();

        for (Department d : departments) {

            if (d.getParentDepartment() == null) {
                roots.add(map.get(d.getId()));
            } else {
                map.get(d.getParentDepartment().getId())
                        .getChildren()
                        .add(map.get(d.getId()));
            }
        }

        return roots;
    }

    /* =====================================================
       REPORTING MANAGEMENT
       ===================================================== */

    @Override
    public void assignManager(Long employeeId,
                              Long managerId,
                              ReportingType type,
                              LocalDate effectiveFrom) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow();

        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow();

        EmployeeReportingLine line = EmployeeReportingLine.builder()
                .employee(employee)
                .manager(manager)
                .reportingType(type)
                .effectiveFrom(effectiveFrom)
                .status(RecordStatus.ACTIVE)
                .build();

        reportingRepository.save(line);
    }

    @Override
    public void removeManager(Long employeeId,
                              ReportingType type,
                              LocalDate effectiveTo) {

        EmployeeReportingLine line =
                reportingRepository.findActiveLine(employeeId, type)
                        .orElseThrow();

        line.setEffectiveTo(effectiveTo);
        line.setStatus(RecordStatus.INACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeNodeDTO> getEmployeeReportingTree(Long rootEmployeeId,
                                                          ReportingType type,
                                                          LocalDate date) {

        Employee root = employeeRepository.findById(rootEmployeeId)
                .orElseThrow();

        return List.of(buildEmployeeNode(root, type, date));
    }
    @Override
    public CompanyDTO addSubsidiary(Long parentCompanyId,
                                    CompanyDTO subsidiaryDTO) {

        Company parent = companyRepository.findById(parentCompanyId)
                .orElseThrow(() -> new IllegalArgumentException("Parent company not found"));

        Company subsidiary = Company.builder()
                .name(subsidiaryDTO.getName())
                .code(subsidiaryDTO.getCode())
                .parentCompany(parent)
                .status(RecordStatus.ACTIVE)
                .build();

        companyRepository.save(subsidiary);

        return mapToCompanyDTO(subsidiary);
    }
    @Override
    @Transactional(readOnly = true)
    public List<CompanyTreeDTO> getCompanyStructure() {

        List<Company> companies = companyRepository.findAll();

        Map<Long, CompanyTreeDTO> map = new HashMap<>();

        for (Company c : companies) {
            map.put(c.getId(),
                    CompanyTreeDTO.builder()
                            .companyId(c.getId())
                            .name(c.getName())
                            .code(c.getCode())
                            .subsidiaries(new ArrayList<>())
                            .build());
        }

        List<CompanyTreeDTO> roots = new ArrayList<>();

        for (Company c : companies) {

            if (c.getParentCompany() == null) {
                roots.add(map.get(c.getId()));
            } else {
                map.get(c.getParentCompany().getId())
                        .getSubsidiaries()
                        .add(map.get(c.getId()));
            }
        }

        return roots;
    }

    private EmployeeNodeDTO buildEmployeeNode(Employee employee,
                                              ReportingType type,
                                              LocalDate date) {

        List<EmployeeReportingLine> reports =
                reportingRepository.findDirectReports(employee.getId(), type, date);

        List<EmployeeNodeDTO> children = reports.stream()
                .map(r -> buildEmployeeNode(r.getEmployee(), type, date))
                .toList();

        return EmployeeNodeDTO.builder()
                .employeeId(employee.getId())
                .employeeNumber(employee.getEmployeeNumber())
                .fullName(employee.getFullName())
                .departmentId(employee.getDepartment().getId())
                .directReports(children)
                .build();
    }

    /* =====================================================
       MAPPERS
       ===================================================== */

    private CompanyDTO mapToCompanyDTO(Company c) {
        return CompanyDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .code(c.getCode())
                .parentCompanyId(
                        c.getParentCompany() != null
                                ? c.getParentCompany().getId()
                                : null
                )
                .status(c.getStatus())
                .build();
    }

    private DepartmentDTO mapToDepartmentDTO(Department d) {
        return DepartmentDTO.builder()
                .id(d.getId())
                .code(d.getCode())
                .name(d.getName())
                .companyId(d.getCompany().getId())
                .parentDepartmentId(
                        d.getParentDepartment() != null
                                ? d.getParentDepartment().getId()
                                : null
                )
                .effectiveFrom(d.getEffectiveFrom())
                .effectiveTo(d.getEffectiveTo())
                .status(d.getStatus())
                .build();
    }
}
