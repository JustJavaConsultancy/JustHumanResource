package com.justjava.humanresource.kpi.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class KpiAppraisalLineCommand {
    private BigDecimal selfScore;
    private Long selfRubricBandId;
    private String selfComment;
    private BigDecimal managerScore;
    private Long managerRubricBandId;
    private String managerComment;
    private String evidenceUrl;
}
