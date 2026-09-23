package com.justjava.humanresource.kpi.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class KpiScorecardImportRowDTO {
    private Integer rowNumber;
    private String perspective;
    private String objective;
    private String indicator;
    private String description;
    private String timeline;
    private String measure;
    private BigDecimal weight;
}
