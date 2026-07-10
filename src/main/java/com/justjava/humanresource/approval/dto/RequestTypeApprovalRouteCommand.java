package com.justjava.humanresource.approval.dto;

import com.justjava.humanresource.approval.enums.ApprovalRouteType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RequestTypeApprovalRouteCommand {
    @NotNull
    private ApprovalRouteType approvalRouteType;

    private Long customApprovalPathId;
}
