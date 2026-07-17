package com.justjava.humanresource.hr.dto;

import lombok.*;
import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JobStepSummaryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String stepName;
    private BigDecimal grossSalary; // adjust type if JobStep uses Double instead
}