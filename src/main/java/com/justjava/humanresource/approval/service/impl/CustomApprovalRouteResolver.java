package com.justjava.humanresource.approval.service.impl;

import com.justjava.humanresource.approval.entity.CustomApprovalPath;
import com.justjava.humanresource.approval.enums.ApprovalModuleType;
import com.justjava.humanresource.approval.enums.ApprovalRouteType;
import com.justjava.humanresource.approval.model.ApprovalContext;
import com.justjava.humanresource.approval.model.ApproverRef;
import com.justjava.humanresource.approval.repository.CustomApprovalPathRepository;
import com.justjava.humanresource.approval.repository.CustomApprovalPathStepRepository;
import com.justjava.humanresource.approval.service.ApprovalRouteResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CustomApprovalRouteResolver implements ApprovalRouteResolver {

    private final CustomApprovalPathRepository pathRepository;
    private final CustomApprovalPathStepRepository stepRepository;

    @Override
    public boolean supports(ApprovalContext context) {
        return context.getModuleType() == ApprovalModuleType.REQUEST
                && context.getRouteType() == ApprovalRouteType.CUSTOM;
    }

    @Override
    public List<ApproverRef> resolveApprovers(ApprovalContext context) {
        if (context.getCustomApprovalPathId() == null) {
            throw new IllegalStateException("Custom approval path is not configured for this request type.");
        }

        CustomApprovalPath path = pathRepository.findById(context.getCustomApprovalPathId())
                .orElseThrow(() -> new IllegalStateException("Custom approval path not found."));
        if (!path.isEnabled()) {
            throw new IllegalStateException("Custom approval path is disabled.");
        }

        Set<Long> seen = new LinkedHashSet<>();
        return stepRepository.findByCustomApprovalPathIdOrderBySequenceNo(path.getId()).stream()
                .map(step -> step.getApproverEmployeeId())
                .filter(id -> id != null && !id.equals(context.getRequesterEmployeeId()))
                .filter(seen::add)
                .map(id -> ApproverRef.builder()
                        .employeeId(id)
                        .level(seen.size())
                        .build())
                .toList();
    }
}
