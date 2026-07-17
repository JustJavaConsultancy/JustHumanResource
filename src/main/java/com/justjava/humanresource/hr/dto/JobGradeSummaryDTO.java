package com.justjava.humanresource.hr.dto;

import lombok.*;
import java.io.Serializable;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JobGradeSummaryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
}