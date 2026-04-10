package com.justjava.humanresource.hr.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EmployeeUploadDTO {

    private String firstName;
    private String secondName;
    private BigDecimal gross;
}