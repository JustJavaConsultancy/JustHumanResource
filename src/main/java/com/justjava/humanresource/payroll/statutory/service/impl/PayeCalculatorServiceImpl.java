package com.justjava.humanresource.payroll.statutory.service.impl;

import com.justjava.humanresource.payroll.statutory.entity.PayeTaxBand;
import com.justjava.humanresource.payroll.statutory.repositories.PayeTaxBandRepository;
import com.justjava.humanresource.payroll.statutory.service.PayeCalculatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayeCalculatorServiceImpl implements PayeCalculatorService {

    private final PayeTaxBandRepository taxBandRepository;

    @Override
    public BigDecimal calculateTax(BigDecimal taxableAmount) {

        List<PayeTaxBand> bands = taxBandRepository.findAll();
        BigDecimal tax = BigDecimal.ZERO;

        for (PayeTaxBand band : bands) {
            if (taxableAmount.compareTo(band.getLowerBound()) > 0) {
                BigDecimal upper =
                        taxableAmount.min(band.getUpperBound());

                BigDecimal bandAmount =
                        upper.subtract(band.getLowerBound());

                tax = tax.add(
                        bandAmount.multiply(band.getRate())
                                .divide(BigDecimal.valueOf(100))
                );
            }
        }
        return tax;
    }
}
