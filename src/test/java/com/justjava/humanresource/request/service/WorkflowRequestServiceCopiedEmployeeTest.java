package com.justjava.humanresource.request.service;

import com.justjava.humanresource.approval.service.ApprovalRouteResolverFactory;
import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobGradeRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.integration.fam.FamAssetLookupService;
import com.justjava.humanresource.request.dto.CreateWorkflowRequestCommand;
import com.justjava.humanresource.request.dto.WorkflowRequestInboxItemDTO;
import com.justjava.humanresource.request.entity.*;
import com.justjava.humanresource.request.enums.ApprovalDecision;
import com.justjava.humanresource.request.enums.RequestActivityType;
import com.justjava.humanresource.request.enums.RequestStatus;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.request.handler.WorkflowRequestHandlerRegistry;
import com.justjava.humanresource.request.handler.WorkflowRequestTypeHandler;
import com.justjava.humanresource.request.repository.*;
import com.justjava.humanresource.utils.AfterCommitExecutor;
import com.justjava.humanresource.utils.FinanceRequestEmailService;
import com.justjava.humanresource.utils.RequestEmailService;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRequestServiceCopiedEmployeeTest {

    @Mock private com.justjava.humanresource.request.grouped.GroupedApprovalService groupedApprovals;
    @Mock private WorkflowRequestRepository requestRepository;
    @Mock private WorkflowRequestTypeRepository typeRepository;
    @Mock private WorkflowRequestItemRepository itemRepository;
    @Mock private WorkflowRequestAttachmentRepository attachmentRepository;
    @Mock private WorkflowRequestApprovalStepRepository stepRepository;
    @Mock private WorkflowRequestCommentRepository commentRepository;
    @Mock private WorkflowRequestActivityRepository activityRepository;
    @Mock private StaffRequisitionDetailRepository staffRepository;
    @Mock private FileRequestDetailRepository fileRepository;
    @Mock private AssetRequestDetailRepository assetRepository;
    @Mock private ExpenseReimbursementDetailRepository expenseRepository;
    @Mock private ExpenseReimbursementItemRepository expenseItemRepository;
    @Mock private WorkflowRequestHandlerRegistry handlers;
    @Mock private RequestNumberService numberService;
    @Mock private WorkflowRequestActivityService activityService;
    @Mock private EmployeeService employeeService;
    @Mock private AuthenticationManager auth;
    @Mock private ApprovalRouteResolverFactory routeFactory;
    @Mock private RuntimeService runtimeService;
    @Mock private FlowableTaskService taskService;
    @Mock private TaskService flowableTaskService;
    @Mock private FamAssetLookupService famAssetLookupService;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private JobGradeRepository jobGradeRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private RequestEmailService requestEmailService;
    @Mock private AfterCommitExecutor afterCommitExecutor;
    @Mock private WorkflowRequestFreeRouteEventRepository freeRouteEventRepository;
    @Mock private WorkflowRequestFinanceActionRepository financeActionRepository;
    @Mock private WorkflowRequestCopiedEmployeeRepository copiedEmployeeRepository;
    @Mock private WorkflowRequestRecommendationRepository recommendationRepository;
    @Mock private FinanceRequestEmailService financeRequestEmailService;

    @InjectMocks private WorkflowRequestService service;

    private Employee requester;
    private WorkflowRequestTypeHandler handler;

    @BeforeEach
    void setUp() {
        requester = new Employee();
        requester.setId(1L);
        lenient().when(auth.get("email")).thenReturn("requester@example.test");
        lenient().when(employeeService.getByEmail("requester@example.test")).thenReturn(requester);

        handler = org.mockito.Mockito.mock(WorkflowRequestTypeHandler.class);
        lenient().when(handlers.get(RequestType.GENERAL_REQUEST)).thenReturn(handler);
    }

    @Test
    void financeApprovalSupersedesGroupedAssignmentsBeforeFinalizing() {
        WorkflowRequest r = request(10L,2L,RequestStatus.IN_APPROVAL);
        when(auth.isFinancialOfficer()).thenReturn(true);
        when(requestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(r));
        when(requestRepository.save(r)).thenReturn(r);
        when(financeActionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            WorkflowRequestFinanceAction action = invocation.getArgument(0); action.setId(90L); return action;
        });
        service.financeApprove(10L,"Approved by Finance");
        verify(groupedApprovals).supersede(10L,1L,"Finance override: APPROVE");
        verify(handler).afterApproved(r);
        assertEquals(RequestStatus.APPROVED,r.getStatus());
        assertTrue(r.isFinanceDecisionConsumed());
    }

    @Test
    void requesterCancellationAlsoRetiresGroupedAssignments() {
        WorkflowRequest r = request(10L,1L,RequestStatus.IN_APPROVAL);
        when(requestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(r));
        when(requestRepository.save(r)).thenReturn(r);
        service.cancel(10L);
        verify(groupedApprovals).supersede(10L,1L,"Cancelled by requester");
        assertEquals(RequestStatus.CANCELLED,r.getStatus());
    }

    @Test
    void automaticApprovalRemainsAutomaticEvenIfAnUnusedGroupedReferenceExists() {
        WorkflowRequest r = request(10L,1L,RequestStatus.DRAFT);
        WorkflowRequestType type = new WorkflowRequestType(); type.setCode("GENERAL_REQUEST");
        type.setRequiresApproval(false); type.setGroupedApprovalPathVersionId(99L);
        when(groupedApprovals.pinnedVersion(10L)).thenReturn(null);
        when(requestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(r));
        when(typeRepository.findByCode("GENERAL_REQUEST")).thenReturn(Optional.of(type));
        when(requestRepository.save(r)).thenReturn(r);
        service.submit(10L);
        verify(groupedApprovals,never()).start(any(),any());
        verify(handler).afterApproved(r);
        assertEquals(RequestStatus.APPROVED,r.getStatus());
    }

    @Test
    void groupedMemberCanViewOnlyAfterTheirTaskHasBeenActivated() {
        WorkflowRequest r = request(10L,2L,RequestStatus.IN_APPROVAL);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(r));
        when(groupedApprovals.mayView(10L,1L)).thenReturn(true);
        assertEquals(r,service.visible(10L));
        when(groupedApprovals.mayView(10L,1L)).thenReturn(false);
        assertThrows(IllegalStateException.class,() -> service.visible(10L));
    }

    @Test
    void employeeInboxDeduplicatesRequestsAndCombinesCurrentRoles() {
        WorkflowRequest owned = request(10L, 1L, RequestStatus.IN_APPROVAL);
        WorkflowRequest copied = request(20L, 2L, RequestStatus.IN_APPROVAL);
        WorkflowRequest copiedAndAssigned = request(30L, 3L, RequestStatus.IN_APPROVAL);
        WorkflowRequestCopiedEmployee copiedMembership = new WorkflowRequestCopiedEmployee();
        copiedMembership.setWorkflowRequestId(20L);
        copiedMembership.setEmployeeId(1L);
        WorkflowRequestCopiedEmployee overlapMembership = new WorkflowRequestCopiedEmployee();
        overlapMembership.setWorkflowRequestId(30L);
        overlapMembership.setEmployeeId(1L);
        FlowableTaskDTO task = FlowableTaskDTO.builder()
                .taskId("task-30")
                .taskName("Review request")
                .assignee("1")
                .variables(Map.of("workflowRequestId", 30L))
                .build();

        when(requestRepository.findByRequesterEmployeeIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(owned));
        when(copiedEmployeeRepository.findByEmployeeIdIn(List.of(1L)))
                .thenReturn(List.of(copiedMembership, overlapMembership));
        when(typeRepository.findByEnabledTrueOrderByName()).thenReturn(List.of());
        when(taskService.getTasksForAssigneeAcrossProcesses(any(), any())).thenReturn(List.of(task));
        when(groupedApprovals.inboxLabel("task-30")).thenReturn("Level 1: Review — ALL — 1 of 3 approvals received");
        when(requestRepository.findByFinanceOverrideRecipientIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(copiedAndAssigned));
        when(requestRepository.findAllById(anyCollection()))
                .thenReturn(List.of(owned, copied, copiedAndAssigned));

        List<WorkflowRequestInboxItemDTO> inbox = service.employeeInbox();

        assertEquals(3, inbox.size());
        assertEquals(3, inbox.stream().map(item -> item.getRequest().getId()).distinct().count());
        WorkflowRequestInboxItemDTO ownedItem = inbox.stream()
                .filter(item -> item.getRequest().getId().equals(10L))
                .findFirst()
                .orElseThrow();
        assertTrue(ownedItem.isRequester());
        WorkflowRequestInboxItemDTO copiedItem = inbox.stream()
                .filter(item -> item.getRequest().getId().equals(20L))
                .findFirst()
                .orElseThrow();
        assertTrue(copiedItem.isCopied());
        assertTrue(!copiedItem.isApprovalRequired());
        WorkflowRequestInboxItemDTO overlapItem = inbox.stream()
                .filter(item -> item.getRequest().getId().equals(30L))
                .findFirst()
                .orElseThrow();
        assertTrue(overlapItem.isCopied());
        assertTrue(overlapItem.isApprovalRequired());
        assertTrue(overlapItem.getApprovalTasks().getFirst().isGroupedApproval());
        assertTrue(overlapItem.getApprovalTasks().getFirst().getApprovalLevelLabel().contains("1 of 3"));
        assertTrue(overlapItem.isFinanceForwardRecipient());
        assertEquals(1, overlapItem.getApprovalTasks().size());
    }

    @Test
    void copiedEmployeeOptionsOnlyIncludeOtherActiveVisibleEmployees() {
        when(auth.isEmployee()).thenReturn(true);
        Employee eligible = new Employee();
        eligible.setId(2L);
        eligible.setEmployeeNumber("E002");
        eligible.setFirstName("Eligible");
        eligible.setLastName("Employee");
        eligible.setEmploymentStatus(com.justjava.humanresource.core.enums.EmploymentStatus.ACTIVE);
        eligible.setStatus(com.justjava.humanresource.core.enums.RecordStatus.ACTIVE);
        Employee inactive = new Employee();
        inactive.setId(3L);
        inactive.setEmploymentStatus(com.justjava.humanresource.core.enums.EmploymentStatus.TERMINATED);
        inactive.setStatus(com.justjava.humanresource.core.enums.RecordStatus.ACTIVE);
        when(employeeRepository.findAllVisible()).thenReturn(List.of(requester, eligible, inactive));

        var options = service.copiedEmployeeOptions();

        assertEquals(1, options.size());
        assertEquals(2L, options.getFirst().value());
        assertTrue(options.getFirst().label().contains("E002"));
    }

    @Test
    void copiedEmployeeOptionsRequireEmployeeRole() {
        when(auth.isEmployee()).thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.copiedEmployeeOptions());
    }

    @Test
    void createDraftPersistsValidatedCopiedEmployeeMemberships() {
        stubGeneralRequestType();
        when(numberService.next(RequestType.GENERAL_REQUEST)).thenReturn("REQ-TEST-1");
        when(requestRepository.save(any(WorkflowRequest.class))).thenAnswer(invocation -> {
            WorkflowRequest request = invocation.getArgument(0);
            request.setId(100L);
            return request;
        });
        when(copiedEmployeeRepository.findByWorkflowRequestIdOrderByCreatedAtAscIdAsc(100L))
                .thenReturn(List.of());
        Employee copied = new Employee();
        copied.setId(2L);
        copied.setEmploymentStatus(com.justjava.humanresource.core.enums.EmploymentStatus.ACTIVE);
        copied.setStatus(com.justjava.humanresource.core.enums.RecordStatus.ACTIVE);
        when(employeeRepository.findAllById(anyCollection())).thenReturn(List.of(copied));

        CreateWorkflowRequestCommand command = new CreateWorkflowRequestCommand();
        command.setRequestType(RequestType.GENERAL_REQUEST);
        command.setTitle("Equipment request");
        command.setCopiedEmployeeIds(List.of(2L));

        service.createDraft(command);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WorkflowRequestCopiedEmployee>> memberships = ArgumentCaptor.forClass(List.class);
        verify(copiedEmployeeRepository).saveAll(memberships.capture());
        assertEquals(1, memberships.getValue().size());
        assertEquals(100L, memberships.getValue().getFirst().getWorkflowRequestId());
        assertEquals(2L, memberships.getValue().getFirst().getEmployeeId());
    }

    @Test
    void createDraftRejectsRequesterAsCopiedEmployeeBeforeSavingRequest() {
        stubGeneralRequestType();
        CreateWorkflowRequestCommand command = new CreateWorkflowRequestCommand();
        command.setRequestType(RequestType.GENERAL_REQUEST);
        command.setTitle("Equipment request");
        command.setCopiedEmployeeIds(List.of(requester.getId()));

        assertThrows(IllegalArgumentException.class, () -> service.createDraft(command));

        verify(requestRepository, never()).save(any(WorkflowRequest.class));
        verify(copiedEmployeeRepository, never()).saveAll(anyList());
    }

    @Test
    void createDraftRejectsDuplicateCopiedEmployees() {
        stubGeneralRequestType();
        CreateWorkflowRequestCommand command = new CreateWorkflowRequestCommand();
        command.setRequestType(RequestType.GENERAL_REQUEST);
        command.setTitle("Equipment request");
        command.setCopiedEmployeeIds(List.of(2L, 2L));

        assertThrows(IllegalArgumentException.class, () -> service.createDraft(command));

        verify(employeeRepository, never()).findAllById(anyCollection());
        verify(requestRepository, never()).save(any(WorkflowRequest.class));
    }

    @Test
    void createDraftRejectsInactiveCopiedEmployees() {
        stubGeneralRequestType();
        Employee inactive = new Employee();
        inactive.setId(2L);
        inactive.setEmploymentStatus(com.justjava.humanresource.core.enums.EmploymentStatus.TERMINATED);
        inactive.setStatus(com.justjava.humanresource.core.enums.RecordStatus.ACTIVE);
        when(employeeRepository.findAllById(anyCollection())).thenReturn(List.of(inactive));

        CreateWorkflowRequestCommand command = new CreateWorkflowRequestCommand();
        command.setRequestType(RequestType.GENERAL_REQUEST);
        command.setTitle("Equipment request");
        command.setCopiedEmployeeIds(List.of(2L));

        assertThrows(IllegalArgumentException.class, () -> service.createDraft(command));

        verify(requestRepository, never()).save(any(WorkflowRequest.class));
    }

    private void stubGeneralRequestType() {
        WorkflowRequestType requestType = new WorkflowRequestType();
        requestType.setEnabled(true);
        when(typeRepository.findByCode(RequestType.GENERAL_REQUEST.name()))
                .thenReturn(Optional.of(requestType));
    }

    @Test
    void updateFromOlderClientPreservesExistingCopiedEmployeesWhenListIsOmitted() {
        WorkflowRequest request = new WorkflowRequest();
        request.setId(100L);
        request.setRequesterEmployeeId(requester.getId());
        request.setRequestType(RequestType.GENERAL_REQUEST);
        request.setStatus(RequestStatus.DRAFT);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(WorkflowRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateWorkflowRequestCommand command = new CreateWorkflowRequestCommand();
        command.setRequestType(RequestType.GENERAL_REQUEST);
        command.setTitle("Updated request");

        service.updateEditableRequest(100L, command);

        verify(copiedEmployeeRepository, never()).deleteByWorkflowRequestId(anyLong());
        verify(copiedEmployeeRepository, never()).saveAll(anyList());
    }

    @Test
    void updatePreservesPreviouslyCopiedEmployeeWhoIsNoLongerActive() {
        WorkflowRequest request = request(RequestStatus.DRAFT);
        WorkflowRequestCopiedEmployee inactiveMembership = new WorkflowRequestCopiedEmployee();
        inactiveMembership.setWorkflowRequestId(request.getId());
        inactiveMembership.setEmployeeId(2L);
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(requestRepository.save(any(WorkflowRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(copiedEmployeeRepository.findByWorkflowRequestIdOrderByCreatedAtAscIdAsc(request.getId()))
                .thenReturn(List.of(inactiveMembership));

        CreateWorkflowRequestCommand command = new CreateWorkflowRequestCommand();
        command.setRequestType(RequestType.GENERAL_REQUEST);
        command.setTitle("Updated request");
        command.setCopiedEmployeeIds(List.of(2L));

        service.updateEditableRequest(request.getId(), command);

        verify(employeeRepository, never()).findAllById(anyCollection());
        verify(copiedEmployeeRepository).deleteAll(List.of());
        verify(copiedEmployeeRepository).saveAll(List.of());
    }

    @Test
    void copiedEmployeesCanBeManagedFromDraftAndExistingInactiveRecipientsCanBeRetained() {
        WorkflowRequest request = request(RequestStatus.DRAFT);
        WorkflowRequestCopiedEmployee inactiveMembership = new WorkflowRequestCopiedEmployee();
        inactiveMembership.setWorkflowRequestId(request.getId());
        inactiveMembership.setEmployeeId(3L);
        Employee activeAddition = new Employee();
        activeAddition.setId(2L);
        activeAddition.setEmploymentStatus(com.justjava.humanresource.core.enums.EmploymentStatus.ACTIVE);
        activeAddition.setStatus(com.justjava.humanresource.core.enums.RecordStatus.ACTIVE);
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(copiedEmployeeRepository.findByWorkflowRequestIdOrderByCreatedAtAscIdAsc(request.getId()))
                .thenReturn(List.of(inactiveMembership));
        when(employeeRepository.findAllById(anyCollection())).thenReturn(List.of(activeAddition));

        service.updateCopiedEmployees(request.getId(), List.of(3L, 2L));

        verify(copiedEmployeeRepository).deleteAll(List.of());
        ArgumentCaptor<List<WorkflowRequestCopiedEmployee>> additions = ArgumentCaptor.forClass(List.class);
        verify(copiedEmployeeRepository).saveAll(additions.capture());
        assertEquals(1, additions.getValue().size());
        assertEquals(2L, additions.getValue().getFirst().getEmployeeId());
        verify(activityService).record(request.getId(), RequestActivityType.UPDATED,
                "Copied employees updated", requester.getId());
    }

    @Test
    void copiedEmployeesCannotBeChangedAfterRequestIsSubmitted() {
        WorkflowRequest request = request(RequestStatus.IN_APPROVAL);
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(IllegalStateException.class,
                () -> service.updateCopiedEmployees(request.getId(), List.of(2L)));

        verify(copiedEmployeeRepository, never()).findByWorkflowRequestIdOrderByCreatedAtAscIdAsc(anyLong());
        verify(copiedEmployeeRepository, never()).saveAll(anyList());
    }

    @Test
    void copiedEmployeeCanReadNonterminalRequest() {
        Employee copied = setCurrentActor(2L, "copied@example.test");
        WorkflowRequest request = request(RequestStatus.IN_APPROVAL);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(financeActionRepository.existsByWorkflowRequestIdAndActorEmployeeIdAndActionTypeIn(
                anyLong(), anyLong(), anyCollection())).thenReturn(false);
        when(stepRepository.findByWorkflowRequestIdOrderBySequenceNo(100L)).thenReturn(List.of());
        when(copiedEmployeeRepository.existsByWorkflowRequestIdAndEmployeeId(100L, copied.getId()))
                .thenReturn(true);

        assertEquals(request, service.visible(100L));
    }

    @Test
    void copiedEmployeeCannotReadRequestAfterTerminalStatus() {
        setCurrentActor(2L, "copied@example.test");
        WorkflowRequest request = request(RequestStatus.APPROVED);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(financeActionRepository.existsByWorkflowRequestIdAndActorEmployeeIdAndActionTypeIn(
                anyLong(), anyLong(), anyCollection())).thenReturn(false);
        when(stepRepository.findByWorkflowRequestIdOrderBySequenceNo(100L)).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.visible(100L));
    }

    @Test
    void copiedEmployeeCanRecommendAndAuthorComesFromAuthentication() {
        Employee copied = setCurrentActor(2L, "copied@example.test");
        WorkflowRequest request = request(RequestStatus.IN_APPROVAL);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(financeActionRepository.existsByWorkflowRequestIdAndActorEmployeeIdAndActionTypeIn(
                anyLong(), anyLong(), anyCollection())).thenReturn(false);
        when(stepRepository.findByWorkflowRequestIdOrderBySequenceNo(100L)).thenReturn(List.of());
        when(copiedEmployeeRepository.existsByWorkflowRequestIdAndEmployeeId(100L, copied.getId()))
                .thenReturn(true);
        when(recommendationRepository.save(any(WorkflowRequestRecommendation.class))).thenAnswer(invocation -> {
            WorkflowRequestRecommendation recommendation = invocation.getArgument(0);
            recommendation.setId(7L);
            recommendation.setCreatedAt(LocalDateTime.now());
            return recommendation;
        });

        var result = service.addRecommendation(100L, "  Please consider this option.  ");

        assertEquals(2L, result.authorEmployeeId());
        assertEquals("Copied Employee", result.authorEmployeeName());
        assertEquals("Please consider this option.", result.text());
        ArgumentCaptor<WorkflowRequestRecommendation> recommendation =
                ArgumentCaptor.forClass(WorkflowRequestRecommendation.class);
        verify(recommendationRepository).save(recommendation.capture());
        assertEquals(2L, recommendation.getValue().getAuthorEmployeeId());
    }

    @Test
    void nonCopiedApproverCannotRecommend() {
        setCurrentActor(2L, "approver@example.test");
        WorkflowRequest request = request(RequestStatus.IN_APPROVAL);
        WorkflowRequestApprovalStep oldStep = new WorkflowRequestApprovalStep();
        oldStep.setApproverEmployeeId(2L);
        oldStep.setDecision(ApprovalDecision.APPROVED);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(financeActionRepository.existsByWorkflowRequestIdAndActorEmployeeIdAndActionTypeIn(
                anyLong(), anyLong(), anyCollection())).thenReturn(false);
        when(stepRepository.findByWorkflowRequestIdOrderBySequenceNo(100L)).thenReturn(List.of(oldStep));
        when(copiedEmployeeRepository.existsByWorkflowRequestIdAndEmployeeId(100L, 2L)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.addRecommendation(100L, "Recommendation"));
        verify(recommendationRepository, never()).save(any(WorkflowRequestRecommendation.class));
    }

    private Employee setCurrentActor(Long employeeId, String email) {
        Employee actor = new Employee();
        actor.setId(employeeId);
        actor.setFirstName("Copied");
        actor.setLastName("Employee");
        when(auth.get("email")).thenReturn(email);
        when(employeeService.getByEmail(email)).thenReturn(actor);
        return actor;
    }

    private WorkflowRequest request(RequestStatus status) {
        return request(100L, requester.getId(), status);
    }

    private WorkflowRequest request(Long id, Long requesterEmployeeId, RequestStatus status) {
        WorkflowRequest request = new WorkflowRequest();
        request.setId(id);
        request.setRequesterEmployeeId(requesterEmployeeId);
        request.setRequestType(RequestType.GENERAL_REQUEST);
        request.setStatus(status);
        return request;
    }
}
