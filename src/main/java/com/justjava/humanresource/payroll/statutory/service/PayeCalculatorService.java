package com.justjava.humanresource.payroll.statutory.service;

import java.math.BigDecimal;

public interface PayeCalculatorService {

    BigDecimal calculateTax(BigDecimal taxableAmount);
}