package com.justjava.humanresource.approval.service;

import com.justjava.humanresource.approval.entity.CustomApprovalPath;
import com.justjava.humanresource.approval.entity.CustomApprovalPathStep;
import com.justjava.humanresource.approval.enums.ApprovalModuleType;
import com.justjava.humanresource.approval.enums.ApprovalRouteType;
import com.justjava.humanresource.approval.model.ApprovalContext;
import com.justjava.humanresource.approval.repository.CustomApprovalPathRepository;
import com.justjava.humanresource.approval.repository.CustomApprovalPathStepRepository;
import com.justjava.humanresource.approval.service.impl.CustomApprovalRouteResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomApprovalRouteResolverTest {
    private final CustomApprovalPathRepository pathRepository = mock(CustomApprovalPathRepository.class);
    private final CustomApprovalPathStepRepository stepRepository = mock(CustomApprovalPathStepRepository.class);
    private final CustomApprovalRouteResolver resolver = new CustomApprovalRouteResolver(pathRepository, stepRepository);

    @Test
    void supportsOnlyRequestCustomRoutes() {
        assertTrue(resolver.supports(context(ApprovalRouteType.CUSTOM)));
        assertFalse(resolver.supports(context(ApprovalRouteType.LINE_MANAGER)));
        assertFalse(resolver.supports(ApprovalContext.builder()
                .moduleType(ApprovalModuleType.LEAVE)
                .routeType(ApprovalRouteType.CUSTOM)
                .build()));
    }

    @Test
    void resolvesEnabledPathStepsInSequence() {
        CustomApprovalPath path = path(true);
        when(pathRepository.findById(10L)).thenReturn(Optional.of(path));
        when(stepRepository.findByCustomApprovalPathIdOrderBySequenceNo(10L)).thenReturn(List.of(step(1, 20L), step(2, 30L)));

        var approvers = resolver.resolveApprovers(context(ApprovalRouteType.CUSTOM));

        assertEquals(2, approvers.size());
        assertEquals(20L, approvers.get(0).getEmployeeId());
        assertEquals(1, approvers.get(0).getLevel());
        assertEquals(30L, approvers.get(1).getEmployeeId());
        assertEquals(2, approvers.get(1).getLevel());
    }

    @Test
    void rejectsDisabledPath() {
        when(pathRepository.findById(10L)).thenReturn(Optional.of(path(false)));

        assertThrows(IllegalStateException.class, () -> resolver.resolveApprovers(context(ApprovalRouteType.CUSTOM)));
    }

    @Test
    void removesRequesterAndDuplicateApprovers() {
        CustomApprovalPath path = path(true);
        when(pathRepository.findById(10L)).thenReturn(Optional.of(path));
        when(stepRepository.findByCustomApprovalPathIdOrderBySequenceNo(10L)).thenReturn(List.of(step(1, 7L), step(2, 20L), step(3, 20L)));

        var approvers = resolver.resolveApprovers(context(ApprovalRouteType.CUSTOM));

        assertEquals(1, approvers.size());
        assertEquals(20L, approvers.getFirst().getEmployeeId());
    }

    private ApprovalContext context(ApprovalRouteType routeType) {
        return ApprovalContext.builder()
                .moduleType(ApprovalModuleType.REQUEST)
                .routeType(routeType)
                .requesterEmployeeId(7L)
                .customApprovalPathId(10L)
                .build();
    }

    private CustomApprovalPath path(boolean enabled) {
        CustomApprovalPath path = new CustomApprovalPath();
        path.setId(10L);
        path.setEnabled(enabled);
        return path;
    }

    private CustomApprovalPathStep step(int sequence, Long employeeId) {
        CustomApprovalPathStep step = new CustomApprovalPathStep();
        step.setSequenceNo(sequence);
        step.setApproverEmployeeId(employeeId);
        return step;
    }
}
