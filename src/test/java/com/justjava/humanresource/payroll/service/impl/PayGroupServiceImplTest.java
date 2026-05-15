package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeePositionHistory;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.repository.EmployeePositionHistoryRepository;
import com.justjava.humanresource.hr.repository.PayGroupRepository;
import com.justjava.humanresource.payroll.entity.Allowance;
import com.justjava.humanresource.payroll.entity.Deduction;
import com.justjava.humanresource.payroll.entity.PayGroupAllowance;
import com.justjava.humanresource.payroll.entity.PayGroupDeduction;
import com.justjava.humanresource.payroll.entity.PayGroupTaxRelief;
import com.justjava.humanresource.payroll.entity.TaxRelief;
import com.justjava.humanresource.payroll.repositories.PayGroupAllowanceRepository;
import com.justjava.humanresource.payroll.repositories.PayGroupDeductionRepository;
import com.justjava.humanresource.payroll.repositories.PayGroupTaxReliefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayGroupServiceImplTest {

    @Mock
    private PayGroupRepository payGroupRepository;
    @Mock
    private PayGroupAllowanceRepository payGroupAllowanceRepository;
    @Mock
    private PayGroupDeductionRepository payGroupDeductionRepository;
    @Mock
    private PayGroupTaxReliefRepository payGroupTaxReliefRepository;
    @Mock
    private EmployeePositionHistoryRepository positionHistoryRepository;

    private PayGroupServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PayGroupServiceImpl(
                payGroupRepository,
                payGroupAllowanceRepository,
                payGroupDeductionRepository,
                payGroupTaxReliefRepository,
                positionHistoryRepository
        );
    }

    @Test
    void getAllowances_shouldMapEntityToViewDto() {
        Long payGroupId = 1L;
        LocalDate date = LocalDate.of(2026, 5, 1);
        when(payGroupRepository.findById(payGroupId)).thenReturn(Optional.of(new PayGroup()));
        when(payGroupAllowanceRepository.findActiveAllowances(payGroupId, date, RecordStatus.ACTIVE))
                .thenReturn(List.of(samplePayGroupAllowance()));

        var result = service.getAllowances(payGroupId, date);

        assertEquals(1, result.size());
        assertEquals(10L, result.getFirst().getAllowanceId());
        assertEquals("ALW001", result.getFirst().getAllowanceCode());
        assertEquals("Housing", result.getFirst().getAllowanceName());
        assertEquals(new BigDecimal("5000.00"), result.getFirst().getOverrideAmount());
    }

    @Test
    void getAllowances_shouldThrowWhenPayGroupDoesNotExist() {
        when(payGroupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.getAllowances(99L, LocalDate.now()));
    }

    @Test
    void getDeductions_shouldMapEntityToViewDto() {
        Long payGroupId = 1L;
        LocalDate date = LocalDate.of(2026, 5, 1);
        when(payGroupRepository.findById(payGroupId)).thenReturn(Optional.of(new PayGroup()));
        when(payGroupDeductionRepository.findActiveDeductions(payGroupId, date, RecordStatus.ACTIVE))
                .thenReturn(List.of(samplePayGroupDeduction()));

        var result = service.getDeductions(payGroupId, date);

        assertEquals(1, result.size());
        assertEquals(20L, result.getFirst().getDeductionId());
        assertEquals("DED001", result.getFirst().getDeductionCode());
        assertEquals("Tax", result.getFirst().getDeductionName());
    }

    @Test
    void getTaxReliefs_shouldMapEntityToViewDto() {
        Long payGroupId = 1L;
        LocalDate date = LocalDate.of(2026, 5, 1);
        when(payGroupRepository.findById(payGroupId)).thenReturn(Optional.of(new PayGroup()));
        when(payGroupTaxReliefRepository.findActiveReliefs(payGroupId, date, RecordStatus.ACTIVE))
                .thenReturn(List.of(samplePayGroupTaxRelief()));

        var result = service.getTaxReliefs(payGroupId, date);

        assertEquals(1, result.size());
        assertEquals(30L, result.getFirst().getTaxReliefId());
        assertEquals("TR001", result.getFirst().getTaxReliefCode());
        assertEquals("Consolidated Relief", result.getFirst().getTaxReliefName());
    }

    @Test
    void getEmployees_shouldMapEmployeePositionHistoryToViewDto() {
        Long payGroupId = 1L;
        LocalDate date = LocalDate.of(2026, 5, 1);
        when(positionHistoryRepository.findEmployeesByPayGroupAndDate(payGroupId, date, RecordStatus.ACTIVE))
                .thenReturn(List.of(samplePositionHistory()));

        var result = service.getEmployees(payGroupId, date);

        assertEquals(1, result.size());
        assertEquals(200L, result.getFirst().getEmployeeId());
        assertEquals("EMP-001", result.getFirst().getEmployeeNumber());
        assertEquals("Jane Doe", result.getFirst().getFullName());
    }

    @Test
    void getAllAssignedAllowances_shouldMapAllAssignedItems() {
        Long payGroupId = 1L;
        LocalDate date = LocalDate.of(2026, 5, 1);
        when(payGroupRepository.findById(payGroupId)).thenReturn(Optional.of(new PayGroup()));
        when(payGroupAllowanceRepository.findAllAssignedAllowances(eq(payGroupId), eq(date), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of(samplePayGroupAllowance()));

        var result = service.getAllAssignedAllowances(payGroupId, date);

        assertEquals(1, result.size());
        assertEquals("ALW001", result.getFirst().getAllowanceCode());
    }

    @Test
    void getAllAssignedDeductions_shouldMapAllAssignedItems() {
        Long payGroupId = 1L;
        LocalDate date = LocalDate.of(2026, 5, 1);
        when(payGroupRepository.findById(payGroupId)).thenReturn(Optional.of(new PayGroup()));
        when(payGroupDeductionRepository.findAllAssignedDeductions(eq(payGroupId), eq(date), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of(samplePayGroupDeduction()));

        var result = service.getAllAssignedDeductions(payGroupId, date);

        assertEquals(1, result.size());
        assertEquals("DED001", result.getFirst().getDeductionCode());
    }

    @Test
    void getAllAssignedTaxReliefs_shouldMapAllAssignedItems() {
        Long payGroupId = 1L;
        LocalDate date = LocalDate.of(2026, 5, 1);
        when(payGroupRepository.findById(payGroupId)).thenReturn(Optional.of(new PayGroup()));
        when(payGroupTaxReliefRepository.findAllAssignedReliefs(eq(payGroupId), eq(date), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of(samplePayGroupTaxRelief()));

        var result = service.getAllAssignedTaxReliefs(payGroupId, date);

        assertEquals(1, result.size());
        assertEquals("TR001", result.getFirst().getTaxReliefCode());
    }

    private static PayGroupAllowance samplePayGroupAllowance() {
        Allowance allowance = new Allowance();
        allowance.setId(10L);
        allowance.setCode("ALW001");
        allowance.setName("Housing");

        PayGroupAllowance mapping = new PayGroupAllowance();
        mapping.setAllowance(allowance);
        mapping.setOverrideAmount(new BigDecimal("5000.00"));
        mapping.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        mapping.setEffectiveTo(LocalDate.of(2026, 12, 31));
        return mapping;
    }

    private static PayGroupDeduction samplePayGroupDeduction() {
        Deduction deduction = new Deduction();
        deduction.setId(20L);
        deduction.setCode("DED001");
        deduction.setName("Tax");

        PayGroupDeduction mapping = new PayGroupDeduction();
        mapping.setDeduction(deduction);
        mapping.setOverrideAmount(new BigDecimal("3000.00"));
        mapping.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        mapping.setEffectiveTo(LocalDate.of(2026, 12, 31));
        return mapping;
    }

    private static PayGroupTaxRelief samplePayGroupTaxRelief() {
        TaxRelief relief = new TaxRelief();
        relief.setId(30L);
        relief.setCode("TR001");
        relief.setName("Consolidated Relief");

        PayGroupTaxRelief mapping = new PayGroupTaxRelief();
        mapping.setTaxRelief(relief);
        mapping.setOverrideAmount(new BigDecimal("12000.00"));
        mapping.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        mapping.setEffectiveTo(LocalDate.of(2026, 12, 31));
        return mapping;
    }

    private static EmployeePositionHistory samplePositionHistory() {
        Employee employee = new Employee();
        employee.setId(200L);
        employee.setEmployeeNumber("EMP-001");
        employee.setFirstName("Jane");
        employee.setLastName("Doe");

        EmployeePositionHistory position = new EmployeePositionHistory();
        position.setEmployee(employee);
        position.setEffectiveFrom(LocalDate.of(2026, 3, 1));
        return position;
    }
}
