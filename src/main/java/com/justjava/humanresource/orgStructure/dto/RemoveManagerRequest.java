package com.justjava.humanresource.orgStructure.dto;

import com.justjava.humanresource.orgStructure.enums.ReportingType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class RemoveManagerRequest {

    private Long employeeId;
    private ReportingType reportingType;
    private LocalDate effectiveTo;
}
