package com.justjava.humanresource.payroll.statutory.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface PayeCalculatorService {

    BigDecimal calculateTax(BigDecimal taxableAmount);

    BigDecimal calculateTax(BigDecimal taxableAmount, LocalDate payrollDate);
}
