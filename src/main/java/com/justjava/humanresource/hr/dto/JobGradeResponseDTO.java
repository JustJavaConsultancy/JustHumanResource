package com.justjava.humanresource.hr.dto;

import com.justjava.humanresource.hr.entity.JobStep;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class JobGradeResponseDTO {

    private Long id;
    private String name;
    private String departmentName;


    private List<JobStepSummaryDTO> steps;

    @Getter
    @Builder
    public static class JobStepSummaryDTO {
        private Long id;
        private String name;
        private BigDecimal basicSalary;
    }
}
