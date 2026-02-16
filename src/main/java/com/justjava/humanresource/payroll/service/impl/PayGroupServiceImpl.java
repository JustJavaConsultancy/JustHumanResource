package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.dto.*;
import com.justjava.humanresource.hr.repository.*;
import com.justjava.humanresource.payroll.entity.PayGroupAllowanceViewDTO;
import com.justjava.humanresource.payroll.entity.PayGroupDeductionViewDTO;
import com.justjava.humanresource.payroll.entity.PayGroupEmployeeViewDTO;
import com.justjava.humanresource.payroll.repositories.*;
import com.justjava.humanresource.payroll.service.PayGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayGroupServiceImpl implements PayGroupService {

    private final PayGroupRepository payGroupRepository;
    private final PayGroupAllowanceRepository payGroupAllowanceRepository;
    private final PayGroupDeductionRepository payGroupDeductionRepository;
    private final EmployeePositionHistoryRepository positionHistoryRepository;

    /* ============================================================
       ALLOWANCES
       ============================================================ */

    @Override
    public List<PayGroupAllowanceViewDTO> getAllowances(
            Long payGroupId,
            LocalDate date) {

        payGroupRepository.findById(payGroupId)
                .orElseThrow();

        return payGroupAllowanceRepository
                .findActiveAllowances(
                        payGroupId,
                        date,
                        RecordStatus.ACTIVE
                )
                .stream()
                .map(entity ->
                        PayGroupAllowanceViewDTO.builder()
                                .allowanceId(entity.getAllowance().getId())
                                .allowanceCode(entity.getAllowance().getCode())
                                .allowanceName(entity.getAllowance().getName())
                                .overrideAmount(entity.getOverrideAmount())
                                .effectiveFrom(entity.getEffectiveFrom())
                                .effectiveTo(entity.getEffectiveTo())
                                .build()
                )
                .toList();
    }

    /* ============================================================
       DEDUCTIONS
       ============================================================ */

    @Override
    public List<PayGroupDeductionViewDTO> getDeductions(
            Long payGroupId,
            LocalDate date) {

        payGroupRepository.findById(payGroupId)
                .orElseThrow();

        return payGroupDeductionRepository
                .findActiveDeductions(
                        payGroupId,
                        date,
                        RecordStatus.ACTIVE
                )
                .stream()
                .map(entity ->
                        PayGroupDeductionViewDTO.builder()
                                .deductionId(entity.getDeduction().getId())
                                .deductionCode(entity.getDeduction().getCode())
                                .deductionName(entity.getDeduction().getName())
                                .overrideAmount(entity.getOverrideAmount())
                                .effectiveFrom(entity.getEffectiveFrom())
                                .effectiveTo(entity.getEffectiveTo())
                                .build()
                )
                .toList();
    }

    /* ============================================================
       EMPLOYEES IN PAYGROUP (DATE-AWARE)
       ============================================================ */

    @Override
    public List<PayGroupEmployeeViewDTO> getEmployees(
            Long payGroupId,
            LocalDate date) {

        return positionHistoryRepository
                .findEmployeesByPayGroupAndDate(
                        payGroupId,
                        date,
                        RecordStatus.ACTIVE
                )
                .stream()
                .map(position ->
                        PayGroupEmployeeViewDTO.builder()
                                .employeeId(position.getEmployee().getId())
                                .employeeNumber(position.getEmployee().getEmployeeNumber())
                                .fullName(
                                        position.getEmployee().getFirstName()
                                                + " "
                                                + position.getEmployee().getLastName()
                                )
                                .effectiveFrom(position.getEffectiveFrom())
                                .build()
                )
                .toList();
    }
}
