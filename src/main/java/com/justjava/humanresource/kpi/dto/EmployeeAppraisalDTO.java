package com.justjava.humanresource.kpi.dto;

import com.justjava.humanresource.kpi.enums.AppraisalOutcome;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class EmployeeAppraisalDTO {

    private Long appraisalId;

    private Long employeeId;
    private String employeeName;

    private Long cycleId;
    private String cycleName;

    private BigDecimal kpiScore;
    private BigDecimal managerScore;
    private BigDecimal finalScore;
    private BigDecimal selfScore;
    private String selfComment;
    private String managerComment;

    private AppraisalOutcome outcome;

    private LocalDateTime completedAt;
}
