package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.dispatcher.PayrollMessageDispatcher;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.payroll.service.EmployeePositionHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollChangeOrchestratorImplTest {

    @Mock
    private PayrollMessageDispatcher dispatcher;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private EmployeePositionHistoryService positionHistoryService;

    private PayrollChangeOrchestratorImpl orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new PayrollChangeOrchestratorImpl(
                dispatcher,
                employeeRepository,
                positionHistoryService
        );
    }

    @Test
    void recalculateForEmployee_shouldUpdatePositionAndDispatchMessage() {
        LocalDate effectiveDate = LocalDate.of(2026, 5, 1);
        Employee employee = employee(1L, 10L, 20L, 30L);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        orchestrator.recalculateForEmployee(1L, effectiveDate);

        verify(positionHistoryService).changePosition(1L, 10L, 20L, 30L, effectiveDate);
        verify(dispatcher).requestPayroll(1L, effectiveDate);
    }

    @Test
    void recalculateForPayGroup_shouldProcessAllEmployeesInGroup() {
        LocalDate effectiveDate = LocalDate.of(2026, 5, 1);
        Employee first = employee(1L, 10L, 20L, 30L);
        Employee second = employee(2L, 11L, 21L, 31L);
        when(employeeRepository.findByPayGroup_Id(50L)).thenReturn(List.of(first, second));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(first));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(second));

        orchestrator.recalculateForPayGroup(50L, effectiveDate);

        verify(positionHistoryService).changePosition(1L, 10L, 20L, 30L, effectiveDate);
        verify(positionHistoryService).changePosition(2L, 11L, 21L, 31L, effectiveDate);
        verify(dispatcher).requestPayroll(1L, effectiveDate);
        verify(dispatcher).requestPayroll(2L, effectiveDate);
    }

    @Test
    void recalculateForJobStep_shouldProcessAllEmployeesInJobStep() {
        LocalDate effectiveDate = LocalDate.of(2026, 5, 1);
        Employee employee = employee(3L, 12L, 22L, 32L);
        when(employeeRepository.findByJobStep_Id(70L)).thenReturn(List.of(employee));
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(employee));

        orchestrator.recalculateForJobStep(70L, effectiveDate);

        verify(positionHistoryService).changePosition(3L, 12L, 22L, 32L, effectiveDate);
        verify(dispatcher).requestPayroll(3L, effectiveDate);
    }

    @Test
    void updateEmployeePositionHistory_shouldThrowWhenEmployeeIsMissing() {
        when(employeeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                orchestrator.updateEmployeePositionHistory(404L, LocalDate.of(2026, 5, 1)));
    }

    @Test
    void recalculateForPayGroup_shouldDoNothingWhenNoEmployeeIsFound() {
        when(employeeRepository.findByPayGroup_Id(50L)).thenReturn(List.of());

        orchestrator.recalculateForPayGroup(50L, LocalDate.of(2026, 5, 1));

        verify(positionHistoryService, times(0))
                .changePosition(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(LocalDate.class));
    }

    private static Employee employee(Long id, Long departmentId, Long jobStepId, Long payGroupId) {
        Department department = new Department();
        department.setId(departmentId);

        JobStep jobStep = new JobStep();
        jobStep.setId(jobStepId);

        PayGroup payGroup = new PayGroup();
        payGroup.setId(payGroupId);

        Employee employee = new Employee();
        employee.setId(id);
        employee.setDepartment(department);
        employee.setJobStep(jobStep);
        employee.setPayGroup(payGroup);
        return employee;
    }
}
