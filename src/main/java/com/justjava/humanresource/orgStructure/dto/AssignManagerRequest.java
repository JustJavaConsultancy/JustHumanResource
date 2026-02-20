package com.justjava.humanresource.orgStructure.dto;

import com.justjava.humanresource.orgStructure.enums.ReportingType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class AssignManagerRequest {

    private Long employeeId;
    private Long managerId;
    private ReportingType reportingType;
    private LocalDate effectiveFrom;
}
