package com.justjava.humanresource.approval.dto;

import com.justjava.humanresource.approval.entity.CustomApprovalPath;
import com.justjava.humanresource.approval.entity.CustomApprovalPathStep;

import java.util.List;

public record ApprovalPathResponse(
        Long id,
        String name,
        String description,
        boolean enabled,
        List<Step> steps
) {
    public static ApprovalPathResponse from(CustomApprovalPath path, List<CustomApprovalPathStep> steps) {
        return new ApprovalPathResponse(
                path.getId(),
                path.getName(),
                path.getDescription(),
                path.isEnabled(),
                steps.stream()
                        .map(step -> new Step(step.getId(), step.getSequenceNo(), step.getApproverEmployeeId()))
                        .toList()
        );
    }

    public record Step(Long id, Integer sequenceNo, Long approverEmployeeId) {
    }
}
