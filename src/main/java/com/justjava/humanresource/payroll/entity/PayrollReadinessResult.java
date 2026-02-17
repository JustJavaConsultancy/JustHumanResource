package com.justjava.humanresource.payroll.entity;

import java.util.List;

public record PayrollReadinessResult(
        boolean ready,
        List<String> missingItems
) {}
