package com.justjava.humanresource.kpi.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KpiScorecardImportResultDTO {
    private Long templateId;
    private String templateName;
    private int definitionsCreated;
    private int definitionsReused;
    private int templateItemsCreated;
}
