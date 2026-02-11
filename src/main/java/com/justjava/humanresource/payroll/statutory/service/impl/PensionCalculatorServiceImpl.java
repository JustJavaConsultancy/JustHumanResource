package com.justjava.humanresource.payroll.statutory.service.impl;

import com.justjava.humanresource.payroll.statutory.service.PensionCalculatorService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PensionCalculatorServiceImpl implements PensionCalculatorService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 2;

    @Override
    public BigDecimal calculateEmployeeContribution(
            BigDecimal pensionableAmount,
            BigDecimal rate) {

        return calculate(pensionableAmount, rate);
    }

    @Override
    public BigDecimal calculateEmployerContribution(
            BigDecimal pensionableAmount,
            BigDecimal rate) {

        return calculate(pensionableAmount, rate);
    }

    private BigDecimal calculate(
            BigDecimal pensionableAmount,
            BigDecimal rate) {

        if (pensionableAmount == null
                || rate == null
                || pensionableAmount.compareTo(BigDecimal.ZERO) <= 0
                || rate.compareTo(BigDecimal.ZERO) <= 0) {

            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal contribution = pensionableAmount
                .multiply(rate)
                .divide(ONE_HUNDRED, SCALE, RoundingMode.HALF_UP);

        return contribution.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
