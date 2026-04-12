package com.justjava.humanresource.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class EmployeeReportItemDTO {

    private Long employeeId;
    private String firstName;
    private String secondName;

    private BigDecimal gross;
    private BigDecimal net;
    private BigDecimal paye;
    private BigDecimal pension;

    private String groupName;
}