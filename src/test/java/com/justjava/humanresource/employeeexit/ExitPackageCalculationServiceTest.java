package com.justjava.humanresource.employeeexit;

import com.justjava.humanresource.employeeexit.entity.*;
import com.justjava.humanresource.employeeexit.enums.*;
import com.justjava.humanresource.employeeexit.repository.*;
import com.justjava.humanresource.employeeexit.service.ExitPackageCalculationService;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class ExitPackageCalculationServiceTest {
    @Mock ExitPackageRuleRepository rules;
    @Mock ExitPackageComponentRepository components;
    @Mock EmployeeRepository employees;
    ExitPackageCalculationService service;

    @BeforeEach
    void setUp() {
        service = new ExitPackageCalculationService(rules, components, employees);
    }

    @Test
    void calculatesApplicablePercentOfBasicRule() {
        Employee employee = new Employee();
        employee.setId(7L);
        employee.setDateOfHire(LocalDate.now().minusYears(4));
        EmployeeExitCase exit = new EmployeeExitCase();
        exit.setEmployeeId(7L);
        exit.setExitType(ExitType.RESIGNATION);
        exit.setProposedLastWorkingDate(LocalDate.now());

        ExitPackageComponent component = new ExitPackageComponent();
        component.setId(3L);
        component.setComponentCode("EXIT_SEVERANCE");
        component.setName("Severance");
        component.setLineType(SettlementLineType.SEVERANCE);
        component.setEarning(true);
        component.setTaxable(true);
        component.setActive(true);

        ExitPackageRule rule = new ExitPackageRule();
        rule.setId(4L);
        rule.setComponentId(3L);
        rule.setCalculationMethod(ExitPackageCalculationMethod.PERCENT_OF_BASIC);
        rule.setPercentage(new BigDecimal("25"));
        rule.setAppliesToExitTypes("RESIGNATION,RETIREMENT");
        rule.setActive(true);

        when(employees.findById(7L)).thenReturn(Optional.of(employee));
        when(rules.findByActiveTrue()).thenReturn(List.of(rule));
        when(components.findById(3L)).thenReturn(Optional.of(component));

        var result = service.calculate(exit, new BigDecimal("100000"), BigDecimal.ZERO);

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("25000.00"), result.get(0).getAmount());
        assertEquals("EXIT_PACKAGE_RULE_4", result.get(0).getSource());
    }

    @Test
    void skipsManualRulesDuringAutomaticCalculation() {
        Employee employee = new Employee();
        employee.setId(7L);
        EmployeeExitCase exit = new EmployeeExitCase();
        exit.setEmployeeId(7L);
        exit.setExitType(ExitType.RESIGNATION);
        ExitPackageRule rule = new ExitPackageRule();
        rule.setCalculationMethod(ExitPackageCalculationMethod.MANUAL);
        rule.setActive(true);

        when(employees.findById(7L)).thenReturn(Optional.of(employee));
        when(rules.findByActiveTrue()).thenReturn(List.of(rule));

        assertTrue(service.calculate(exit, BigDecimal.TEN, BigDecimal.ZERO).isEmpty());
    }
}
