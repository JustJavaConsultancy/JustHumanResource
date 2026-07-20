package com.justjava.humanresource.orgStructure.services.impl;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.dto.EmployeeDTO;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.justjava.humanresource.orgStructure.entity.*;
@Service
@RequiredArgsConstructor
@Transactional
public class OrganogramServiceImpl implements OrganogramService {

    private static final String DEPARTMENT_HEAD_GROUP = "departmentHead";

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
                .logoData(decodeLogo(dto.getLogoBase64()))
                .logoContentType(dto.getLogoContentType())
                .build();

        companyRepository.save(company);

        return mapToCompanyDTO(company);
    }

    @Override
    public CompanyDTO updateCompany(Long companyId, CompanyDTO dto) {

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyId));

        if (dto.getName() != null && !dto.getName().isBlank()) {
            company.setName(dto.getName().trim());
        }
        if (dto.getCode() != null && !dto.getCode().isBlank()) {
            company.setCode(dto.getCode().trim());
        }
        // Logo: if a new logo is supplied, replace it; if logoBase64 is explicitly
        // set to empty string the caller wants to clear it.
        if (dto.getLogoBase64() != null) {
            if (dto.getLogoBase64().isBlank()) {
                company.setLogoData(null);
                company.setLogoContentType(null);
            } else {
                company.setLogoData(decodeLogo(dto.getLogoBase64()));
                company.setLogoContentType(dto.getLogoContentType());
            }
        }

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
        dept.setDepartmentHead(resolveDepartmentHead(dto.getDepartmentHeadId()));

        departmentRepository.save(dept);

        return mapToDepartmentDTO(dept);
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentDTO getDepartmentById(Long departmentId) {

        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + departmentId));

        return mapToDepartmentDTO(dept);
    }

    @Override
    public DepartmentDTO updateDepartment(Long departmentId, DepartmentDTO dto) {

        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + departmentId));

        // NOTE: dept.code is intentionally never touched here — it is not editable.

        if (dto.getName() != null && !dto.getName().isBlank()) {
            dept.setName(dto.getName().trim());
        }

        if (dto.getCompanyId() != null) {
            Company company = companyRepository.findById(dto.getCompanyId())
                    .orElseThrow(() -> new IllegalArgumentException("Company not found: " + dto.getCompanyId()));
            dept.setCompany(company);
        }

        if (dto.getParentDepartmentId() != null) {
            if (dto.getParentDepartmentId().equals(departmentId)) {
                throw new IllegalArgumentException("A department cannot be its own parent.");
            }
            Department parent = departmentRepository.findById(dto.getParentDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent department not found: " + dto.getParentDepartmentId()));
            dept.setParentDepartment(parent);
        } else {
            dept.setParentDepartment(null);
        }

        if (dto.getEffectiveFrom() != null) {
            dept.setEffectiveFrom(dto.getEffectiveFrom());
        }

        dept.setDepartmentHead(resolveDepartmentHead(dto.getDepartmentHeadId()));

        departmentRepository.save(dept);

        return mapToDepartmentDTO(dept);
    }

    /**
     * Resolves and validates the employee selected as a department head.
     * Returns null when no employee is selected (department head is optional).
     * Throws if the employee doesn't exist or isn't in the "departmentHead" group.
     */
    private Employee resolveDepartmentHead(Long employeeId) {
        if (employeeId == null) {
            return null;
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        if (employee.getGroups() == null || !employee.getGroups().contains(DEPARTMENT_HEAD_GROUP)) {
            throw new IllegalStateException(
                    "Selected employee is not in the \"" + DEPARTMENT_HEAD_GROUP + "\" group and cannot be assigned as department head.");
        }

        return employee;
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

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDTO> getDepartmentHeadCandidates() {

        return employeeRepository.findActiveEmployeesInGroup(DEPARTMENT_HEAD_GROUP).stream()
                .map(this::mapToDepartmentHeadCandidateDTO)
                .toList();
    }

    // Minimal mapping — only the fields the department-head dropdown needs.
    // Not a full Employee->EmployeeDTO mapper; other EmployeeDTO fields are left null.
    private EmployeeDTO mapToDepartmentHeadCandidateDTO(Employee e) {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setId(e.getId());
        dto.setEmployeeNumber(e.getEmployeeNumber());
        dto.setFirstName(e.getFirstName());
        dto.setLastName(e.getLastName());
        dto.setEmail(e.getEmail());
        return dto;
    }

    /* =====================================================
       REPORTING MANAGEMENT
       ===================================================== */

    @Override
    public void assignManager(Long employeeId,
                              Long managerId,
                              ReportingType type,
                              LocalDate effectiveFrom) {

        if (employeeId.equals(managerId)) {
            throw new IllegalArgumentException("Employee cannot report to self.");
        }

        if (reportingRepository.wouldCreateCycle(employeeId, managerId)) {
            throw new IllegalStateException("This reporting assignment would create a cycle.");
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow();

        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow();

        var activeOpt = reportingRepository.findActiveLine(employeeId, type);
        if (activeOpt.isPresent()) {
            var active = activeOpt.get();
            if (active.getManager().getId().equals(managerId)) {
                return;
            }
            active.setEffectiveTo(effectiveFrom.minusDays(1));
            active.setStatus(RecordStatus.INACTIVE);
            reportingRepository.save(active);
        }

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
                .logoData(decodeLogo(subsidiaryDTO.getLogoBase64()))
                .logoContentType(subsidiaryDTO.getLogoContentType())
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
            String logoBase64 = (c.getLogoData() != null)
                    ? Base64.getEncoder().encodeToString(c.getLogoData())
                    : null;
            map.put(c.getId(),
                    CompanyTreeDTO.builder()
                            .companyId(c.getId())
                            .name(c.getName())
                            .code(c.getCode())
                            .logoBase64(logoBase64)
                            .logoContentType(c.getLogoContentType())
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
        String logoBase64 = (c.getLogoData() != null)
                ? Base64.getEncoder().encodeToString(c.getLogoData())
                : null;
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
                .logoBase64(logoBase64)
                .logoContentType(c.getLogoContentType())
                .build();
    }


    private byte[] decodeLogo(String logoBase64) {
        if (logoBase64 == null || logoBase64.isBlank()) return null;
        String data = logoBase64.contains(",")
                ? logoBase64.substring(logoBase64.indexOf(',') + 1)
                : logoBase64;
        return Base64.getDecoder().decode(data);
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
                .departmentHeadId(
                        d.getDepartmentHead() != null
                                ? d.getDepartmentHead().getId()
                                : null
                )
                .departmentHeadName(
                        d.getDepartmentHead() != null
                                ? d.getDepartmentHead().getFullName()
                                : null
                )
                .build();
    }
}