package com.justjava.humanresource.payroll.service.impl;


import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.core.exception.InvalidOperationException;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeePositionHistory;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.PayGroupRepository;
import com.justjava.humanresource.payroll.dto.FutureEmployeeAllowanceDTO;
import com.justjava.humanresource.payroll.entity.*;
import com.justjava.humanresource.payroll.mapper.EmployeeAllowanceMapper;
import com.justjava.humanresource.payroll.mapper.EmployeeDeductionMapper;
import com.justjava.humanresource.payroll.mapper.PayGroupAllowanceMapper;
import com.justjava.humanresource.payroll.mapper.PayGroupDeductionMapper;
import com.justjava.humanresource.payroll.repositories.*;
import com.justjava.humanresource.payroll.service.EmployeePositionHistoryService;
import com.justjava.humanresource.payroll.service.PayrollChangeOrchestrator;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import com.justjava.humanresource.payroll.service.PayrollSetupService;
import com.justjava.humanresource.payroll.statutory.entity.PayeTaxBand;
import com.justjava.humanresource.payroll.statutory.entity.PensionScheme;
import com.justjava.humanresource.payroll.statutory.repositories.PayeTaxBandRepository;
import com.justjava.humanresource.payroll.statutory.repositories.PensionSchemeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

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
    private final EmployeePositionHistoryService employeePositionHistoryService;

    private final PayrollPeriodService payrollPeriodService;
    private final TaxReliefRepository taxReliefRepository;
    private final PayGroupTaxReliefRepository payGroupTaxReliefRepository;
    private final EmployeeTaxReliefRepository employeeTaxReliefRepository;









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
    @Transactional
    public PayeTaxBand updateTax(Long id, PayeTaxBand incoming) {
        // 1. Fetch existing
        PayeTaxBand existing = payeTaxBandRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PayeTaxBand not found"));

        // 3. Determine new values (use existing if null in incoming)
        BigDecimal newLower = incoming.getLowerBound() != null ? incoming.getLowerBound() : existing.getLowerBound();
        BigDecimal newUpper = incoming.getUpperBound() != null ? incoming.getUpperBound() : existing.getUpperBound();
        LocalDate newEffectiveFrom = incoming.getEffectiveFrom() != null ? incoming.getEffectiveFrom() : existing.getEffectiveFrom();
        LocalDate newEffectiveTo = incoming.getEffectiveTo() != null ? incoming.getEffectiveTo() : existing.getEffectiveTo();

        // 7. Apply updates (copy only mutable fields)
        existing.setLowerBound(newLower);
        existing.setUpperBound(newUpper);
        if (incoming.getRate() != null) existing.setRate(incoming.getRate());
        existing.setEffectiveFrom(newEffectiveFrom);
        existing.setEffectiveTo(newEffectiveTo);
        if (incoming.getStatus() != null) existing.setStatus(incoming.getStatus());
        if (incoming.getRegimeCode() != null) existing.setRegimeCode(incoming.getRegimeCode());

        // 8. Save and return
        return payeTaxBandRepository.save(existing);
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
    @Transactional
    public PensionScheme update(Long id, PensionScheme incoming) {
        PensionScheme existing = pensionSchemeRepository.findById(id).orElseThrow(
                () -> new InvalidOperationException("Pension Scheme not found.")
        );
        // Copy allowed fields manually (or use something like BeanUtils.copyProperties ignoring nulls)
        existing.setName(incoming.getName());
        existing.setEmployeeRate(incoming.getEmployeeRate());
        existing.setEmployerRate(incoming.getEmployerRate());
        if (incoming.getPensionableOnBasicOnly() != null) {
            existing.setPensionableOnBasicOnly(incoming.getPensionableOnBasicOnly());
        }
        if (incoming.getPensionableCap() != null) { // null is a valid value for "no cap"
            existing.setPensionableCap(incoming.getPensionableCap());
        }
        if (incoming.getStatus() != null) {
            existing.setStatus(incoming.getStatus());
        }
        existing.setEffectiveFrom(incoming.getEffectiveFrom());
        existing.setEffectiveTo(incoming.getEffectiveTo());

        // ... but careful: do NOT copy id, code, version, etc.
        return pensionSchemeRepository.save(existing);
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
//    @Override
//    public List<Allowance> findAllowanceByID(){
//        return allowanceRepository.findByStatus(RecordStatus.ACTIVE);
//    }

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

        if (requests == null || requests.isEmpty()) return response;

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
        if (payrollPeriodService.isPayrollDateInOpenPeriod(1L,affectedDate)) {
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

        if (requests == null || requests.isEmpty()) return response;

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

        if (payrollPeriodService.isPayrollDateInOpenPeriod(1L,affectedDate)) {
            payrollChangeOrchestrator.recalculateForPayGroup(payGroupId, affectedDate);
        };
        return response;
    }
    /* ============================================================
       SOFT-DELETE: deactivate items removed from pay group
       ============================================================ */

    @Override
    @Transactional
    public void deactivateRemovedAllowancesFromPayGroup(Long payGroupId, List<Long> activeAllowanceIds) {
        List<PayGroupAllowance> existing = payGroupAllowanceRepository.findByPayGroupId(payGroupId);
        boolean anyDeactivated = false;
        for (PayGroupAllowance a : existing) {
            if (a.getStatus() == RecordStatus.ACTIVE
                    && !activeAllowanceIds.contains(a.getAllowance().getId())) {
                a.setStatus(RecordStatus.INACTIVE);
                payGroupAllowanceRepository.save(a);
                anyDeactivated = true;
            }
        }
        if (anyDeactivated
                && payrollPeriodService.isPayrollDateInOpenPeriod(1L, LocalDate.now())) {
            payrollChangeOrchestrator.recalculateForPayGroup(payGroupId, LocalDate.now());
        }
    }

    @Override
    @Transactional
    public void deactivateRemovedDeductionsFromPayGroup(Long payGroupId, List<Long> activeDeductionIds) {
        List<PayGroupDeduction> existing = payGroupDeductionRepository.findByPayGroupId(payGroupId);
        boolean anyDeactivated = false;
        for (PayGroupDeduction d : existing) {
            if (d.getStatus() == RecordStatus.ACTIVE
                    && !activeDeductionIds.contains(d.getDeduction().getId())) {
                d.setStatus(RecordStatus.INACTIVE);
                payGroupDeductionRepository.save(d);
                anyDeactivated = true;
            }
        }
        if (anyDeactivated
                && payrollPeriodService.isPayrollDateInOpenPeriod(1L, LocalDate.now())) {
            payrollChangeOrchestrator.recalculateForPayGroup(payGroupId, LocalDate.now());
        }
    }

    @Override
    @Transactional
    public void deactivateRemovedTaxReliefsFromPayGroup(Long payGroupId, List<Long> activeTaxReliefIds) {
        List<PayGroupTaxRelief> existing = payGroupTaxReliefRepository.findByPayGroupId(payGroupId);
        boolean anyDeactivated = false;
        for (PayGroupTaxRelief t : existing) {
            if (t.getStatus() == RecordStatus.ACTIVE
                    && !activeTaxReliefIds.contains(t.getTaxRelief().getId())) {
                t.setStatus(RecordStatus.INACTIVE);
                payGroupTaxReliefRepository.save(t);
                anyDeactivated = true;
            }
        }
        if (anyDeactivated
                && payrollPeriodService.isPayrollDateInOpenPeriod(1L, LocalDate.now())) {
            payrollChangeOrchestrator.recalculateForPayGroup(payGroupId, LocalDate.now());
        }
    }

    @Override
    @Transactional
    public void deactivateRemovedAllowancesFromEmployee(Long employeeId, List<Long> activeAllowanceIds) {
        List<EmployeeAllowance> existing = employeeAllowanceRepository.findByEmployeeId(employeeId);
        boolean anyDeactivated = false;
        for (EmployeeAllowance a : existing) {
            if (a.getStatus() == RecordStatus.ACTIVE
                    && !activeAllowanceIds.contains(a.getAllowance().getId())) {
                a.setStatus(RecordStatus.INACTIVE);
                employeeAllowanceRepository.save(a);
                anyDeactivated = true;
            }
        }
        if (anyDeactivated
                && payrollPeriodService.isPayrollDateInOpenPeriod(1L, LocalDate.now())) {
            payrollChangeOrchestrator.recalculateForEmployee(employeeId, LocalDate.now());
        }
    }

    @Override
    @Transactional
    public void deactivateRemovedDeductionsFromEmployee(Long employeeId, List<Long> activeDeductionIds) {
        List<EmployeeDeduction> existing = employeeDeductionRepository.findByEmployeeId(employeeId);
        boolean anyDeactivated = false;
        for (EmployeeDeduction d : existing) {
            if (d.getStatus() == RecordStatus.ACTIVE
                    && !activeDeductionIds.contains(d.getDeduction().getId())) {
                d.setStatus(RecordStatus.INACTIVE);
                employeeDeductionRepository.save(d);
                anyDeactivated = true;
            }
        }
        if (anyDeactivated
                && payrollPeriodService.isPayrollDateInOpenPeriod(1L, LocalDate.now())) {
            payrollChangeOrchestrator.recalculateForEmployee(employeeId, LocalDate.now());
        }
    }

    @Override
    @Transactional
    public void deactivateRemovedTaxReliefsFromEmployee(Long employeeId, List<Long> activeTaxReliefIds) {
        List<EmployeeTaxRelief> existing = employeeTaxReliefRepository.findByEmployeeId(employeeId);
        boolean anyDeactivated = false;
        for (EmployeeTaxRelief t : existing) {
            if (t.getStatus() == RecordStatus.ACTIVE
                    && !activeTaxReliefIds.contains(t.getTaxRelief().getId())) {
                t.setStatus(RecordStatus.INACTIVE);
                employeeTaxReliefRepository.save(t);
                anyDeactivated = true;
            }
        }
        if (anyDeactivated
                && payrollPeriodService.isPayrollDateInOpenPeriod(1L, LocalDate.now())) {
            payrollChangeOrchestrator.recalculateForEmployee(employeeId, LocalDate.now());
        }
    }

    @Override
    @Transactional
    public List<EmployeeAllowanceResponse> addAllowancesToEmployee(
            Long employeeId,
            List<AllowanceAttachmentRequest> requests) {

        if (requests == null || requests.isEmpty()) return List.of();

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
        if (payrollPeriodService.isPayrollDateInOpenPeriod(1L,affectedDate)) {
            payrollChangeOrchestrator.recalculateForEmployee(employeeId, affectedDate);
        }

        return responses;
    }
    @Override
    @Transactional
    public List<EmployeeDeductionResponse> addDeductionsToEmployee(
            Long employeeId,
            List<DeductionAttachmentRequest> requests) {

        if (requests == null || requests.isEmpty()) return List.of();

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

        System.out.println(" affectedDate in addDeductionsToEmployee ==="+affectedDate);

        if (payrollPeriodService.isPayrollDateInOpenPeriod(1L,affectedDate)) {
            payrollChangeOrchestrator.recalculateForEmployee(employeeId, affectedDate);
        }

        return response;
    }
    @Override
    public List<EmployeeAllowanceResponse> getAllowancesForEmployee(Long employeeId) {
        List<EmployeeAllowance> allowances = employeeAllowanceRepository
                .findActiveAllowances(employeeId, LocalDate.now(), RecordStatus.ACTIVE);
        return employeeAllowanceMapper.toResponseList(allowances);
    }
    @Override
    public List<EmployeeDeductionResponse> getDeductionsForEmployee(Long employeeId){
        List<EmployeeDeduction> deductions = employeeDeductionRepository
                .findActiveDeductions(employeeId, LocalDate.now(), RecordStatus.ACTIVE);
        return employeeDeductionMapper.toResponseList(deductions);
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

        // Upsert: update existing record if same (payGroup, allowance, effectiveFrom) already exists
        PayGroupAllowance entity = payGroupAllowanceRepository
                .findByPayGroupIdAndAllowanceIdAndEffectiveFrom(payGroupId, allowanceId, effectiveFrom)
                .orElse(new PayGroupAllowance());

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

        // Upsert: update existing record if same (payGroup, deduction, effectiveFrom) already exists
        PayGroupDeduction entity = payGroupDeductionRepository
                .findByPayGroupIdAndDeductionIdAndEffectiveFrom(payGroupId, deductionId, effectiveFrom)
                .orElse(new PayGroupDeduction());

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

        // Upsert: update existing record if same (employee, allowance, effectiveFrom) already exists
        EmployeeAllowance entity = employeeAllowanceRepository
                .findByEmployeeIdAndAllowanceIdAndEffectiveFrom(employeeId, allowanceId, effectiveFrom)
                .orElse(new EmployeeAllowance());

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

        // Upsert: update existing record if same (employee, deduction, effectiveFrom) already exists
        EmployeeDeduction entity = employeeDeductionRepository
                .findByEmployeeIdAndDeductionIdAndEffectiveFrom(employeeId, deductionId, effectiveFrom)
                .orElse(new EmployeeDeduction());

        entity.setEmployee(employee);
        entity.setDeduction(deduction);
        entity.setOverridden(overridden);
        entity.setOverrideAmount(overridden ? overrideAmount : null);
        entity.setEffectiveFrom(effectiveFrom);
        entity.setEffectiveTo(effectiveTo);
        entity.setStatus(RecordStatus.ACTIVE);

        return employeeDeductionRepository.save(entity);
    }
    @Override
    public TaxRelief createTaxRelief(TaxRelief relief) {
        relief.setActive(true);
        return taxReliefRepository.save(relief);
    }
    @Override
    public List<TaxRelief> getActiveTaxReliefs() {
        return taxReliefRepository.findByActiveTrue();
    }
    @Override
    public PayGroupTaxRelief addTaxReliefToPayGroup(
            Long payGroupId,
            Long taxReliefId,
            BigDecimal overrideAmount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        PayGroup group = payGroupRepository.findById(payGroupId)
                .orElseThrow(() -> new IllegalStateException("PayGroup not found"));

        TaxRelief relief = taxReliefRepository.findById(taxReliefId)
                .orElseThrow(() -> new IllegalStateException("TaxRelief not found"));

        // Upsert: update existing record if same (payGroup, taxRelief) already exists
        // NOTE: unique constraint is (paygroup_id, tax_relief_id) only — no effectiveFrom
        PayGroupTaxRelief mapping = payGroupTaxReliefRepository
                .findByPayGroupIdAndTaxReliefId(payGroupId, taxReliefId)
                .orElse(new PayGroupTaxRelief());

        mapping.setPayGroup(group);
        mapping.setTaxRelief(relief);
        mapping.setOverrideAmount(overrideAmount);
        mapping.setEffectiveFrom(effectiveFrom);
        mapping.setEffectiveTo(effectiveTo);
        mapping.setStatus(RecordStatus.ACTIVE); // reactivates if previously soft-deleted

        return payGroupTaxReliefRepository.save(mapping);
    }
    @Override
    @Transactional
    public List<PayGroupTaxReliefResponse> addTaxReliefsToPayGroup(
            Long payGroupId,
            List<TaxReliefAttachmentRequest> requests) {

        if (requests == null || requests.isEmpty()) return List.of();

        List<PayGroupTaxReliefResponse> responses = requests.stream()
                .map(req -> {
                    PayGroupTaxRelief mapping =
                            addTaxReliefToPayGroup(
                                    payGroupId,
                                    req.getTaxReliefId(),
                                    req.getOverrideAmount(),
                                    req.getEffectiveFrom(),
                                    req.getEffectiveTo()
                            );

                    return PayGroupTaxReliefResponse.builder()
                            .id(mapping.getId())
                            .payGroupId(payGroupId)
                            .reliefCode(mapping.getTaxRelief().getCode())
                            .reliefName(mapping.getTaxRelief().getName())
                            .overrideAmount(mapping.getOverrideAmount())
                            .effectiveFrom(mapping.getEffectiveFrom())
                            .effectiveTo(mapping.getEffectiveTo())
                            .build();
                })
                .toList();

        LocalDate affectedDate = determineAffectedPayrollDate(requests);
        if (payrollPeriodService.isPayrollDateInOpenPeriod(1L, affectedDate)) {
            payrollChangeOrchestrator.recalculateForPayGroup(payGroupId, affectedDate);
        }

        return responses;
    }
    @Override
    public EmployeeTaxRelief addTaxReliefToEmployee(
            Long employeeId,
            Long taxReliefId,
            boolean overridden,
            BigDecimal overrideAmount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalStateException("Employee not found"));

        TaxRelief relief = taxReliefRepository.findById(taxReliefId)
                .orElseThrow(() -> new IllegalStateException("TaxRelief not found"));

        // Upsert: update existing record if same (employee, taxRelief, effectiveFrom) already exists
        EmployeeTaxRelief mapping = employeeTaxReliefRepository
                .findByEmployeeIdAndTaxReliefIdAndEffectiveFrom(employeeId, taxReliefId, effectiveFrom)
                .orElse(new EmployeeTaxRelief());

        mapping.setEmployeeId(employeeId);
        mapping.setTaxRelief(relief);
        mapping.setOverridden(overridden);
        mapping.setOverrideAmount(overrideAmount);
        mapping.setEffectiveFrom(effectiveFrom);
        mapping.setEffectiveTo(effectiveTo);
        mapping.setStatus(RecordStatus.ACTIVE); // reactivates if previously soft-deleted

        return employeeTaxReliefRepository.save(mapping);
    }

    @Override
    @Transactional
    public List<EmployeeTaxReliefResponse> addTaxReliefsToEmployee(
            Long employeeId,
            List<TaxReliefAttachmentRequest> requests) {

        if (requests == null || requests.isEmpty()) return List.of();

        List<EmployeeTaxReliefResponse> responses = requests.stream()
                .map(req -> {
                    EmployeeTaxRelief mapping =
                            addTaxReliefToEmployee(
                                    employeeId,
                                    req.getTaxReliefId(),
                                    req.isOverridden(),
                                    req.getOverrideAmount(),
                                    req.getEffectiveFrom(),
                                    req.getEffectiveTo()
                            );

                    return EmployeeTaxReliefResponse.builder()
                            .id(mapping.getId())
                            .employeeId(employeeId)
                            .taxReliefId(mapping.getTaxRelief().getId())
                            .reliefCode(mapping.getTaxRelief().getCode())
                            .reliefName(mapping.getTaxRelief().getName())
                            .overridden(mapping.isOverridden())
                            .overrideAmount(mapping.getOverrideAmount())
                            .effectiveFrom(mapping.getEffectiveFrom())
                            .effectiveTo(mapping.getEffectiveTo())
                            .build();
                })
                .toList();

        LocalDate affectedDate = determineAffectedPayrollDate(requests);
        if (payrollPeriodService.isPayrollDateInOpenPeriod(1L, affectedDate)) {
            payrollChangeOrchestrator.recalculateForEmployee(employeeId, affectedDate);
        }

        return responses;
    }
    @Override
    public List<EmployeeTaxReliefResponse> getTaxReliefsForEmployee(Long employeeId) {

        return employeeTaxReliefRepository
                .findActiveReliefs(employeeId, LocalDate.now(), RecordStatus.ACTIVE)
                .stream()
                .map(mapping -> EmployeeTaxReliefResponse.builder()
                        .id(mapping.getId())
                        .employeeId(employeeId)
                        .taxReliefId(mapping.getTaxRelief().getId())
                        .reliefCode(mapping.getTaxRelief().getCode())
                        .reliefName(mapping.getTaxRelief().getName())
                        .overridden(mapping.isOverridden())
                        .overrideAmount(mapping.getOverrideAmount())
                        .effectiveFrom(mapping.getEffectiveFrom())
                        .effectiveTo(mapping.getEffectiveTo())
                        .build()
                )
                .toList();
    }
    @Override
    public List<FutureEmployeeAllowanceDTO> getFutureAllowancesForEmployee(Long employeeId) {

        LocalDate today = LocalDate.now();

        // ---------------------------------------------------------
        // 1. Get Employee + Position
        // ---------------------------------------------------------

        EmployeePositionHistory position =
                employeePositionHistoryService.getCurrentPosition(employeeId);

        Long payGroupId = position.getPayGroup().getId();

        // ---------------------------------------------------------
        // 2. Fetch BOTH SOURCES
        // ---------------------------------------------------------

        List<EmployeeAllowance> employeeAllowances =
                employeeAllowanceRepository.findFutureAllowancesByEmployee(
                        employeeId, today
                );

        List<PayGroupAllowance> payGroupAllowances =
                employeeAllowanceRepository.findFutureAllowancesByPayGroup(
                        payGroupId, today
                );

        // ---------------------------------------------------------
        // 3. MERGE (Employee overrides PayGroup)
        // ---------------------------------------------------------

        Map<String, FutureEmployeeAllowanceDTO> map = new LinkedHashMap<>();

        // 🔹 3A: Load PayGroup first
        for (PayGroupAllowance pga : payGroupAllowances) {

            Allowance a = pga.getAllowance();

            BigDecimal amount = pga.getOverrideAmount() != null
                    ? pga.getOverrideAmount()
                    : a.getAmount();

            map.put(a.getCode(),
                    new FutureEmployeeAllowanceDTO(
                            null, // employeeId optional here
                            null,
                            null,
                            a.getCode(),
                            a.getName(),
                            amount,
                            false,
                            pga.getEffectiveFrom(),
                            pga.getEffectiveTo()
                    )
            );
        }

        // 🔹 3B: Override with Employee allowances
        for (EmployeeAllowance ea : employeeAllowances) {

            Allowance a = ea.getAllowance();

            BigDecimal amount = ea.getOverrideAmount() != null
                    ? ea.getOverrideAmount()
                    : a.getAmount();

            map.put(a.getCode(),
                    new FutureEmployeeAllowanceDTO(
                            ea.getEmployee().getId(),
                            ea.getEmployee().getFirstName(),
                            ea.getEmployee().getLastName(),
                            a.getCode(),
                            a.getName(),
                            amount,
                            ea.isOverridden(),
                            ea.getEffectiveFrom(),
                            ea.getEffectiveTo()
                    )
            );
        }
        return new ArrayList<>(map.values());
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
                    if (r instanceof TaxReliefAttachmentRequest t) {
                        return t.getEffectiveFrom();
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