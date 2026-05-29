package com.justjava.humanresource.payroll.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class AllowanceGroupReportDTO {


    private String groupName;

    private Long employeeCount;

    private List<AllowanceReportLineDTO> allowances = new ArrayList<>();

    private BigDecimal groupTotal = BigDecimal.ZERO;
}