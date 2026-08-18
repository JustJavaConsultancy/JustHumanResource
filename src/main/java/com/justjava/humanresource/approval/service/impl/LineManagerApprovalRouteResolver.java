package com.justjava.humanresource.approval.service.impl;

import com.justjava.humanresource.approval.enums.ApprovalModuleType;
import com.justjava.humanresource.approval.model.ApprovalContext;
import com.justjava.humanresource.approval.model.ApproverRef;
import com.justjava.humanresource.approval.service.ApprovalRouteResolver;
import com.justjava.humanresource.orgStructure.enums.ReportingType;
import com.justjava.humanresource.orgStructure.repositories.EmployeeReportingLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class LineManagerApprovalRouteResolver implements ApprovalRouteResolver {

    private final EmployeeReportingLineRepository reportingLineRepository;

    @Override
    public boolean supports(ApprovalContext context) {
        return context.getModuleType() == ApprovalModuleType.LEAVE
                || context.getModuleType() == ApprovalModuleType.REQUEST;
    }

    @Override
    public List<ApproverRef> resolveApprovers(ApprovalContext context) {
        List<Long> managerIds = reportingLineRepository.findManagerChainIds(
                context.getRequesterEmployeeId(),
                ReportingType.PRIMARY.name(),
                LocalDate.now()
        );

        Set<Long> uniqueOrdered = new LinkedHashSet<>();
        for (Long id : managerIds) {
            if (id != null && !id.equals(context.getRequesterEmployeeId())) {
                uniqueOrdered.add(id);
            }
        }

        List<ApproverRef> result = new ArrayList<>();
        int level = 1;
        for (Long managerId : uniqueOrdered) {
            result.add(ApproverRef.builder()
                    .employeeId(managerId)
                    .level(level++)
                    .build());
        }
        return result;
    }
}
