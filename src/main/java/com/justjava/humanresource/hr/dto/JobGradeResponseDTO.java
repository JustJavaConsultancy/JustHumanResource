package com.justjava.humanresource.hr.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class JobGradeResponseDTO {

    private Long id;
    private String name;

    private List<JobStepSummaryDTO> steps;

    @Getter
    @Builder
    public static class JobStepSummaryDTO {
        private Long id;
        private String name;
    }
}
