package com.justjava.humanresource.kpi.dto;

import com.justjava.humanresource.kpi.enums.AppraisalStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class KpiCompletionStatusDTO {
    private Long departmentId;
    private String departmentName;
    private Long managerId;
    private String managerName;
    private AppraisalStatus status;
    private long count;
    private BigDecimal completionRate;
}
