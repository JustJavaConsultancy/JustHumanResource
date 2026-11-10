package com.justjava.humanresource.request.service;

import com.justjava.humanresource.request.grouped.GroupedApprovalService;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestApprovalActionLabelTest {
    @Mock RuntimeService runtimeService;
    @Mock GroupedApprovalService groupedApprovals;
    @InjectMocks WorkflowRequestService service;

    private String label() {
        return ReflectionTestUtils.invokeMethod(service, "approvalActionLabel",
                FlowableTaskDTO.builder().taskId("task").processInstanceId("current-cycle").build());
    }

    @Test void earlierStepsRecommendAndFinalStepApproves() {
        when(runtimeService.getVariable("current-cycle", "approverIds")).thenReturn(List.of("10", "20", "30"));
        when(runtimeService.getVariable("current-cycle", "currentLevel")).thenReturn(1, 2, 3);
        assertEquals("Recommend", label());
        assertEquals("Recommend", label());
        assertEquals("Approve", label());
    }

    @Test void singleStepAndForwardedFreeRouteApprove() {
        when(runtimeService.getVariable("current-cycle", "approverIds")).thenReturn(List.of("30"));
        when(runtimeService.getVariable("current-cycle", "currentLevel")).thenReturn(1, 4);
        assertEquals("Approve", label());
        assertEquals("Approve", label());
    }

    @Test void groupedTaskUsesGroupedRouteMetadata() {
        when(groupedApprovals.approvalActionLabel("task")).thenReturn("Recommend", "Approve");
        assertEquals("Recommend", label());
        assertEquals("Approve", label());
        verifyNoInteractions(runtimeService);
    }
}
