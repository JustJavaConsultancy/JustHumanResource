package com.justjava.humanresource.payroll.statutory.service;

import java.math.BigDecimal;

public interface PensionCalculatorService {

    BigDecimal calculateEmployeeContribution(
            BigDecimal pensionableAmount,
            BigDecimal rate
    );

    BigDecimal calculateEmployerContribution(
            BigDecimal pensionableAmount,
            BigDecimal rate
    );
}
