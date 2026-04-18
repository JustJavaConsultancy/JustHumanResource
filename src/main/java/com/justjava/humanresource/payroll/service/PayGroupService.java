package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.hr.dto.*;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.payroll.entity.PayGroupAllowanceViewDTO;
import com.justjava.humanresource.payroll.entity.PayGroupDeductionViewDTO;
import com.justjava.humanresource.payroll.entity.PayGroupEmployeeViewDTO;
import com.justjava.humanresource.payroll.entity.PayGroupTaxReliefViewDTO;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

public interface PayGroupService {


    List<PayGroupAllowanceViewDTO> getAllowances(
            Long payGroupId,
            LocalDate date
    );

    List<PayGroupDeductionViewDTO> getDeductions(
            Long payGroupId,
            LocalDate date
    );

    List<PayGroupEmployeeViewDTO> getEmployees(
            Long payGroupId,
            LocalDate date
    );

    List<PayGroupTaxReliefViewDTO> getTaxReliefs(
            Long payGroupId,
            LocalDate date
    );

    /* ============================================================
       ALL ASSIGNED ITEMS (INCLUDING FUTURE EFFECTIVE DATES)
       For UI display purposes
       ============================================================ */

    List<PayGroupAllowanceViewDTO> getAllAssignedAllowances(
            Long payGroupId,
            LocalDate date
    );

    List<PayGroupDeductionViewDTO> getAllAssignedDeductions(
            Long payGroupId,
            LocalDate date
    );

    List<PayGroupTaxReliefViewDTO> getAllAssignedTaxReliefs(
            Long payGroupId,
            LocalDate date
    );
}

