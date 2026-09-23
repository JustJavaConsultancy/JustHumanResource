package com.justjava.humanresource.kpi.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KpiScorecardImportConfirmCommand {
    private String templateName;
    private String roleName;
    private List<KpiScorecardImportRowDTO> rows = new ArrayList<>();
}
