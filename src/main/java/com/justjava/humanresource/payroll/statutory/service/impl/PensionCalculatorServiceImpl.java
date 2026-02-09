package com.justjava.humanresource.payroll.statutory.service.impl;


import com.justjava.humanresource.payroll.statutory.service.PensionCalculatorService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PensionCalculatorServiceImpl implements PensionCalculatorService {

    @Override
    public BigDecimal calculateEmployeeContribution(
            BigDecimal pensionableAmount,
            BigDecimal rate) {

        return pensionableAmount
                .multiply(rate)
                .divide(BigDecimal.valueOf(100));
    }

    @Override
    public BigDecimal calculateEmployerContribution(
            BigDecimal pensionableAmount,
            BigDecimal rate) {

        return pensionableAmount
                .multiply(rate)
                .divide(BigDecimal.valueOf(100));
    }
}
