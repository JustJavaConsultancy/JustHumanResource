package com.justjava.humanresource.kpi.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class KpiScorecardImportPreviewDTO {
    private String templateName;
    private String roleName;
    private BigDecimal totalWeight;
    private List<KpiScorecardImportRowDTO> rows;
    private List<String> warnings;
    private List<String> errors;
}
