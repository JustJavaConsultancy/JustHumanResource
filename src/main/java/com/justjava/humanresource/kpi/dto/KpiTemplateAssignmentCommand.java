package com.justjava.humanresource.kpi.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class KpiTemplateAssignmentCommand {
    private Long templateId;
    private Long employeeId;
    private Long jobStepId;
    private Long departmentId;
    private LocalDate validFrom;
    private LocalDate validTo;
    private boolean replaceExisting = true;
}
