package com.justjava.humanresource.payroll.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class PayGroupEmployeeViewDTO {

    private Long employeeId;
    private String employeeNumber;
    private String fullName;
    private LocalDate effectiveFrom;
}
