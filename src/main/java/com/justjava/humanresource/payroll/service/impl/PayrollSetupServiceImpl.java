package com.justjava.humanresource.payroll.service.impl;


import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.core.exception.InvalidOperationException;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.PayGroupRepository;
import com.justjava.humanresource.payroll.entity.*;
import com.justjava.humanresource.payroll.mapper.EmployeeAllowanceMapper;
import com.justjava.humanresource.payroll.mapper.EmployeeDeductionMapper;
import com.justjava.humanresource.payroll.mapper.PayGroupAllowanceMapper;
import com.justjava.humanresource.payroll.mapper.PayGroupDeductionMapper;
import com.justjava.humanresource.payroll.repositories.*;
import com.justjava.humanresource.payroll.service.PayrollChangeOrchestrator;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.payroll.statutory.entity.PayeTaxBand;
import com.justjava.humanresource.payroll.statutory.entity.PensionScheme;
import com.justjava.humanresource.payroll.statutory.repositories.PayeTaxBandRepository;
import com.justjava.humanresource.payroll.statutory.repositories.PensionSchemeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollSetupServiceImpl implements PayrollSetupService {

    private final PayeTaxBandRepository payeTaxBandRepository;
    private final PensionSchemeRepository pensionSchemeRepository;
    private final AllowanceRepository allowanceRepository;
    private final DeductionRepository deductionRepository;
    private final PayGroupRepository payGroupRepository;
    private final PayGroupAllowanceRepository payGroupAllowanceRepository;
    private final PayGroupDeductionRepository payGroupDeductionRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAllowanceRepository employeeAllowanceRepository;
    private final EmployeeDeductionRepository employeeDeductionRepository;
    private final EmployeeAllowanceMapper employeeAllowanceMapper;
    private final EmployeeDeductionMapper employeeDeductionMapper;
    private final PayGroupAllowanceMapper payGroupAllowanceMapper;
    private final PayGroupDeductionMapper payGroupDeductionMapper;
    private final PayrollChangeOrchestrator  payrollChangeOrchestrator;

    private PayrollPeriodService payrollPeriodService;









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
                .findEffectiveSchemes(LocalDate.now(),RecordStatus.ACTIVE);
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
    @Override
    @Transactional
    public List<PayGroupAllowanceResponse> addAllowancesToPayGroup(
            Long payGroupId,
            List<AllowanceAttachmentRequest> requests) {

        List<PayGroupAllowanceResponse> response = new ArrayList<>();

        for (AllowanceAttachmentRequest request : requests) {

            response.add(payGroupAllowanceMapper.toResponse(
                    addAllowanceToPayGroup(
                            payGroupId,
                            request.getAllowanceId(),
                            request.getOverrideAmount(),
                            request.getEffectiveFrom(),
                            request.getEffectiveTo()
                    )
            ));
        }


        LocalDate affectedDate = determineAffectedPayrollDate(requests);
        if (payrollPeriodService.isPayrollDateInOpenPeriod(affectedDate)) {
            payrollChangeOrchestrator.recalculateForPayGroup(payGroupId, affectedDate);
        }
        return response;
    }

    @Override
    @Transactional
    public List<PayGroupDeductionResponse> addDeductionsToPayGroup(
            Long payGroupId,
            List<DeductionAttachmentRequest> requests) {

        List<PayGroupDeductionResponse> response = new ArrayList<>();

        for (DeductionAttachmentRequest request : requests) {

            response.add(payGroupDeductionMapper.toResponse(
                    addDeductionToPayGroup(
                            payGroupId,
                            request.getDeductionId(),
                            request.getOverrideAmount(),
                            request.getEffectiveFrom(),
                            request.getEffectiveTo()
                    )
            ));
        }
        LocalDate affectedDate = determineAffectedPayrollDate(requests);

        if (payrollPeriodService.isPayrollDateInOpenPeriod(affectedDate)) {
            payrollChangeOrchestrator.recalculateForPayGroup(payGroupId, affectedDate);
        };
        return response;
    }
    @Override
    @Transactional
    public List<EmployeeAllowanceResponse> addAllowancesToEmployee(
            Long employeeId,
            List<AllowanceAttachmentRequest> requests) {

        List<EmployeeAllowanceResponse> responses = new ArrayList<>();

        for (AllowanceAttachmentRequest request : requests) {

            responses.add(employeeAllowanceMapper.toResponse(
                    addAllowanceToEmployee(
                            employeeId,
                            request.getAllowanceId(),
                            request.isOverridden(),
                            request.getOverrideAmount(),
                            request.getEffectiveFrom(),
                            request.getEffectiveTo()
                    )
            ));
        }
        LocalDate affectedDate = determineAffectedPayrollDate(requests);
        if (payrollPeriodService.isPayrollDateInOpenPeriod(affectedDate)) {
            payrollChangeOrchestrator.recalculateForEmployee(employeeId, affectedDate);
        }

        return responses;
    }
    @Override
    @Transactional
    public List<EmployeeDeductionResponse> addDeductionsToEmployee(
            Long employeeId,
            List<DeductionAttachmentRequest> requests) {

        List<EmployeeDeductionResponse> response = new ArrayList<>();

        for (DeductionAttachmentRequest request : requests) {

            response.add(employeeDeductionMapper.toResponse(
                    addDeductionToEmployee(
                            employeeId,
                            request.getDeductionId(),
                            request.isOverridden(),
                            request.getOverrideAmount(),
                            request.getEffectiveFrom(),
                            request.getEffectiveTo()
                    )
            ));
        }
        LocalDate affectedDate = determineAffectedPayrollDate(requests);

        if (payrollPeriodService.isPayrollDateInOpenPeriod(affectedDate)) {
            payrollChangeOrchestrator.recalculateForEmployee(employeeId, affectedDate);
        }

        return response;
    }
    @Override
    @Transactional
    public PayGroupAllowance addAllowanceToPayGroup(
            Long payGroupId,
            Long allowanceId,
            BigDecimal overrideAmount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        PayGroup payGroup = payGroupRepository.findById(payGroupId)
                .orElseThrow();

        Allowance allowance = allowanceRepository.findById(allowanceId)
                .orElseThrow();

        PayGroupAllowance entity = new PayGroupAllowance();
        entity.setPayGroup(payGroup);
        entity.setAllowance(allowance);
        entity.setOverrideAmount(overrideAmount);
        entity.setEffectiveFrom(effectiveFrom);
        entity.setEffectiveTo(effectiveTo);
        entity.setStatus(RecordStatus.ACTIVE);

        return payGroupAllowanceRepository.save(entity);
    }
    @Override
    @Transactional
    public PayGroupDeduction addDeductionToPayGroup(
            Long payGroupId,
            Long deductionId,
            BigDecimal overrideAmount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        PayGroup payGroup = payGroupRepository.findById(payGroupId)
                .orElseThrow();

        Deduction deduction = deductionRepository.findById(deductionId)
                .orElseThrow();

        PayGroupDeduction entity = new PayGroupDeduction();
        entity.setPayGroup(payGroup);
        entity.setDeduction(deduction);
        entity.setOverrideAmount(overrideAmount);
        entity.setEffectiveFrom(effectiveFrom);
        entity.setEffectiveTo(effectiveTo);
        entity.setStatus(RecordStatus.ACTIVE);

        return payGroupDeductionRepository.save(entity);
    }
    @Override
    @Transactional
    public List<PayGroup> getAllPayGroups() {
        return payGroupRepository.findAll();
    }

    @Override
    @Transactional
    public EmployeeAllowance addAllowanceToEmployee(
            Long employeeId,
            Long allowanceId,
            boolean overridden,
            BigDecimal overrideAmount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow();

        Allowance allowance = allowanceRepository.findById(allowanceId)
                .orElseThrow();

        EmployeeAllowance entity = new EmployeeAllowance();
        entity.setEmployee(employee);
        entity.setAllowance(allowance);
        entity.setOverridden(overridden);
        entity.setOverrideAmount(overridden ? overrideAmount : null);
        entity.setEffectiveFrom(effectiveFrom);
        entity.setEffectiveTo(effectiveTo);
        entity.setStatus(RecordStatus.ACTIVE);

        return employeeAllowanceRepository.save(entity);
    }
    @Override
    @Transactional
    public EmployeeDeduction addDeductionToEmployee(
            Long employeeId,
            Long deductionId,
            boolean overridden,
            BigDecimal overrideAmount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow();

        Deduction deduction = deductionRepository.findById(deductionId)
                .orElseThrow();

        EmployeeDeduction entity = new EmployeeDeduction();
        entity.setEmployee(employee);
        entity.setDeduction(deduction);
        entity.setOverridden(overridden);
        entity.setOverrideAmount(overridden ? overrideAmount : null);
        entity.setEffectiveFrom(effectiveFrom);
        entity.setEffectiveTo(effectiveTo);
        entity.setStatus(RecordStatus.ACTIVE);

        return employeeDeductionRepository.save(entity);
    }
    private LocalDate determineAffectedPayrollDate(
            List<? extends Object> requests) {

        return requests.stream()
                .map(r -> {
                    if (r instanceof AllowanceAttachmentRequest a) {
                        return a.getEffectiveFrom();
                    }
                    if (r instanceof DeductionAttachmentRequest d) {
                        return d.getEffectiveFrom();
                    }
                    return null;
                })
                .filter(d -> d != null)
                .min(LocalDate::compareTo)
                .orElseThrow(() ->
                        new InvalidOperationException(
                                "EffectiveFrom date is required for recalculation."
                        ));
    }

}
