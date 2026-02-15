package com.justjava.humanresource.payroll.service.impl;


import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.core.exception.InvalidOperationException;
import com.justjava.humanresource.payroll.entity.Allowance;
import com.justjava.humanresource.payroll.entity.Deduction;
import com.justjava.humanresource.payroll.repositories.AllowanceRepository;
import com.justjava.humanresource.payroll.repositories.DeductionRepository;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.payroll.statutory.entity.PayeTaxBand;
import com.justjava.humanresource.payroll.statutory.entity.PensionScheme;
import com.justjava.humanresource.payroll.statutory.repositories.PayeTaxBandRepository;
import com.justjava.humanresource.payroll.statutory.repositories.PensionSchemeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollSetupServiceImpl implements PayrollSetupService {

    private final PayeTaxBandRepository payeTaxBandRepository;
    private final PensionSchemeRepository pensionSchemeRepository;
    private final AllowanceRepository allowanceRepository;
    private final DeductionRepository deductionRepository;

    /* =========================
     * PAYE
     * ========================= */

    @Override
    @Transactional
    public PayeTaxBand createPayeTaxBand(PayeTaxBand band) {
        band.setStatus(RecordStatus.ACTIVE);
        return payeTaxBandRepository.save(band);
    }

    @Override
    public List<PayeTaxBand> getActivePayeBands(LocalDate date) {
        return payeTaxBandRepository.findByEffectiveFromLessThanEqualAndEffectiveToIsNullAndStatusOrderByLowerBoundAsc(
                        date, RecordStatus.ACTIVE
                );
    }

    @Override
    public void validatePayeConfiguration(LocalDate date) {
        List<PayeTaxBand> bands = getActivePayeBands(date);

        if (bands.isEmpty()) {
            throw new InvalidOperationException("No active PAYE bands configured.");
        }

        bands.sort(Comparator.comparing(PayeTaxBand::getLowerBound));

        for (int i = 0; i < bands.size() - 1; i++) {
            if (bands.get(i).getUpperBound() != null &&
                    bands.get(i).getUpperBound()
                            .compareTo(bands.get(i + 1).getLowerBound()) > 0) {
                throw new InvalidOperationException("Overlapping PAYE bands detected.");
            }
        }
    }

    /* =========================
     * PENSION
     * ========================= */

    @Override
    @Transactional
    public PensionScheme createPensionScheme(PensionScheme scheme) {
        scheme.setStatus(RecordStatus.ACTIVE);
        return pensionSchemeRepository.save(scheme);
    }

    @Override
    public List<PensionScheme> getActivePensionSchemes() {
        return pensionSchemeRepository
                .findByEffectiveFromLessThanEqualAndEffectiveToIsNullAndStatus(LocalDate.now(),RecordStatus.ACTIVE);
    }

    /* =========================
     * ALLOWANCES
     * ========================= */

    @Override
    @Transactional
    public Allowance createAllowance(Allowance allowance) {
        allowance.setStatus(RecordStatus.ACTIVE);
        return allowanceRepository.save(allowance);
    }

    @Override
    public List<Allowance> getActiveAllowances() {
        return allowanceRepository.findByStatus(RecordStatus.ACTIVE);
    }

    /* =========================
     * DEDUCTIONS
     * ========================= */

    @Override
    @Transactional
    public Deduction createDeduction(Deduction deduction) {
        deduction.setStatus(RecordStatus.ACTIVE);
        return deductionRepository.save(deduction);
    }

    @Override
    public List<Deduction> getActiveDeductions() {
        return deductionRepository.findByStatus(RecordStatus.ACTIVE);
    }

    /* =========================
     * SYSTEM READINESS
     * ========================= */

    @Override
    public void validatePayrollSystemReadiness(LocalDate payrollDate) {
        validatePayeConfiguration(payrollDate);

        if (getActivePensionSchemes().isEmpty()) {
            throw new InvalidOperationException("No active Pension Scheme configured.");
        }
    }
}
