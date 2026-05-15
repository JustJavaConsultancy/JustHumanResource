package com.justjava.humanresource.payroll.statutory.service.impl;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.statutory.entity.PayeTaxBand;
import com.justjava.humanresource.payroll.statutory.repositories.PayeTaxBandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayeCalculatorServiceImplTest {

    @Mock
    private PayeTaxBandRepository taxBandRepository;

    private PayeCalculatorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PayeCalculatorServiceImpl(taxBandRepository);
    }

    @Test
    void calculateTax_shouldReturnZeroForNullOrNonPositiveInput() {
        LocalDate payrollDate = LocalDate.of(2026, 5, 1);

        assertEquals(new BigDecimal("0.00"), service.calculateTax(null, payrollDate));
        assertEquals(new BigDecimal("0.00"), service.calculateTax(BigDecimal.ZERO, payrollDate));
        assertEquals(new BigDecimal("0.00"), service.calculateTax(new BigDecimal("-1"), payrollDate));
    }

    @Test
    void calculateTax_shouldCalculateProgressiveTaxAcrossBands() {
        LocalDate payrollDate = LocalDate.of(2026, 5, 1);
        List<PayeTaxBand> bounded = List.of(
                band("0", "300000", "7"),
                band("300000", "600000", "11")
        );
        List<PayeTaxBand> openEnded = List.of(
                band("600000", null, "15")
        );

        when(taxBandRepository
                .findByEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualAndStatusOrderByLowerBoundAsc(
                        eq(payrollDate), eq(payrollDate), eq(RecordStatus.ACTIVE)))
                .thenReturn(bounded);
        when(taxBandRepository
                .findByEffectiveFromLessThanEqualAndEffectiveToIsNullAndStatusOrderByLowerBoundAsc(
                        eq(payrollDate), eq(RecordStatus.ACTIVE)))
                .thenReturn(openEnded);

        BigDecimal result = service.calculateTax(new BigDecimal("900000"), payrollDate);

        assertEquals(new BigDecimal("99000.00"), result);
    }

    @Test
    void calculateTax_shouldApplyOnlyBandsReachedByAmount() {
        LocalDate payrollDate = LocalDate.of(2026, 5, 1);
        List<PayeTaxBand> bounded = List.of(
                band("0", "300000", "7"),
                band("300000", "600000", "11")
        );

        when(taxBandRepository
                .findByEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualAndStatusOrderByLowerBoundAsc(
                        eq(payrollDate), eq(payrollDate), eq(RecordStatus.ACTIVE)))
                .thenReturn(bounded);
        when(taxBandRepository
                .findByEffectiveFromLessThanEqualAndEffectiveToIsNullAndStatusOrderByLowerBoundAsc(
                        eq(payrollDate), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of());

        BigDecimal result = service.calculateTax(new BigDecimal("200000"), payrollDate);

        assertEquals(new BigDecimal("14000.00"), result);
    }

    @Test
    void calculateMonthlyTax_shouldReturnAnnualTaxDividedByTwelve() {
        LocalDate payrollDate = LocalDate.of(2026, 5, 1);
        List<PayeTaxBand> bounded = List.of(
                band("0", "1200000", "12")
        );

        when(taxBandRepository
                .findByEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualAndStatusOrderByLowerBoundAsc(
                        eq(payrollDate), eq(payrollDate), eq(RecordStatus.ACTIVE)))
                .thenReturn(bounded);
        when(taxBandRepository
                .findByEffectiveFromLessThanEqualAndEffectiveToIsNullAndStatusOrderByLowerBoundAsc(
                        eq(payrollDate), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of());

        BigDecimal result = service.calculateMonthlyTax(new BigDecimal("1200000"), payrollDate);

        assertEquals(new BigDecimal("12000.00"), result);
    }

    @Test
    void calculateTax_legacyMethod_shouldUseEffectiveBandFetch() {
        when(taxBandRepository
                .findByEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualAndStatusOrderByLowerBoundAsc(
                        any(LocalDate.class), any(LocalDate.class), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of(band("0", null, "10")));
        when(taxBandRepository
                .findByEffectiveFromLessThanEqualAndEffectiveToIsNullAndStatusOrderByLowerBoundAsc(
                        any(LocalDate.class), eq(RecordStatus.ACTIVE)))
                .thenReturn(List.of());

        BigDecimal result = service.calculateTax(new BigDecimal("1000"));

        assertEquals(new BigDecimal("100.00"), result);
    }

    private static PayeTaxBand band(String lower, String upper, String rate) {
        PayeTaxBand b = new PayeTaxBand();
        b.setLowerBound(new BigDecimal(lower));
        b.setUpperBound(upper == null ? null : new BigDecimal(upper));
        b.setRate(new BigDecimal(rate));
        return b;
    }
}
