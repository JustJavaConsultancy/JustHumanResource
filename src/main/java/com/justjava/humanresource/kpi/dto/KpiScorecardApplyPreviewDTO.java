package com.justjava.humanresource.kpi.dto;

import com.justjava.humanresource.hr.dto.KpiAssignmentResponseDTO;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class KpiScorecardApplyPreviewDTO {
    private Long templateId;
    private String templateName;
    private String scope;
    private Long ownerId;
    private BigDecimal effectiveWeight;
    private List<KpiAssignmentResponseDTO> currentAssignments;
    private List<KpiAssignmentResponseDTO> templateAssignments;
    private List<KpiAssignmentResponseDTO> assignmentsToAdd;
    private List<KpiAssignmentResponseDTO> assignmentsToRemove;
    private List<String> warnings;
}
