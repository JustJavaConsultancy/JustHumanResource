package com.justjava.humanresource.request.dto;

import com.justjava.humanresource.request.enums.FinanceActionType;
import com.justjava.humanresource.request.enums.RequestStatus;


public record WorkflowRequestFinanceActionDTO(
        Long id,
        FinanceActionType actionType,
        Long actorEmployeeId,
        Long targetEmployeeId,
        String comment,
        RequestStatus statusBefore,
        RequestStatus statusAfter,
        Integer supersededStepCount,
        boolean finalDecision,
        Object createdAt) {
}