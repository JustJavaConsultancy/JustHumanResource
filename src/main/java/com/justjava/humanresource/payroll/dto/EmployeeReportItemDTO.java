package com.justjava.humanresource.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
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

    private String bankAccountNumber;
    private String bankName;
}