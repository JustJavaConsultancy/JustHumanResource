package com.justjava.humanresource.request.grouped;

import com.justjava.humanresource.approval.enums.ApprovalRouteType;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.enums.*;
import com.justjava.humanresource.request.handler.WorkflowRequestHandlerRegistry;
import com.justjava.humanresource.request.repository.WorkflowRequestRepository;
import com.justjava.humanresource.request.service.WorkflowRequestActivityService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.task.api.Task;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

/** Domain state is authoritative. All mutations serialize on the owning request, before touching Flowable. */
@Service("groupedApprovalService")
@RequiredArgsConstructor
public class GroupedApprovalService {
    public static final String PROCESS_KEY = "groupedRequestApprovalProcess";
    private final GroupedApprovalConfigurationService configuration;
    private final GroupedApprovalCycleRepository cycles;
    private final GroupedApprovalLevelRepository levels;
    private final GroupedApprovalAssignmentRepository assignments;
    private final GroupedApprovalEventRepository events;
    private final GroupedApprovalNoticeRepository notices;
    private final WorkflowRequestRepository requests;
    private final WorkflowRequestHandlerRegistry handlers;
    private final WorkflowRequestActivityService activities;
    private final RuntimeService runtime;
    private final TaskService tasks;

    @Transactional
    public WorkflowRequest start(WorkflowRequest request, Long configuredVersionId) {
        configuration.requireEnabled();
        WorkflowRequest locked = requests.findByIdForUpdate(request.getId()).orElseThrow();
        if (locked.getWorkflowInstanceId() != null && runtime.createProcessInstanceQuery()
                .processInstanceId(locked.getWorkflowInstanceId()).count() > 0) throw conflict("This request already has an active approval process.");
        List<GroupedApprovalCycle> prior = cycles.findByRequestIdOrderByCycleNo(locked.getId());
        if (prior.stream().anyMatch(c -> "ACTIVE".equals(c.getState()))) throw conflict("An approval cycle is already active.");
        Long versionId = prior.isEmpty() ? configuredVersionId : prior.getLast().getPathVersionId();
        var version = configuration.published(versionId);
        var definitions = configuration.effectiveLevels(versionId, locked.getRequesterEmployeeId());
        GroupedApprovalCycle cycle = new GroupedApprovalCycle();
        cycle.setRequestId(locked.getId()); cycle.setCycleNo(prior.size() + 1);
        cycle.setPathVersionId(versionId); cycle.setPathName(version.getName() + " (v" + version.getRevision() + ")");
        cycle = cycles.saveAndFlush(cycle);
        int ordinal = 1;
        for (var definition : definitions) {
            GroupedApprovalLevel level = new GroupedApprovalLevel();
            level.setCycleId(cycle.getId()); level.setOrdinal(ordinal++); level.setName(definition.name()); level.setPolicy(definition.policy());
            level = levels.saveAndFlush(level);
            for (Long employeeId : definition.employeeIds()) {
                var employee = configuration.eligibleEmployee(employeeId);
                GroupedApprovalAssignment assignment = new GroupedApprovalAssignment();
                assignment.setRequestId(locked.getId()); assignment.setLevelId(level.getId()); assignment.setEmployeeId(employeeId);
                assignment.setEmployeeName(employee.getFullName()); assignments.save(assignment);
            }
        }
        assignments.flush();
        locked.setApprovalRouteType(ApprovalRouteType.CUSTOM); locked.setCurrentApprovalLevel(1);
        locked.setTotalApprovalLevels(definitions.size()); locked.setStatus(RequestStatus.IN_APPROVAL);
        requests.saveAndFlush(locked);
        Map<String,Object> variables = new HashMap<>(handlers.get(locked.getRequestType()).buildWorkflowVariables(locked));
        variables.put("workflowRequestId", locked.getId()); variables.put("groupCycleId", cycle.getId());
        variables.put("requestType", locked.getRequestType().name());
        var process = runtime.startProcessInstanceByKey(PROCESS_KEY, "GROUPED_REQUEST_" + locked.getId() + "_" + cycle.getCycleNo(), variables);
        locked.setWorkflowInstanceId(process.getId()); cycle.setProcessInstanceId(process.getId()); cycles.save(cycle);
        event(cycle, null, locked.getRequesterEmployeeId(), "SUBMITTED", "Grouped approval cycle " + cycle.getCycleNo() + " started");
        activities.record(locked.getId(), RequestActivityType.SUBMITTED, "Submitted for grouped approval", locked.getRequesterEmployeeId());
        notice(cycle, null, null, "SUBMITTED");
        return requests.save(locked);
    }

    @Transactional(readOnly=true)
    public boolean hasHistory(Long requestId) { return cycles.findFirstByRequestIdOrderByCycleNoDesc(requestId).isPresent(); }

    @Transactional(readOnly=true)
    public Long pinnedVersion(Long requestId) {
        return cycles.findFirstByRequestIdOrderByCycleNoDesc(requestId).map(GroupedApprovalCycle::getPathVersionId).orElse(null);
    }

    /** Called inside the Flowable command transaction before the multi-instance activity is entered. */
    @Transactional
    public void activate(DelegateExecution execution) {
        GroupedApprovalCycle cycle = cycle(execution);
        GroupedApprovalLevel level = currentLevel(cycle);
        level.setState("ACTIVE"); level.setActivatedAt(LocalDateTime.now()); levels.saveAndFlush(level);
        List<Map<String,String>> members = new ArrayList<>();
        for (var assignment : assignments.findByLevelIdOrderById(level.getId())) {
            assignment.setState("PENDING"); assignments.save(assignment);
            members.add(Map.of("id", assignment.getId().toString(), "employeeId", assignment.getEmployeeId().toString()));
            notice(cycle, assignment.getId(), assignment.getEmployeeId(), "PENDING");
        }
        if (members.isEmpty()) throw new IllegalStateException("Cannot activate an empty approval level.");
        execution.setVariable("groupAssignments", members);
        execution.setVariable("groupLevelResolved", false);
        execution.setVariable("groupMore", false);
    }

    @Transactional
    public void taskCreated(DelegateTask task) {
        Map<?,?> member = (Map<?,?>) task.getVariable("groupAssignment");
        var assignment = assignments.findById(Long.valueOf(member.get("id").toString())).orElseThrow();
        var level = levels.findById(assignment.getLevelId()).orElseThrow();
        var cycle = cycles.findById(level.getCycleId()).orElseThrow();
        if (!"ACTIVE".equals(cycle.getState()) || !"ACTIVE".equals(level.getState())
                || !task.getProcessInstanceId().equals(cycle.getProcessInstanceId())
                || !task.getAssignee().equals(assignment.getEmployeeId().toString())
                || !"PENDING".equals(assignment.getState()) || assignment.getTaskId() != null)
            throw new IllegalStateException("Task does not belong to the active grouped approval assignment.");
        assignment.setTaskId(task.getId()); assignments.saveAndFlush(assignment);
    }

    @Transactional
    public void taskCompleting(DelegateTask task) {
        var assignment = assignments.findByTaskId(task.getId()).orElseThrow();
        if (!Set.of("APPROVED", "REJECTED", "RETURNED").contains(assignment.getState()))
            throw new IllegalStateException("Grouped tasks must be decided through the request approval service.");
    }

    @Transactional(readOnly=true)
    public boolean ownsTask(String taskId) { return assignments.findByTaskId(taskId).isPresent(); }

    @Transactional
    public DecisionResult decide(String taskId, String action, String comment, Long actorId) {
        GroupedApprovalAssignment initial = assignments.findByTaskId(taskId).orElseThrow(() -> conflict("Approval task not found."));
        WorkflowRequest request = requests.findByIdForUpdate(initial.getRequestId()).orElseThrow();
        // Refresh avoids an entity loaded before waiting for the request lock retaining stale assignment state.
        assignments.flush();
        GroupedApprovalAssignment assignment = reload(initial.getId());
        if (!assignment.getEmployeeId().equals(actorId)) throw new org.springframework.security.access.AccessDeniedException("This task is not assigned to you.");
        String outcome = switch (action) {
            case "APPROVE" -> "APPROVED"; case "REJECT" -> "REJECTED"; case "RETURN" -> "RETURNED";
            default -> throw new IllegalArgumentException("Unknown approval action.");
        };
        String clean = comment == null || comment.isBlank() ? null : comment.trim();
        if (clean != null && clean.length() > 2000) throw new IllegalArgumentException("Comment must not exceed 2000 characters.");
        if (!"APPROVE".equals(action) && clean == null) throw new IllegalArgumentException("A rejection or return comment is required.");
        GroupedApprovalLevel level = levels.findById(assignment.getLevelId()).orElseThrow();
        GroupedApprovalCycle cycle = cycles.findById(level.getCycleId()).orElseThrow();
        if (!"PENDING".equals(assignment.getState())) {
            if (outcome.equals(assignment.getState()) && Objects.equals(clean, assignment.getComment()))
                return new DecisionResult(assignment.getState(), level.getState(), request.getStatus(), "Your decision was already recorded.");
            throw conflict("This level was already resolved or your decision was already recorded. Refresh to see the current outcome.");
        }
        if (request.getStatus() != RequestStatus.IN_APPROVAL || !"ACTIVE".equals(cycle.getState()) || !"ACTIVE".equals(level.getState()))
            throw conflict("This approval is no longer active. Refresh to see the current outcome.");
        Task task = tasks.createTaskQuery().taskId(taskId).singleResult();
        if (task == null || !actorId.toString().equals(task.getAssignee()) || !task.getProcessInstanceId().equals(cycle.getProcessInstanceId()))
            throw conflict("This approval task is no longer available.");
        assignment.setState(outcome); assignment.setComment(clean); assignment.setDecidedAt(LocalDateTime.now()); assignments.saveAndFlush(assignment);
        List<GroupedApprovalAssignment> members = assignments.findByLevelIdOrderById(level.getId());
        boolean resolved = resolves(level.getPolicy(), outcome, members.stream().allMatch(a -> "APPROVED".equals(a.getState())));
        event(cycle, assignment.getId(), actorId, outcome, clean);
        if (resolved) {
            level.setState(outcome); level.setResolvedAt(LocalDateTime.now()); level.setResolvedByAssignmentId(assignment.getId()); levels.saveAndFlush(level);
            supersedeMembers(level, "Resolved by " + assignment.getEmployeeName() + ": " + outcome);
            runtime.setVariable(task.getProcessInstanceId(), "groupLevelResolved", true);
        }
        // No shared decision/actor/comment variables: only authoritative level resolution crosses the process boundary.
        tasks.complete(taskId);
        requests.flush();
        String message = !resolved ? "Your approval was recorded; other approvals are still required."
                : request.getStatus() == RequestStatus.APPROVED ? "Request approved."
                : "APPROVED".equals(outcome) ? "Level approved; the next level is active."
                : "REJECTED".equals(outcome) ? "Request rejected." : "Request returned for correction.";
        return new DecisionResult(outcome, level.getState(), request.getStatus(), message);
    }

    public static boolean resolves(ApprovalPolicy policy, String outcome, boolean allApproved) {
        return !"APPROVED".equals(outcome) || policy == ApprovalPolicy.ANY_ONE || allApproved;
    }

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private GroupedApprovalAssignment reload(Long id) {
        GroupedApprovalAssignment value = assignments.findById(id).orElseThrow();
        entityManager.refresh(value);
        return value;
    }

    /** Executes only after the multi-instance scope closes, including cancellation of unresolved siblings. */
    @Transactional
    public void advance(DelegateExecution execution) {
        GroupedApprovalCycle cycle = cycle(execution);
        GroupedApprovalLevel level = currentLevel(cycle);
        if ("ACTIVE".equals(level.getState())) throw new IllegalStateException("A level must resolve before advancing.");
        List<GroupedApprovalLevel> route = levels.findByCycleIdOrderByOrdinal(cycle.getId());
        WorkflowRequest request = requests.findById(cycle.getRequestId()).orElseThrow();
        if ("APPROVED".equals(level.getState()) && cycle.getCurrentOrdinal() < route.size()) {
            cycle.setCurrentOrdinal(cycle.getCurrentOrdinal() + 1); cycles.saveAndFlush(cycle);
            request.setCurrentApprovalLevel(cycle.getCurrentOrdinal()); requests.save(request);
            execution.setVariable("groupMore", true); return;
        }
        cycle.setState(level.getState()); cycle.setCompletedAt(LocalDateTime.now()); cycles.saveAndFlush(cycle);
        closeRemaining(cycle, "Cycle ended: " + cycle.getState());
        switch (cycle.getState()) {
            case "APPROVED" -> { request.setStatus(RequestStatus.APPROVED); request.setApprovedAt(LocalDateTime.now()); handlers.get(request.getRequestType()).afterApproved(request); }
            case "REJECTED" -> { request.setStatus(RequestStatus.REJECTED); request.setRejectedAt(LocalDateTime.now()); handlers.get(request.getRequestType()).afterRejected(request); }
            case "RETURNED" -> { request.setStatus(RequestStatus.RETURNED_FOR_CORRECTION); request.setWorkflowInstanceId(null); }
            default -> throw new IllegalStateException("Unknown grouped outcome: " + cycle.getState());
        }
        requests.saveAndFlush(request);
        notice(cycle, null, null, cycle.getState());
        event(cycle, null, null, "CYCLE_" + cycle.getState(), "Grouped cycle completed");
        execution.setVariable("groupMore", false);
    }

    /** Caller must already own the request row lock. The existing Finance/cancel path retires Flowable. */
    @Transactional
    public void supersede(Long requestId, Long actorId, String reason) {
        for (var cycle : cycles.findByRequestIdOrderByCycleNo(requestId)) {
            if (!"ACTIVE".equals(cycle.getState())) continue;
            cycle.setState("SUPERSEDED"); cycle.setCompletedAt(LocalDateTime.now()); cycle.setResolutionReason(reason); cycles.save(cycle);
            closeRemaining(cycle, reason); event(cycle, null, actorId, "SUPERSEDED", reason);
        }
    }

    private void closeRemaining(GroupedApprovalCycle cycle, String reason) {
        for (var level : levels.findByCycleIdOrderByOrdinal(cycle.getId())) {
            if (Set.of("ACTIVE", "NOT_STARTED").contains(level.getState())) {
                level.setState("SUPERSEDED"); level.setResolvedAt(LocalDateTime.now()); levels.save(level);
            }
            supersedeMembers(level, reason);
        }
    }
    private void supersedeMembers(GroupedApprovalLevel level, String reason) {
        for (var member : assignments.findByLevelIdOrderById(level.getId())) {
            if (Set.of("PENDING", "NOT_STARTED").contains(member.getState())) {
                member.setState("SUPERSEDED"); member.setSupersessionReason(reason); assignments.save(member);
            }
        }
        assignments.flush();
    }
    private GroupedApprovalCycle cycle(DelegateExecution e) {
        var cycle = cycles.findById(((Number)e.getVariable("groupCycleId")).longValue()).orElseThrow();
        requests.findByIdForUpdate(cycle.getRequestId()).orElseThrow();
        if (!"ACTIVE".equals(cycle.getState())
                || !Objects.equals(cycle.getRequestId(), ((Number)e.getVariable("workflowRequestId")).longValue())
                || cycle.getProcessInstanceId() != null && !cycle.getProcessInstanceId().equals(e.getProcessInstanceId()))
            throw new IllegalStateException("This process does not own the active grouped approval cycle.");
        // Pin the instance during initial activation, before user-task listeners run.
        if (cycle.getProcessInstanceId() == null) { cycle.setProcessInstanceId(e.getProcessInstanceId()); cycles.saveAndFlush(cycle); }
        return cycle;
    }
    private GroupedApprovalLevel currentLevel(GroupedApprovalCycle c) {
        return levels.findByCycleIdOrderByOrdinal(c.getId()).stream().filter(l -> l.getOrdinal().equals(c.getCurrentOrdinal())).findFirst().orElseThrow();
    }
    private void event(GroupedApprovalCycle c, Long assignmentId, Long actorId, String type, String description) {
        GroupedApprovalEvent event = new GroupedApprovalEvent(); event.setRequestId(c.getRequestId()); event.setCycleId(c.getId());
        event.setAssignmentId(assignmentId); event.setActorId(actorId); event.setEventType(type); event.setDescription(description); events.save(event);
    }
    private void notice(GroupedApprovalCycle c, Long assignmentId, Long employeeId, String kind) {
        GroupedApprovalNotice notice = new GroupedApprovalNotice(); notice.setRequestId(c.getRequestId()); notice.setCycleId(c.getId());
        notice.setAssignmentId(assignmentId); notice.setEmployeeId(employeeId); notice.setKind(kind);
        notice.setDeduplicationKey(c.getId() + ":" + kind + ":" + (assignmentId == null ? "request" : assignmentId)); notices.save(notice);
    }

    @Transactional(readOnly=true)
    public boolean mayView(Long requestId, Long employeeId) {
        return assignments.findByRequestIdOrderById(requestId).stream()
                .anyMatch(a -> a.getEmployeeId().equals(employeeId) && a.getTaskId() != null);
    }

    @Transactional(readOnly=true)
    public boolean isPendingMember(Long requestId, Long employeeId) {
        return assignments.findByRequestIdOrderById(requestId).stream()
                .anyMatch(a -> a.getEmployeeId().equals(employeeId) && "PENDING".equals(a.getState()));
    }

    @Transactional(readOnly=true)
    public String inboxLabel(String taskId) {
        return assignments.findByTaskId(taskId).map(assignment -> {
            var level = levels.findById(assignment.getLevelId()).orElseThrow();
            var members = assignments.findByLevelIdOrderById(level.getId());
            long approved = members.stream().filter(a -> "APPROVED".equals(a.getState())).count();
            return "Level " + level.getOrdinal() + ": " + level.getName() + " — " + level.getPolicy() + " — "
                    + (level.getPolicy() == ApprovalPolicy.ALL ? approved + " of " + members.size() + " approvals received" : "Waiting for the first decision");
        }).orElse(null);
    }

    @Transactional(readOnly=true)
    public List<CycleView> history(Long requestId, Long viewerId, boolean includeComments) {
        return cycles.findByRequestIdOrderByCycleNo(requestId).stream().map(c -> new CycleView(c.getCycleNo(), c.getPathName(), c.getState(),
                levels.findByCycleIdOrderByOrdinal(c.getId()).stream().map(l -> {
                    var members = assignments.findByLevelIdOrderById(l.getId());
                    return new LevelView(l.getOrdinal(), l.getName(), l.getPolicy(), l.getState(),
                            members.stream().filter(a -> "APPROVED".equals(a.getState())).count(), members.size(),
                            members.stream().map(a -> new MemberView(a.getEmployeeName(), a.getState(), a.getDecidedAt(),
                                    includeComments || a.getEmployeeId().equals(viewerId) ? a.getComment() : null, a.getSupersessionReason(),
                                    a.getEmployeeId().equals(viewerId) && "PENDING".equals(a.getState()) ? a.getTaskId() : null)).toList());
                }).toList())).toList();
    }

    @Transactional(readOnly=true)
    public Map<String,Object> diagnostics() {
        configuration.requireAdministrator();
        List<Map<String,Object>> issues = new ArrayList<>();
        for (var cycle : cycles.findTop100ByStateOrderById("ACTIVE")) {
            long liveTasks = cycle.getProcessInstanceId() == null ? 0 : tasks.createTaskQuery().processInstanceId(cycle.getProcessInstanceId()).count();
            var level = currentLevel(cycle);
            long pending = assignments.findByLevelIdOrderById(level.getId()).stream().filter(a -> "PENDING".equals(a.getState())).count();
            if (liveTasks != pending || liveTasks == 0) issues.add(Map.of("requestId",cycle.getRequestId(),"cycleId",cycle.getId(),"pendingAssignments",pending,"liveTasks",liveTasks));
        }
        var failed = notices.findTop100ByDeliveredAtIsNullAndAttemptsGreaterThanOrderById(0).stream()
                .map(n -> Map.of("noticeId",n.getId(),"requestId",n.getRequestId(),"attempts",n.getAttempts(),"nextAttemptAt",n.getNextAttemptAt())).toList();
        return Map.of("activeCycleIssues",issues,"retryingNotifications",failed,"sampleLimit",100);
    }
    private ResponseStatusException conflict(String text) { return new ResponseStatusException(HttpStatus.CONFLICT, text); }
    public record DecisionResult(String assignmentOutcome, String levelOutcome, RequestStatus requestStatus, String message) {}
    public record CycleView(int cycleNo, String pathName, String state, List<LevelView> levels) {}
    public record LevelView(int ordinal, String name, ApprovalPolicy policy, String state, long approvedCount, int memberCount, List<MemberView> members) {}
    public record MemberView(String name, String state, LocalDateTime decidedAt, String comment, String supersessionReason, String taskId) {}
}
