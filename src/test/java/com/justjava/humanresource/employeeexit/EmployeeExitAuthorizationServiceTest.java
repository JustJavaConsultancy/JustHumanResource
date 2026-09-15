package com.justjava.humanresource.employeeexit;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.employeeexit.entity.EmployeeExitCase;
import com.justjava.humanresource.employeeexit.entity.EmployeeExitDocument;
import com.justjava.humanresource.employeeexit.enums.ClearanceType;
import com.justjava.humanresource.employeeexit.enums.ExitDocumentVisibility;
import com.justjava.humanresource.employeeexit.service.EmployeeExitAuthorizationService;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import org.flowable.task.api.Task;
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
    void assetManagerCanCompleteAssetClearanceOnlyUnlessAdmin() {
        when(auth.get("groups")).thenReturn(List.of("assetManager"));
        Employee actor = employee(7L);

        assertTrue(service.canCompleteClearance(ClearanceType.ASSET_AND_FACILITIES, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.MANAGER_HANDOVER, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.IT_AND_SECURITY, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.HR_AND_LEGAL, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.PAYROLL_AND_FINANCE, actor));
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
    void departmentHeadCanCompleteManagerHandoverClearanceOnlyUnlessAdmin() {
        when(auth.get("groups")).thenReturn(List.of("departmentHead"));
        Employee actor = employee(7L);

        assertTrue(service.canCompleteClearance(ClearanceType.MANAGER_HANDOVER, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.ASSET_AND_FACILITIES, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.IT_AND_SECURITY, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.HR_AND_LEGAL, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.PAYROLL_AND_FINANCE, actor));
    }

    @Test
    void slashPrefixedGroupIsNotAcceptedForAssetManagerOrDepartmentHead() {
        when(auth.get("groups")).thenReturn(List.of("/assetManager", "/departmentHead"));

        assertFalse(service.canManageAssets());
        assertFalse(service.canCompleteClearance(ClearanceType.ASSET_AND_FACILITIES, employee(7L)));
        assertFalse(service.canCompleteClearance(ClearanceType.MANAGER_HANDOVER, employee(7L)));
    }

    @Test
    void financeCanCompletePayrollClearanceOnly() {
        when(auth.isFinancialOfficer()).thenReturn(true);
        Employee actor = employee(7L);

        assertTrue(service.canCompleteClearance(ClearanceType.PAYROLL_AND_FINANCE, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.MANAGER_HANDOVER, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.ASSET_AND_FACILITIES, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.IT_AND_SECURITY, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.HR_AND_LEGAL, actor));
    }

    @Test
    void hrCanCompleteHrAndLegalClearanceOnlyUnlessAdmin() {
        when(auth.isHumanResource()).thenReturn(true);
        Employee actor = employee(7L);

        assertTrue(service.canCompleteClearance(ClearanceType.HR_AND_LEGAL, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.MANAGER_HANDOVER, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.ASSET_AND_FACILITIES, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.IT_AND_SECURITY, actor));
        assertFalse(service.canCompleteClearance(ClearanceType.PAYROLL_AND_FINANCE, actor));
    }

    @Test
    void adminCanCompleteAllClearanceTypes() {
        when(auth.isAdmin()).thenReturn(true);
        Employee actor = employee(7L);

        for (ClearanceType type : ClearanceType.values()) {
            assertTrue(service.canCompleteClearance(type, actor), type + " should be completable by admin");
        }
    }

    @Test
    void employeeCanSelfViewOwnExitOnly() {
        Employee actor = employee(7L);
        EmployeeExitCase own = exit(7L);
        EmployeeExitCase other = exit(8L);

        assertTrue(service.canViewSelfServiceExit(own, actor));
        assertFalse(service.canViewSelfServiceExit(other, actor));
    }

    @Test
    void exitingEmployeeCannotOperationalViewJustByOwningTheCase() {
        Employee actor = employee(7L);
        EmployeeExitCase own = exit(7L);

        assertFalse(service.canViewOperationalExit(own, actor, List.of()));
    }

    @Test
    void hrCanOperationalView() {
        when(auth.isHumanResource()).thenReturn(true);

        assertTrue(service.canViewOperationalExit(exit(8L), employee(7L), List.of()));
    }

    @Test
    void adminCanOperationalView() {
        when(auth.isAdmin()).thenReturn(true);

        assertTrue(service.canViewOperationalExit(exit(8L), employee(7L), List.of()));
    }

    @Test
    void financeCanOperationalView() {
        when(auth.isFinancialOfficer()).thenReturn(true);

        assertTrue(service.canViewOperationalExit(exit(8L), employee(7L), List.of()));
    }

    @Test
    void assetManagerCanOperationalView() {
        when(auth.get("groups")).thenReturn(List.of("assetManager"));

        assertTrue(service.canViewOperationalExit(exit(8L), employee(7L), List.of()));
    }

    @Test
    void departmentHeadCanOperationalView() {
        when(auth.get("groups")).thenReturn(List.of("departmentHead"));

        assertTrue(service.canViewOperationalExit(exit(8L), employee(7L), List.of()));
    }

    @Test
    void activeTaskAssigneeCanOperationalViewWithoutAnyOtherRole() {
        Task task = mock(Task.class);
        when(task.getAssignee()).thenReturn("7");

        assertTrue(service.canViewOperationalExit(exit(8L), employee(7L), List.of(task)));
    }

    @Test
    void unrelatedEmployeeCannotOperationalView() {
        Task task = mock(Task.class);
        when(task.getAssignee()).thenReturn("99");

        assertFalse(service.canViewOperationalExit(exit(8L), employee(7L), List.of(task)));
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