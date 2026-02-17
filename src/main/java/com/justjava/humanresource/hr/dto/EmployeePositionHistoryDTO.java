package com.justjava.humanresource.hr.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class EmployeePositionHistoryDTO {

    Long id;

    Long employeeId;
    String employeeNumber;
    String employeeName;

    Long departmentId;
    String departmentName;

    Long jobStepId;
    String jobStepName;

    Long payGroupId;
    String payGroupName;

    LocalDate effectiveFrom;
    LocalDate effectiveTo;

    boolean current;
}
