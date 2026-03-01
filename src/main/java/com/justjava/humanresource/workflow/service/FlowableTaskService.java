package com.justjava.humanresource.workflow.service;

import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;
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