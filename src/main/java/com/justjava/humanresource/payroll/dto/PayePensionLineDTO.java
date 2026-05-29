package com.justjava.humanresource.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayePensionLineDTO {
    private String employeeName;
    private Long employeeId;
    private BigDecimal paye;
    private BigDecimal employeePension;
    private BigDecimal employerPension;
    private String tinNumber;
    private String rsaPin;
    private String pfa;
}
