package com.justjava.humanresource.payroll.calculation.impl;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.payroll.calculation.dto.ResolvedPayComponents;
import com.justjava.humanresource.payroll.entity.Allowance;
import com.justjava.humanresource.payroll.entity.Deduction;
import com.justjava.humanresource.payroll.entity.EmployeeAllowance;
import com.justjava.humanresource.payroll.entity.EmployeeDeduction;
import com.justjava.humanresource.payroll.entity.EmployeeTaxRelief;
import com.justjava.humanresource.payroll.entity.PayGroupAllowance;
import com.justjava.humanresource.payroll.entity.PayGroupDeduction;
import com.justjava.humanresource.payroll.entity.PayGroupTaxRelief;
import com.justjava.humanresource.payroll.entity.TaxRelief;
import com.justjava.humanresource.payroll.enums.PayComponentCalculationType;
import com.justjava.humanresource.payroll.repositories.EmployeeAllowanceRepository;
import com.justjava.humanresource.payroll.repositories.EmployeeDeductionRepository;
import com.justjava.humanresource.payroll.repositories.EmployeeTaxReliefRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayGroupResolutionServiceImplTest {

    @Mock
    private PayGroupAllowanceRepository payGroupAllowanceRepository;
    @Mock
    private PayGroupDeductionRepository payGroupDeductionRepository;
    @Mock
    private EmployeeAllowanceRepository employeeAllowanceRepository;
    @Mock
    private EmployeeDeductionRepository employeeDeductionRepository;
    @Mock
    private PayGroupTaxReliefRepository payGroupTaxReliefRepository;
    @Mock
    private EmployeeTaxReliefRepository employeeTaxReliefRepository;

    private PayGroupResolutionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PayGroupResolutionServiceImpl(
                payGroupAllowanceRepository,
                payGroupDeductionRepository,
                employeeAllowanceRepository,
                employeeDeductionRepository,
                payGroupTaxReliefRepository,
                employeeTaxReliefRepository
        );
    }

    @Test
    void resolve_shouldApplyHierarchyThenEmployeeOverrides() {
        LocalDate payrollDate = LocalDate.of(2026, 5, 1);
        PayGroup root = payGroup(1L, null);
        PayGroup leaf = payGroup(2L, root);
        Employee employee = employee(10L);

        Allowance allowance = allowance("ALW1", "1000");
        allowance.setTaxable(true);
        allowance.setPensionable(true);
        allowance.setPartOfGross(true);
        allowance.setOutOfPayroll(false);
        allowance.setCalculationType(PayComponentCalculationType.PERCENTAGE_OF_BASIC);
        allowance.setPercentageRate(new BigDecimal("10.0000"));
        allowance.setFormulaExpression("BASIC * 0.1");
        allowance.setProratable(true);

        Deduction deduction = deduction("DED1", "500");
        deduction.setCalculationType(PayComponentCalculationType.PERCENTAGE_OF_BASIC);
        deduction.setPercentageRate(new BigDecimal("2.5000"));
        deduction.setFormulaExpression("GROSS * 0.025");

        TaxRelief relief = taxRelief("TR1", "200");
        relief.setCalculationType(PayComponentCalculationType.FIXED_AMOUNT);
        relief.setPercentageRate(new BigDecimal("1.500000"));
        relief.setFormulaExpression("FORMULA");
        relief.setMaximumAmount(new BigDecimal("1000"));
        relief.setActive(true);

        when(payGroupAllowanceRepository.findActiveAllowances(eq(1L), eq(payrollDate), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of(payGroupAllowance(allowance, null)));
        when(payGroupAllowanceRepository.findActiveAllowances(eq(2L), eq(payrollDate), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of(payGroupAllowance(allowance, new BigDecimal("1500"))));

        when(payGroupDeductionRepository.findActiveDeductions(eq(1L), eq(payrollDate), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of(payGroupDeduction(deduction, null)));
        when(payGroupDeductionRepository.findActiveDeductions(eq(2L), eq(payrollDate), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of(payGroupDeduction(deduction, new BigDecimal("700"))));

        when(payGroupTaxReliefRepository.findActiveReliefs(eq(1L), eq(payrollDate), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of(payGroupTaxRelief(relief, null)));
        when(payGroupTaxReliefRepository.findActiveReliefs(eq(2L), eq(payrollDate), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of(payGroupTaxRelief(relief, new BigDecimal("400"))));

        when(employeeAllowanceRepository.findActiveAllowances(eq(10L), eq(payrollDate), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of(employeeAllowance(allowance, true, new BigDecimal("2000"))));
        when(employeeDeductionRepository.findActiveDeductions(eq(10L), eq(payrollDate), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of(employeeDeduction(deduction, true, new BigDecimal("900"))));
        when(employeeTaxReliefRepository.findActiveReliefs(eq(10L), eq(payrollDate), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of(employeeTaxRelief(relief, true, new BigDecimal("800"))));

        ResolvedPayComponents result = service.resolve(leaf, employee, payrollDate);

        assertEquals(1, result.getAllowances().size());
        assertEquals(new BigDecimal("2000"), result.getAllowances().getFirst().getAmount());
        assertEquals(true, result.getAllowances().getFirst().isPensionable());
        assertEquals(PayComponentCalculationType.PERCENTAGE_OF_BASIC, result.getAllowances().getFirst().getCalculationType());

        assertEquals(1, result.getDeductions().size());
        assertEquals(new BigDecimal("900"), result.getDeductions().getFirst().getAmount());
        assertEquals(PayComponentCalculationType.PERCENTAGE_OF_BASIC, result.getDeductions().getFirst().getCalculationType());

        assertEquals(1, result.getTaxReliefs().size());
        assertEquals(new BigDecimal("800"), result.getTaxReliefs().getFirst().getAmount());
        assertEquals(true, result.getTaxReliefs().getFirst().isActive());
    }

    @Test
    void resolve_shouldIgnoreNonPositiveGroupOverrides() {
        LocalDate payrollDate = LocalDate.of(2026, 5, 1);
        PayGroup group = payGroup(3L, null);
        Employee employee = employee(20L);

        Allowance allowance = allowance("ALW2", "1000");
        Deduction deduction = deduction("DED2", "500");
        TaxRelief relief = taxRelief("TR2", "300");

        when(payGroupAllowanceRepository.findActiveAllowances(3L, payrollDate, RecordStatus.ACTIVE))
                .thenReturn(List.of(payGroupAllowance(allowance, null)));
        when(payGroupDeductionRepository.findActiveDeductions(3L, payrollDate, RecordStatus.ACTIVE))
                .thenReturn(List.of(payGroupDeduction(deduction, BigDecimal.ZERO)));
        when(payGroupTaxReliefRepository.findActiveReliefs(3L, payrollDate, RecordStatus.ACTIVE))
                .thenReturn(List.of(payGroupTaxRelief(relief, new BigDecimal("-1"))));

        when(employeeAllowanceRepository.findActiveAllowances(20L, payrollDate, RecordStatus.ACTIVE))
                .thenReturn(List.of(employeeAllowance(allowance, false, new BigDecimal("9999"))));
        when(employeeDeductionRepository.findActiveDeductions(20L, payrollDate, RecordStatus.ACTIVE))
                .thenReturn(List.of(employeeDeduction(deduction, false, new BigDecimal("9999"))));
        when(employeeTaxReliefRepository.findActiveReliefs(20L, payrollDate, RecordStatus.ACTIVE))
                .thenReturn(List.of(employeeTaxRelief(relief, false, new BigDecimal("9999"))));

        ResolvedPayComponents result = service.resolve(group, employee, payrollDate);

        assertEquals(new BigDecimal("1000"), result.getAllowances().getFirst().getAmount());
        assertEquals(new BigDecimal("500"), result.getDeductions().getFirst().getAmount());
        assertEquals(new BigDecimal("300"), result.getTaxReliefs().getFirst().getAmount());
    }

    private static PayGroup payGroup(Long id, PayGroup parent) {
        PayGroup payGroup = new PayGroup();
        payGroup.setId(id);
        payGroup.setParent(parent);
        return payGroup;
    }

    private static Employee employee(Long id) {
        Employee e = new Employee();
        e.setId(id);
        return e;
    }

    private static Allowance allowance(String code, String amount) {
        Allowance allowance = new Allowance();
        allowance.setCode(code);
        allowance.setName(code);
        allowance.setAmount(new BigDecimal(amount));
        return allowance;
    }

    private static Deduction deduction(String code, String amount) {
        Deduction deduction = new Deduction();
        deduction.setCode(code);
        deduction.setName(code);
        deduction.setAmount(new BigDecimal(amount));
        return deduction;
    }

    private static TaxRelief taxRelief(String code, String amount) {
        TaxRelief relief = new TaxRelief();
        relief.setCode(code);
        relief.setName(code);
        relief.setAmount(new BigDecimal(amount));
        return relief;
    }

    private static PayGroupAllowance payGroupAllowance(Allowance allowance, BigDecimal override) {
        PayGroupAllowance mapping = new PayGroupAllowance();
        mapping.setAllowance(allowance);
        mapping.setOverrideAmount(override);
        return mapping;
    }

    private static PayGroupDeduction payGroupDeduction(Deduction deduction, BigDecimal override) {
        PayGroupDeduction mapping = new PayGroupDeduction();
        mapping.setDeduction(deduction);
        mapping.setOverrideAmount(override);
        return mapping;
    }

    private static PayGroupTaxRelief payGroupTaxRelief(TaxRelief relief, BigDecimal override) {
        PayGroupTaxRelief mapping = new PayGroupTaxRelief();
        mapping.setTaxRelief(relief);
        mapping.setOverrideAmount(override);
        return mapping;
    }

    private static EmployeeAllowance employeeAllowance(Allowance allowance, boolean overridden, BigDecimal override) {
        EmployeeAllowance mapping = new EmployeeAllowance();
        mapping.setAllowance(allowance);
        mapping.setOverridden(overridden);
        mapping.setOverrideAmount(override);
        return mapping;
    }

    private static EmployeeDeduction employeeDeduction(Deduction deduction, boolean overridden, BigDecimal override) {
        EmployeeDeduction mapping = new EmployeeDeduction();
        mapping.setDeduction(deduction);
        mapping.setOverridden(overridden);
        mapping.setOverrideAmount(override);
        return mapping;
    }

    private static EmployeeTaxRelief employeeTaxRelief(TaxRelief relief, boolean overridden, BigDecimal override) {
        EmployeeTaxRelief mapping = new EmployeeTaxRelief();
        mapping.setTaxRelief(relief);
        mapping.setOverridden(overridden);
        mapping.setOverrideAmount(override);
        return mapping;
    }
}
