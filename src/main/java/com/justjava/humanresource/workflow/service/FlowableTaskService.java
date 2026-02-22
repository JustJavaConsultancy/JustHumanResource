package com.justjava.humanresource.workflow.service;

import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
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

    /* =====================================================
       COMPLETE TASK
       ===================================================== */

    @Transactional
    public void completeTask(
            String taskId,
            Map<String, Object> variables
    ) {
        taskService.complete(taskId, variables);
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