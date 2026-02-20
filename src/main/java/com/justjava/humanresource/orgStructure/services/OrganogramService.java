package com.justjava.humanresource.orgStructure.services;

import com.justjava.humanresource.orgStructure.dto.*;
import com.justjava.humanresource.orgStructure.enums.ReportingType;

import java.time.LocalDate;
import java.util.List;


public interface OrganogramService {

    /* =========================
       COMPANY MANAGEMENT
       ========================= */

    CompanyDTO createCompany(CompanyDTO dto);

    /* =========================
       DEPARTMENT MANAGEMENT
       ========================= */

    DepartmentDTO createDepartment(DepartmentDTO dto);

    DepartmentDTO moveDepartment(Long departmentId, Long newParentId);

    void deactivateDepartment(Long departmentId, LocalDate effectiveTo);

    List<DepartmentTreeDTO> getFullDepartmentStructure();

    /* =========================
       REPORTING MANAGEMENT
       ========================= */

    void assignManager(Long employeeId,
                       Long managerId,
                       ReportingType type,
                       LocalDate effectiveFrom);

    void removeManager(Long employeeId,
                       ReportingType type,
                       LocalDate effectiveTo);

    List<EmployeeNodeDTO> getEmployeeReportingTree(Long rootEmployeeId,
                                                   ReportingType type,
                                                   LocalDate date);
    CompanyDTO addSubsidiary(Long parentCompanyId, CompanyDTO subsidiaryDTO);

    List<CompanyTreeDTO> getCompanyStructure();

}
