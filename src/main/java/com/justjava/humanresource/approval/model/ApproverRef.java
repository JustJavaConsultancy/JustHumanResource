package com.justjava.humanresource.approval.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApproverRef {
    private Long employeeId;
    private int level;
}
