package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanApprovalRouteType;
import com.justjava.humanresource.loan.enums.LoanApprovalStage;
import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

/** Route snapshot captured at submission plus live progress through it. */
@Value
@Builder
public class LoanApprovalRouteResponse {
    LoanApprovalRouteType routeType;
    /** "Role-based HR then Finance" or "Custom approval path". */
    String routeLabel;
    Long customApprovalPathId;
    String customApprovalPathName;

    /** Null once the route has finished or before submission. */
    LoanApprovalStage currentStage;
    /** e.g. "HR group", "Finance group", or the named custom approver. */
    String currentOwnerLabel;
    Long currentApproverEmployeeId;
    String currentApproverName;
    String currentApproverGroup;

    int totalSteps;
    int completedSteps;

    @Builder.Default List<LoanApprovalStepResponse> steps = new ArrayList<>();
}
