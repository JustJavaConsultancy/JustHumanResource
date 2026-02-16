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

    @Override
    public ResolvedPayComponents resolve(
            PayGroup payGroup,
            Employee employee,
            LocalDate payrollDate) {

        Map<String, Allowance> allowanceMap = new LinkedHashMap<>();
        Map<String, Deduction> deductionMap = new LinkedHashMap<>();

        /* ============================================================
           1️⃣ Resolve PayGroup Hierarchy (Root → Leaf)
           ============================================================ */

        List<PayGroup> hierarchy = resolveHierarchy(payGroup);

        for (PayGroup group : hierarchy) {

            List<PayGroupAllowance> groupAllowances =
                    payGroupAllowanceRepository.findActiveAllowances(
                            group.getId(),
                            payrollDate,
                            RecordStatus.ACTIVE
                    );

            for (PayGroupAllowance mapping : groupAllowances) {

                Allowance allowance = mapping.getAllowance();

                // Respect override amount if present
                if (mapping.getOverrideAmount() != null) {
                    allowance = cloneWithOverride(
                            allowance,
                            mapping.getOverrideAmount()
                    );
                }

                allowanceMap.putIfAbsent(
                        allowance.getCode(),
                        allowance
                );
            }

            List<PayGroupDeduction> groupDeductions =
                    payGroupDeductionRepository.findActiveDeductions(
                            group.getId(),
                            payrollDate,
                            RecordStatus.ACTIVE
                    );

            for (PayGroupDeduction mapping : groupDeductions) {
                deductionMap.putIfAbsent(
                        mapping.getDeduction().getCode(),
                        mapping.getDeduction()
                );
            }
        }

        /* ============================================================
           2️⃣ Employee Overrides (Highest Priority)
           ============================================================ */

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

                allowance = cloneWithOverride(
                        allowance,
                        mapping.getOverrideAmount()
                );
            }

            allowanceMap.put(
                    allowance.getCode(),
                    allowance
            );
        }

        List<EmployeeDeduction> employeeDeductions =
                employeeDeductionRepository.findActiveDeductions(
                        employee.getId(),
                        payrollDate,
                        RecordStatus.ACTIVE
                );

        for (EmployeeDeduction mapping : employeeDeductions) {

            deductionMap.put(
                    mapping.getDeduction().getCode(),
                    mapping.getDeduction()
            );
        }

        return new ResolvedPayComponents(
                new ArrayList<>(allowanceMap.values()),
                new ArrayList<>(deductionMap.values())
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

    private Allowance cloneWithOverride(
            Allowance original,
            BigDecimal overrideAmount) {

        Allowance clone = new Allowance();
        clone.setCode(original.getCode());
        clone.setName(original.getName());
        clone.setTaxable(original.isTaxable());
        clone.setAmount(overrideAmount);
        return clone;
    }
}
