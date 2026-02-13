package com.justjava.humanresource.hr.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class CreateJobGradeWithStepsCommand {

    private String gradeName;

    private Long departmentId;

    private List<JobStepCommand> steps;

    @Getter
    @Setter
    public static class JobStepCommand {
        private String stepName;
        private BigDecimal basicSalary;
    }
}
