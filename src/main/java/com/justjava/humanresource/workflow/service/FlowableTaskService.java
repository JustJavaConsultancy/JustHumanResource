package com.justjava.humanresource.workflow.service;

import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlowableTaskService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    @Autowired
    private HistoryService historyService;

    /* =====================================================
       GET TASKS BY ASSIGNEE
       ===================================================== */

    public List<FlowableTaskDTO> getTasksForAssignee(
            String assignee,
            String processDefinitionKey
    ) {

        List<Task> tasks = taskService.createTaskQuery()
                .taskAssignee(assignee)
                .processDefinitionKey(processDefinitionKey)
                .active()
                .orderByTaskCreateTime()
                .desc()
                .list();

        return tasks.stream()
                .map(this::mapToDto)
                .toList();
    }
    public List<FlowableTaskDTO> getTasksByTaskDefinition(
            String taskDefinitionKey,
            String processDefinitionKey
    ) {

        List<Task> tasks = taskService.createTaskQuery()
                .taskDefinitionKey(taskDefinitionKey)
                .processDefinitionKey(processDefinitionKey)
                .active()
                .orderByTaskCreateTime()
                .desc()
                .list();

        return tasks.stream()
                .map(this::mapToDto)
                .toList();
    }

    /**
     * Read-only lookup of one active task by id, built exactly like the
     * list on the lock approval page (same createdTime and variables).
     * Returns null if the task does not exist or is no longer active.
     */
    public FlowableTaskDTO getActiveTaskById(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .active()
                .singleResult();
        return task == null ? null : mapToDto(task);
    }

    public List<FlowableTaskDTO> getTasksByProcessDefinition(
            String processDefinitionKey
    ) {
        List<Task> tasks = taskService.createTaskQuery()
                .processDefinitionKey(processDefinitionKey)
                .active()
                .orderByTaskCreateTime()
                .desc()
                .list();

        return tasks.stream()
                .map(this::mapToDto)
                .toList();
    }

    public List<FlowableTaskDTO> getTasksForAssigneeAcrossProcesses(
            String assignee,
            Collection<String> processDefinitionKeys
    ) {
        if (processDefinitionKeys == null || processDefinitionKeys.isEmpty()) {
            return List.of();
        }

        return processDefinitionKeys.stream()
                .distinct()
                .flatMap(processDefinitionKey -> getTasksForAssignee(assignee, processDefinitionKey).stream())
                .sorted((left, right) -> right.getCreatedTime().compareTo(left.getCreatedTime()))
                .toList();
    }
    /* =====================================================
       CANDIDATE-GROUP TASKS (added for loan module)
       Candidate queries only return UNASSIGNED tasks in Flowable.
       ===================================================== */

    public List<FlowableTaskDTO> getTasksForCandidateGroup(
            String candidateGroup,
            String processDefinitionKey
    ) {
        return getTasksForCandidateGroups(List.of(candidateGroup), processDefinitionKey);
    }

    public List<FlowableTaskDTO> getTasksForCandidateGroups(
            Collection<String> candidateGroups,
            String processDefinitionKey
    ) {
        if (candidateGroups == null || candidateGroups.isEmpty()) {
            return List.of();
        }

        return taskService.createTaskQuery()
                .taskCandidateGroupIn(List.copyOf(candidateGroups))
                .processDefinitionKey(processDefinitionKey)
                .active()
                .orderByTaskCreateTime()
                .desc()
                .list()
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    /** True if the task has the group as a candidate group (works even after the task is claimed). */
    public boolean isTaskCandidateForGroup(String taskId, String candidateGroup) {
        return isTaskCandidateForAnyGroup(taskId, List.of(candidateGroup));
    }

    public boolean isTaskCandidateForAnyGroup(String taskId, Collection<String> candidateGroups) {
        if (taskId == null || candidateGroups == null || candidateGroups.isEmpty()) {
            return false;
        }
        List<IdentityLink> links = taskService.getIdentityLinksForTask(taskId);
        return links.stream()
                .filter(l -> IdentityLinkType.CANDIDATE.equals(l.getType()))
                .anyMatch(l -> l.getGroupId() != null && candidateGroups.contains(l.getGroupId()));
    }

    /**
     * Claims a candidate task for a user. Completing a candidate task does not
     * require a claim in Flowable; use this only if the task should be locked to one user.
     */
    @Transactional
    public void claimTask(String taskId, String userId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalStateException("Task not found: " + taskId);
        }
        if (task.getAssignee() != null && !task.getAssignee().equals(userId)) {
            throw new IllegalStateException("Task is already claimed by another user: " + taskId);
        }
        if (task.getAssignee() == null) {
            taskService.claim(taskId, userId);
        }
    }

    //    GET COMPLETED PROCESS INSTANCES
    public List<HistoricProcessInstance> getCompletedProcessInstancesForAssignee(
            String processDefinitionKey
    ) {
        return historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey(processDefinitionKey)
                .includeProcessVariables()
                .finished()
                .orderByProcessInstanceEndTime()
                .desc()
                .list();
    }
    //    GET COMPLETED PROCESS INSTANCES
    public List<HistoricTaskInstance> getCompletedTasksForAssignee(
            String taskDefinitionKey, String assignee
    ) {
        return historyService.createHistoricTaskInstanceQuery()
                .taskDefinitionKey(taskDefinitionKey)
                .taskAssignee(assignee)
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .list();
    }
    public List<HistoricTaskInstance> getCompletedTaskstaskDefinitionKey(
            String taskDefinitionKey
    ) {
        return historyService.createHistoricTaskInstanceQuery()
                .taskDefinitionKey(taskDefinitionKey)
                .finished()
                .processVariableValueEquals("approved", true)
                .includeProcessVariables()
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .list();
    }

    /* =====================================================
       COMPLETE TASK
       ===================================================== */

    @Transactional
    public void completeTask(
            String taskId,
            Map<String, Object> variables
    ) {

        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

        if (task == null) {
            throw new IllegalStateException("Task not found: " + taskId);
        }

        if (variables != null && !variables.isEmpty()) {

            // Set variables at PROCESS INSTANCE level
            runtimeService.setVariables(
                    task.getProcessInstanceId(),
                    variables
            );
        }

        // Complete task without passing variables again
        taskService.complete(taskId);
    }

    public boolean isTaskAssignedTo(String taskId, String assignee) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .taskAssignee(assignee)
                .singleResult();
        return task != null;
    }

    /* =====================================================
       INTERNAL MAPPER
       ===================================================== */

    private FlowableTaskDTO mapToDto(Task task) {

        String businessKey = runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult()
                .getBusinessKey();

        return FlowableTaskDTO.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .taskDefinitionKey(task.getTaskDefinitionKey())
                .processInstanceId(task.getProcessInstanceId())
                .processDefinitionKey(task.getProcessDefinitionId())
                .businessKey(businessKey)
                .assignee(task.getAssignee())
                .createdTime(
                        task.getCreateTime()
                                .toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime()
                )
                .variables(taskService.getVariables(task.getId()))
                .build();
    }
}