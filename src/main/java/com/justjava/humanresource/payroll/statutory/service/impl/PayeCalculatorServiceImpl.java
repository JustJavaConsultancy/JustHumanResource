package com.justjava.humanresource.payroll.statutory.service.impl;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.statutory.entity.PayeTaxBand;
import com.justjava.humanresource.payroll.statutory.repositories.PayeTaxBandRepository;
import com.justjava.humanresource.payroll.statutory.service.PayeCalculatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayeCalculatorServiceImpl implements PayeCalculatorService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 2;

    private final PayeTaxBandRepository taxBandRepository;

    /* =========================
     * BACKWARD COMPATIBILITY
     * ========================= */

    @Override
    public BigDecimal calculateTax(BigDecimal taxableAmount) {
        return calculateTax(taxableAmount, LocalDate.now());
    }

    /* =========================
     * RETRO-SAFE CALCULATION
     * ========================= */

    @Override
    public BigDecimal calculateTax(BigDecimal annualTaxableAmount, LocalDate payrollDate) {

        if (annualTaxableAmount == null
                || annualTaxableAmount.compareTo(BigDecimal.ZERO) <= 0) {

            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }

        List<PayeTaxBand> bands = fetchEffectiveBands(payrollDate);

        BigDecimal totalTax = BigDecimal.ZERO;

        for (PayeTaxBand band : bands) {

            BigDecimal lower = band.getLowerBound();
            BigDecimal upper = band.getUpperBound(); // can be null
            BigDecimal rate = band.getRate(); // e.g. 7, 11, etc.

            if (annualTaxableAmount.compareTo(lower) <= 0) {
                continue;
            }

            BigDecimal taxableInBand;

            if (upper == null) {
                taxableInBand = annualTaxableAmount.subtract(lower);
            } else {
                BigDecimal cappedUpper = annualTaxableAmount.min(upper);
                taxableInBand = cappedUpper.subtract(lower);
            }

            if (taxableInBand.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal bandTax = taxableInBand
                    .multiply(rate)
                    .divide(ONE_HUNDRED, SCALE, RoundingMode.HALF_UP);

            totalTax = totalTax.add(bandTax);
        }

        return totalTax.setScale(SCALE, RoundingMode.HALF_UP);
    }
    @Override
    public BigDecimal calculateMonthlyTax(BigDecimal monthlyTaxable, LocalDate payrollDate) {

        if (monthlyTaxable == null
                || monthlyTaxable.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }

    /* --------------------------------------------------------
       STEP 1: Annualize
       -------------------------------------------------------- */

        BigDecimal annualTaxable =
                monthlyTaxable.multiply(BigDecimal.valueOf(12));

    /* --------------------------------------------------------
       STEP 2: Calculate annual tax using existing engine
       -------------------------------------------------------- */

        BigDecimal annualTax =
                calculateTax(annualTaxable, payrollDate);

    /* --------------------------------------------------------
       STEP 3: Convert back to monthly
       -------------------------------------------------------- */

        return annualTax
                .divide(BigDecimal.valueOf(12), SCALE, RoundingMode.HALF_UP);
    }
    /* =========================
     * EFFECTIVE BAND FETCHING
     * ========================= */

    private List<PayeTaxBand> fetchEffectiveBands(LocalDate payrollDate) {

        List<PayeTaxBand> bounded =
                taxBandRepository
                        .findByEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualAndStatusOrderByLowerBoundAsc(
                                payrollDate,
                                payrollDate,
                                RecordStatus.ACTIVE
                        );

        List<PayeTaxBand> openEnded =
                taxBandRepository
                        .findByEffectiveFromLessThanEqualAndEffectiveToIsNullAndStatusOrderByLowerBoundAsc(
                                payrollDate,
                                RecordStatus.ACTIVE
                        );

        List<PayeTaxBand> allBands = new ArrayList<>();
        allBands.addAll(bounded);
        allBands.addAll(openEnded);

        return allBands;
    }
}
