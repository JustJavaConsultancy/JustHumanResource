package com.justjava.humanresource.hr.dto;
import lombok.Data;
import java.util.List;

@Data
public class KpiBulkAssignmentRequestDTO {

    private Long employeeId;   // nullable
    private Long jobStepId;    // nullable

    private List<KpiAssignmentItemRequestDTO> kpis;
}
