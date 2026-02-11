package com.justjava.humanresource.payroll.statutory.service.impl;

import com.justjava.humanresource.common.enums.RecordStatus;
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
    public BigDecimal calculateTax(BigDecimal taxableAmount, LocalDate payrollDate) {

        if (taxableAmount == null
                || taxableAmount.compareTo(BigDecimal.ZERO) <= 0) {

            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }

        List<PayeTaxBand> bands = fetchEffectiveBands(payrollDate);

        BigDecimal totalTax = BigDecimal.ZERO;

        for (PayeTaxBand band : bands) {

            if (taxableAmount.compareTo(band.getLowerBound()) <= 0) {
                continue;
            }

            BigDecimal upperBound = band.getUpperBound() == null
                    ? taxableAmount
                    : taxableAmount.min(band.getUpperBound());

            BigDecimal bandAmount = upperBound.subtract(band.getLowerBound());

            if (bandAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal bandTax = bandAmount
                    .multiply(band.getRate())
                    .divide(ONE_HUNDRED, SCALE, RoundingMode.HALF_UP);

            totalTax = totalTax.add(bandTax);
        }

        return totalTax.setScale(SCALE, RoundingMode.HALF_UP);
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
