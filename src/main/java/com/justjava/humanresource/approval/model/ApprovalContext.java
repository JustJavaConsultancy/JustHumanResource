package com.justjava.humanresource.approval.model;

import com.justjava.humanresource.approval.enums.ApprovalModuleType;
import com.justjava.humanresource.approval.enums.ApprovalRouteType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApprovalContext {
    private ApprovalModuleType moduleType;
    private ApprovalRouteType routeType;
    private Long requesterEmployeeId;
    private Long moduleRefId;
    private String requestTypeCode;
    private Long customApprovalPathId;
}
