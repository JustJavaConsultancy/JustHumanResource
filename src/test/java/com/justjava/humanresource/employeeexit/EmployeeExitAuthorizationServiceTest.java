package com.justjava.humanresource.employeeexit;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.employeeexit.entity.EmployeeExitCase;
import com.justjava.humanresource.employeeexit.entity.EmployeeExitDocument;
import com.justjava.humanresource.employeeexit.enums.ClearanceType;
import com.justjava.humanresource.employeeexit.enums.ExitDocumentVisibility;
import com.justjava.humanresource.employeeexit.service.EmployeeExitAuthorizationService;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class EmployeeExitAuthorizationServiceTest {
    @Mock AuthenticationManager auth;
    @Mock EmployeeRepository employees;
    EmployeeExitAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeExitAuthorizationService(auth, employees);
    }

    @Test
    void employeeCanViewOwnExitOnly() {
        Employee actor = employee(7L);
        EmployeeExitCase own = exit(7L);
        EmployeeExitCase other = exit(8L);

        assertTrue(service.canView(own, actor));
        assertFalse(service.canView(other, actor));
    }

    @Test
    void hrCanViewAnyExit() {
        when(auth.isHumanResource()).thenReturn(true);

        assertTrue(service.canView(exit(8L), employee(7L)));
    }

    @Test
    void employeeCannotViewHrOnlyDocument() {
        EmployeeExitDocument doc = new EmployeeExitDocument();
        doc.setVisibility(ExitDocumentVisibility.HR_ONLY);

        assertFalse(service.canViewDocument(exit(7L), doc, employee(7L)));
    }

    @Test
    void financeCanManageSettlement() {
        when(auth.isFinancialOfficer()).thenReturn(true);

        assertTrue(service.canManageSettlement());
    }

    @Test
    void assetManagerCanManageAssets() {
        when(auth.get("groups")).thenReturn(List.of("assetManager"));

        assertTrue(service.canManageAssets());
    }

    @Test
    void assetManagerCanCompleteAssetClearance() {
        when(auth.get("groups")).thenReturn(List.of("assetManager"));

        assertTrue(service.canCompleteClearance(ClearanceType.ASSET_AND_FACILITIES, employee(7L)));
    }

    @Test
    void departmentHeadCanViewExitCases() {
        when(auth.get("groups")).thenReturn(List.of("departmentHead"));

        assertTrue(service.canView(exit(8L), employee(7L)));
    }

    @Test
    void departmentHeadCanCompleteManagerHandoverClearance() {
        when(auth.get("groups")).thenReturn(List.of("departmentHead"));

        assertTrue(service.canCompleteClearance(ClearanceType.MANAGER_HANDOVER, employee(7L)));
    }

    @Test
    void slashPrefixedGroupIsNotAcceptedForAssetManagerOrDepartmentHead() {
        when(auth.get("groups")).thenReturn(List.of("/assetManager", "/departmentHead"));

        assertFalse(service.canManageAssets());
        assertFalse(service.canCompleteClearance(ClearanceType.ASSET_AND_FACILITIES, employee(7L)));
        assertFalse(service.canCompleteClearance(ClearanceType.MANAGER_HANDOVER, employee(7L)));
    }

    private EmployeeExitCase exit(Long employeeId) {
        EmployeeExitCase exit = new EmployeeExitCase();
        exit.setEmployeeId(employeeId);
        return exit;
    }

    private Employee employee(Long id) {
        Employee employee = new Employee();
        employee.setId(id);
        return employee;
    }
}