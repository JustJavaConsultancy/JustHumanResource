package com.justjava.humanresource.payroll.statutory.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PensionCalculatorServiceImplTest {

    private final PensionCalculatorServiceImpl service = new PensionCalculatorServiceImpl();

    @Test
    void calculateEmployeeContribution_shouldReturnCalculatedAmount() {
        BigDecimal result = service.calculateEmployeeContribution(
                new BigDecimal("100000.00"),
                new BigDecimal("8.00")
        );

        assertEquals(new BigDecimal("8000.00"), result);
    }

    @Test
    void calculateEmployerContribution_shouldReturnCalculatedAmount() {
        BigDecimal result = service.calculateEmployerContribution(
                new BigDecimal("100000.00"),
                new BigDecimal("10.00")
        );

        assertEquals(new BigDecimal("10000.00"), result);
    }

    @Test
    void calculateContribution_shouldReturnZeroForInvalidInputs() {
        assertEquals(new BigDecimal("0.00"), service.calculateEmployeeContribution(null, new BigDecimal("8")));
        assertEquals(new BigDecimal("0.00"), service.calculateEmployeeContribution(new BigDecimal("100"), null));
        assertEquals(new BigDecimal("0.00"), service.calculateEmployeeContribution(BigDecimal.ZERO, new BigDecimal("8")));
        assertEquals(new BigDecimal("0.00"), service.calculateEmployeeContribution(new BigDecimal("100"), BigDecimal.ZERO));
    }
}
