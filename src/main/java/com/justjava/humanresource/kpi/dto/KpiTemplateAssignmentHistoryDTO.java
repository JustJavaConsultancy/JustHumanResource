package com.justjava.humanresource.kpi.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class KpiTemplateAssignmentHistoryDTO {
    private Long id;
    private Long templateId;
    private String templateName;
    private Integer templateVersionNumber;
    private String scope;
    private Long ownerId;
    private String ownerName;
    private LocalDate validFrom;
    private LocalDate validTo;
    private boolean active;
    private String appliedBy;
    private LocalDateTime createdAt;
}
