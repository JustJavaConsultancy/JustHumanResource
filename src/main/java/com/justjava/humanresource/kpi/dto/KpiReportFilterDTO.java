package com.justjava.humanresource.kpi.dto;

import com.justjava.humanresource.kpi.enums.AppraisalStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KpiReportFilterDTO {
    private Long cycleId;
    private Long departmentId;
    private Long jobStepId;
    private Long employeeId;
    private Long templateId;
    private Integer templateVersion;
    private Long perspectiveId;
    private Long rubricBandId;
    private AppraisalStatus completionStatus;
}
