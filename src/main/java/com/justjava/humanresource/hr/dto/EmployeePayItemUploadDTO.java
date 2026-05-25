package com.justjava.humanresource.hr.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class EmployeePayItemUploadDTO {

    private int rowNumber;
    private String employeeNumber;
    private String employeeEmail;
    private String itemType;
    private String itemCode;
    private String overridden;
    private BigDecimal overrideAmount;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
