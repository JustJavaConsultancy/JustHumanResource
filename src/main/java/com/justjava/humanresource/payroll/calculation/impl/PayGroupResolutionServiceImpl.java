package com.justjava.humanresource.payroll.calculation.impl;


import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.payroll.calculation.PayGroupResolutionService;
import com.justjava.humanresource.payroll.calculation.dto.ResolvedPayComponents;
import com.justjava.humanresource.payroll.entity.Allowance;
import com.justjava.humanresource.payroll.entity.Deduction;
import com.justjava.humanresource.payroll.repositories.EmployeeAllowanceRepository;
import com.justjava.humanresource.payroll.repositories.EmployeeDeductionRepository;
import com.justjava.humanresource.payroll.repositories.PayGroupAllowanceRepository;
import com.justjava.humanresource.payroll.repositories.PayGroupDeductionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayGroupResolutionServiceImpl implements PayGroupResolutionService {

    private final PayGroupAllowanceRepository payGroupAllowanceRepository;
    private final PayGroupDeductionRepository payGroupDeductionRepository;
    private final EmployeeAllowanceRepository employeeAllowanceRepository;
    private final EmployeeDeductionRepository employeeDeductionRepository;

    @Override
    public ResolvedPayComponents resolve(Employee employee) {

        Map<String, Allowance> allowanceMap = new LinkedHashMap<>();
        Map<String, Deduction> deductionMap = new LinkedHashMap<>();

        // 1️⃣ Resolve Pay Group hierarchy (root → leaf)
        List<PayGroup> hierarchy = resolveHierarchy(employee.getPayGroup());

        for (PayGroup group : hierarchy) {

            payGroupAllowanceRepository
                    .findByPayGroup(group)
                    .forEach(pga ->
                            allowanceMap.putIfAbsent(
                                    pga.getAllowance().getCode(),
                                    pga.getAllowance()
                            )
                    );

            payGroupDeductionRepository
                    .findByPayGroup(group)
                    .forEach(pgd ->
                            deductionMap.putIfAbsent(
                                    pgd.getDeduction().getCode(),
                                    pgd.getDeduction()
                            )
                    );
        }

        // 2️⃣ Apply employee overrides (highest priority)
        employeeAllowanceRepository
                .findByEmployee(employee)
                .forEach(ea -> {
                    if (ea.isOverridden()) {
                        allowanceMap.put(
                                ea.getAllowance().getCode(),
                                ea.getAllowance()
                        );
                    }
                });

        employeeDeductionRepository
                .findByEmployee(employee)
                .forEach(ed -> {
                    if (ed.isOverridden()) {
                        deductionMap.put(
                                ed.getDeduction().getCode(),
                                ed.getDeduction()
                        );
                    }
                });

        return new ResolvedPayComponents(
                new ArrayList<>(allowanceMap.values()),
                new ArrayList<>(deductionMap.values())
        );
    }

    /**
     * Resolves PayGroup hierarchy from root → leaf
     */
    private List<PayGroup> resolveHierarchy(PayGroup leaf) {

        LinkedList<PayGroup> hierarchy = new LinkedList<>();
        PayGroup current = leaf;

        while (current != null) {
            hierarchy.addFirst(current);
            current = current.getParent();
        }
        return hierarchy;
    }
}
