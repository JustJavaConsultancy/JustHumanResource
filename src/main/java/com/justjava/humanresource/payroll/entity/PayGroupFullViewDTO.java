package com.justjava.humanresource.payroll.entity;

import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.payroll.entity.PayGroupAllowanceViewDTO;
import com.justjava.humanresource.payroll.entity.PayGroupDeductionViewDTO;
import com.justjava.humanresource.payroll.entity.PayGroupEmployeeViewDTO;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PayGroupFullViewDTO {

    private PayGroup payGroup;

    private List<PayGroupAllowanceViewDTO> allowances;
    private List<PayGroupDeductionViewDTO> deductions;
    private List<PayGroupEmployeeViewDTO> employees;
}

