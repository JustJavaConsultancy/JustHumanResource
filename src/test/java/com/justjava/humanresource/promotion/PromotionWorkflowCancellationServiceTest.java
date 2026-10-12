package com.justjava.humanresource.promotion;

import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.service.PromotionAuthorizationService;
import com.justjava.humanresource.promotion.service.PromotionCurrentUserService;
import com.justjava.humanresource.promotion.service.PromotionWorkflowServiceImpl;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionWorkflowCancellationServiceTest {

    @Mock RuntimeService runtimeService;
    @Mock TaskService taskService;
    @Mock FlowableTaskService flowableTaskService;
    @Mock PromotionCurrentUserService currentUserService;
    @Mock PromotionAuthorizationService authorizationService;

    private PromotionWorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PromotionWorkflowServiceImpl(
                runtimeService,
                taskService,
                flowableTaskService,
                currentUserService,
                authorizationService
        );
    }

    @Test
    void deletesActiveApprovalProcessWhenCancelling() {
        PromotionRequest request = new PromotionRequest();
        request.setProcessInstanceId("process-1");
        ProcessInstanceQuery query = mock(ProcessInstanceQuery.class);
        ProcessInstance instance = mock(ProcessInstance.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.processInstanceId("process-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(instance);

        service.cancelApproval(request, "No longer needed");

        verify(runtimeService).deleteProcessInstance("process-1", "No longer needed");
    }

    @Test
    void ignoresCancellationWhenNoProcessInstanceExists() {
        PromotionRequest request = new PromotionRequest();

        service.cancelApproval(request, "No process");

        verify(runtimeService, never()).createProcessInstanceQuery();
    }
}
