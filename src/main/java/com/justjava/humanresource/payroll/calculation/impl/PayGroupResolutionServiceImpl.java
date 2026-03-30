package com.justjava.humanresource.payroll.calculation.impl;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.payroll.calculation.PayGroupResolutionService;
import com.justjava.humanresource.payroll.calculation.dto.ResolvedPayComponents;
import com.justjava.humanresource.payroll.entity.*;
import com.justjava.humanresource.payroll.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PayGroupResolutionServiceImpl implements PayGroupResolutionService {

    private final PayGroupAllowanceRepository payGroupAllowanceRepository;
    private final PayGroupDeductionRepository payGroupDeductionRepository;
    private final EmployeeAllowanceRepository employeeAllowanceRepository;
    private final EmployeeDeductionRepository employeeDeductionRepository;

    private final PayGroupTaxReliefRepository payGroupTaxReliefRepository;
    private final EmployeeTaxReliefRepository employeeTaxReliefRepository;

    @Override
    public ResolvedPayComponents resolve(
            PayGroup payGroup,
            Employee employee,
            LocalDate payrollDate) {

        Map<String, Allowance> allowanceMap = new LinkedHashMap<>();
        Map<String, Deduction> deductionMap = new LinkedHashMap<>();
        Map<String, TaxRelief> taxReliefMap = new LinkedHashMap<>();

    /* ============================================================
       1️⃣ Resolve PayGroup Hierarchy (Root → Leaf)
       ============================================================ */

        List<PayGroup> hierarchy = resolveHierarchy(payGroup);

        for (PayGroup group : hierarchy) {

        /* ---------------------------
           ALLOWANCES
           --------------------------- */

            List<PayGroupAllowance> groupAllowances =
                    payGroupAllowanceRepository.findActiveAllowances(
                            group.getId(),
                            payrollDate,
                            RecordStatus.ACTIVE
                    );

            for (PayGroupAllowance mapping : groupAllowances) {

                Allowance allowance = mapping.getAllowance();

                if (mapping.getOverrideAmount() != null
                        && mapping.getOverrideAmount().compareTo(BigDecimal.ZERO) > 0) {

                    allowance = cloneAllowanceWithOverride(
                            allowance,
                            mapping.getOverrideAmount()
                    );
                }

                allowanceMap.putIfAbsent(
                        allowance.getCode(),
                        allowance
                );
            }

        /* ---------------------------
           DEDUCTIONS
           --------------------------- */

            List<PayGroupDeduction> groupDeductions =
                    payGroupDeductionRepository.findActiveDeductions(
                            group.getId(),
                            payrollDate,
                            RecordStatus.ACTIVE
                    );

            for (PayGroupDeduction mapping : groupDeductions) {

                Deduction deduction = mapping.getDeduction();

                if (mapping.getOverrideAmount() != null
                        && mapping.getOverrideAmount().compareTo(BigDecimal.ZERO) > 0) {

                    deduction = cloneDeductionWithOverride(
                            deduction,
                            mapping.getOverrideAmount()
                    );
                }

                deductionMap.putIfAbsent(
                        deduction.getCode(),
                        deduction
                );
            }

        /* ---------------------------
           TAX RELIEFS (NEW)
           --------------------------- */

            List<PayGroupTaxRelief> groupReliefs =
                    payGroupTaxReliefRepository.findActiveReliefs(
                            group.getId(),
                            payrollDate,
                            RecordStatus.ACTIVE
                    );

            for (PayGroupTaxRelief mapping : groupReliefs) {

                TaxRelief relief = mapping.getTaxRelief();

                if (mapping.getOverrideAmount() != null
                        && mapping.getOverrideAmount().compareTo(BigDecimal.ZERO) > 0) {

                    relief = cloneReliefWithOverride(
                            relief,
                            mapping.getOverrideAmount()
                    );
                }

                taxReliefMap.putIfAbsent(
                        relief.getCode(),
                        relief
                );
            }
        }

    /* ============================================================
       2️⃣ Employee Overrides (Highest Priority)
       ============================================================ */

    /* ---------------------------
       ALLOWANCES
       --------------------------- */

        List<EmployeeAllowance> employeeAllowances =
                employeeAllowanceRepository.findActiveAllowances(
                        employee.getId(),
                        payrollDate,
                        RecordStatus.ACTIVE
                );

        for (EmployeeAllowance mapping : employeeAllowances) {

            Allowance allowance = mapping.getAllowance();

            if (mapping.isOverridden()
                    && mapping.getOverrideAmount() != null) {

                allowance = cloneAllowanceWithOverride(
                        allowance,
                        mapping.getOverrideAmount()
                );
            }

            allowanceMap.put(
                    allowance.getCode(),
                    allowance
            );
        }

    /* ---------------------------
       DEDUCTIONS
       --------------------------- */

        List<EmployeeDeduction> employeeDeductions =
                employeeDeductionRepository.findActiveDeductions(
                        employee.getId(),
                        payrollDate,
                        RecordStatus.ACTIVE
                );

        for (EmployeeDeduction mapping : employeeDeductions) {

            Deduction deduction = mapping.getDeduction();

            if (mapping.isOverridden()
                    && mapping.getOverrideAmount() != null) {

                deduction = cloneDeductionWithOverride(
                        deduction,
                        mapping.getOverrideAmount()
                );
            }

            deductionMap.put(
                    deduction.getCode(),
                    deduction
            );
        }
    /* ---------------------------
       TAX RELIEFS (NEW)
       --------------------------- */

        List<EmployeeTaxRelief> employeeReliefs =
                employeeTaxReliefRepository.findActiveReliefs(
                        employee.getId(),
                        payrollDate,
                        RecordStatus.ACTIVE
                );

        for (EmployeeTaxRelief mapping : employeeReliefs) {

            TaxRelief relief = mapping.getTaxRelief();

            if (mapping.isOverridden()
                    && mapping.getOverrideAmount() != null) {

                relief = cloneReliefWithOverride(
                        relief,
                        mapping.getOverrideAmount()
                );
            }

            taxReliefMap.put(
                    relief.getCode(),
                    relief
            );
        }

    /* ============================================================
       FINAL RESULT
       ============================================================ */

        return new ResolvedPayComponents(
                new ArrayList<>(allowanceMap.values()),
                new ArrayList<>(deductionMap.values()),
                new ArrayList<>(taxReliefMap.values())
        );
    }

    /* ============================================================
       Helper: Resolve Hierarchy
       ============================================================ */

    private List<PayGroup> resolveHierarchy(PayGroup leaf) {

        LinkedList<PayGroup> hierarchy = new LinkedList<>();
        PayGroup current = leaf;

        while (current != null) {
            hierarchy.addFirst(current);
            current = current.getParent();
        }
        return hierarchy;
    }

    /* ============================================================
       Helper: Clone Allowance With Override Amount
       ============================================================ */

/*    private Allowance cloneWithOverride(
            Allowance original,
            BigDecimal overrideAmount) {

        Allowance clone = new Allowance();
        clone.setCode(original.getCode());
        clone.setName(original.getName());
        clone.setTaxable(original.isTaxable());
        clone.setAmount(overrideAmount);
        return clone;
    }*/
    private Allowance cloneAllowanceWithOverride(
            Allowance original,
            BigDecimal overrideAmount) {

        Allowance clone = new Allowance();
        clone.setCode(original.getCode());
        clone.setName(original.getName());
        clone.setTaxable(original.isTaxable());
        clone.setPensionable(original.isPensionable());
        clone.setCalculationType(original.getCalculationType());
        clone.setPartOfGross(original.isPartOfGross());
        clone.setAmount(overrideAmount);
        return clone;
    }
    private Deduction cloneDeductionWithOverride(
            Deduction original,
            BigDecimal overrideAmount) {

        Deduction clone = new Deduction();

        clone.setCode(original.getCode());
        clone.setName(original.getName());

        clone.setCalculationType(original.getCalculationType());
        clone.setPercentageRate(original.getPercentageRate());
        clone.setFormulaExpression(original.getFormulaExpression());

        clone.setAmount(overrideAmount);

        // Optional fields if present in your entity
        //clone.setActive(original.isActive());

        return clone;
    }
    private TaxRelief cloneReliefWithOverride(
            TaxRelief original,
            BigDecimal overrideAmount) {

        TaxRelief clone = new TaxRelief();
        clone.setCode(original.getCode());
        clone.setName(original.getName());
        clone.setCalculationType(original.getCalculationType());
        clone.setAmount(overrideAmount);
        clone.setPercentageRate(original.getPercentageRate());
        clone.setFormulaExpression(original.getFormulaExpression());
        clone.setMaximumAmount(original.getMaximumAmount());
        clone.setActive(original.isActive());
        return clone;
    }
}

