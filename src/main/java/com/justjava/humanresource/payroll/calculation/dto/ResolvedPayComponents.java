package com.justjava.humanresource.payroll.calculation.dto;


import com.justjava.humanresource.payroll.entity.Allowance;
import com.justjava.humanresource.payroll.entity.Deduction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class ResolvedPayComponents {

    private final List<Allowance> allowances;
    private final List<Deduction> deductions;
}
