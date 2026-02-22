package com.justjava.humanresource.orgStructure.controller;

import com.justjava.humanresource.orgStructure.dto.*;
import com.justjava.humanresource.orgStructure.enums.ReportingType;
import com.justjava.humanresource.orgStructure.services.OrganogramService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/organogram")
@RequiredArgsConstructor
public class OrganogramController {

    private final OrganogramService organogramService;

    /* =========================================================
       COMPANY MANAGEMENT
       ========================================================= */

    @PostMapping("/companies")
    public CompanyDTO createCompany(@RequestBody CompanyDTO dto) {
        return organogramService.createCompany(dto);
    }

    /* =========================================================
       DEPARTMENT MANAGEMENT
       ========================================================= */

    @PostMapping("/departments")
    public DepartmentDTO createDepartment(@RequestBody DepartmentDTO dto) {
        return organogramService.createDepartment(dto);
    }

    @PutMapping("/departments/{departmentId}/move/{newParentId}")
    public DepartmentDTO moveDepartment(@PathVariable Long departmentId,
                                        @PathVariable Long newParentId) {
        return organogramService.moveDepartment(departmentId, newParentId);
    }

    @PutMapping("/departments/{departmentId}/deactivate")
    public void deactivateDepartment(
            @PathVariable Long departmentId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate effectiveTo) {
        organogramService.deactivateDepartment(departmentId, effectiveTo);
    }

    @GetMapping("/departments/tree")
    public List<DepartmentTreeDTO> getFullDepartmentStructure() {
        return organogramService.getFullDepartmentStructure();
    }

    /* =========================================================
       REPORTING MANAGEMENT
       ========================================================= */

    @PostMapping("/reporting/assign")
    public void assignManager(@RequestBody AssignManagerRequest request) {
        organogramService.assignManager(
                request.getEmployeeId(),
                request.getManagerId(),
                request.getReportingType(),
                request.getEffectiveFrom()
        );
    }

    @PutMapping("/reporting/remove")
    public void removeManager(@RequestBody RemoveManagerRequest request) {

        organogramService.removeManager(
                request.getEmployeeId(),
                request.getReportingType(),
                request.getEffectiveTo()
        );
    }

    @GetMapping("/reporting/tree/{employeeId}")
    public List<EmployeeNodeDTO> getEmployeeTree(
            @PathVariable Long employeeId,
            @RequestParam ReportingType type,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        return organogramService.getEmployeeReportingTree(
                employeeId,
                type,
                date
        );
    }
    @PostMapping("/companies/{parentId}/subsidiaries")
    public CompanyDTO addSubsidiary(
            @PathVariable Long parentId,
            @RequestBody CompanyDTO dto) {
        return organogramService.addSubsidiary(parentId, dto);
    }
    @GetMapping("/companies/tree")
    public List<CompanyTreeDTO> getCompanyStructure() {
        return organogramService.getCompanyStructure();
    }

}

/***
 1️⃣ Create Company

 POST
 http://localhost:8080/api/organogram/companies

 {
 "name": "JustJava Group",
 "code": "JJG",
 "parentCompanyId": null
 }

 2️⃣ Create Department

 POST
 http://localhost:8080/api/organogram/departments

 {
 "code": "100001",
 "name": "Human Resources",
 "companyId": 1,
 "parentDepartmentId": null,
 "effectiveFrom": "2026-01-01"
 }

 3️⃣ Move Department

 PUT
 /api/organogram/departments/2/move/1

 4️⃣ Assign Primary Manager

 POST
 /api/organogram/reporting/assign

 {
 "employeeId": 5,
 "managerId": 2,
 "reportingType": "PRIMARY",
 "effectiveFrom": "2026-01-01"
 }

 5️⃣ Remove Manager

 PUT
 /api/organogram/reporting/remove

 {
 "employeeId": 5,
 "reportingType": "PRIMARY",
 "effectiveTo": "2026-06-30"
 }

 6️⃣ Get Department Tree

 GET
 /api/organogram/departments/tree

 7️⃣ Get Employee Reporting Tree

 GET

 /api/organogram/reporting/tree/5?type=PRIMARY&date=2026-02-01
 */
/***
 *
 1️⃣ Add Parent Company

 POST
 /api/organogram/companies

 {
 "name": "JustJava Holdings",
 "code": "JJH"
 }

 2️⃣ Add Subsidiary

 POST
 /api/organogram/companies/1/subsidiaries

 {
 "name": "JustJava Nigeria Ltd",
 "code": "JJN"
 }

 3️⃣ Add Another Subsidiary

 POST
 /api/organogram/companies/1/subsidiaries

 {
 "name": "JustJava Ghana Ltd",
 "code": "JJG"
 }

 4️⃣ Get Company Structure

 GET
 /api/organogram/companies/tree

 Response:

 [
 {
 "companyId": 1,
 "name": "JustJava Holdings",
 "code": "JJH",
 "subsidiaries": [
 {
 "companyId": 2,
 "name": "JustJava Nigeria Ltd",
 "code": "JJN",
 "subsidiaries": []
 },
 {
 "companyId": 3,
 "name": "JustJava Ghana Ltd",
 "code": "JJG",
 "subsidiaries": []
 }
 ]
 }
 ]
 */