package com.justjava.humanresource.hr.dto;

import com.justjava.humanresource.core.enums.PayFrequency;
import com.justjava.humanresource.core.enums.RecordStatus;
import lombok.*;
import java.io.Serializable;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayGroupSummaryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String name;
    private PayFrequency payFrequency;
    private RecordStatus status;
}