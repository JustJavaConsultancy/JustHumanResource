package com.justjava.humanresource.workflow.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;
@Data
@Builder
public class FlowableTaskDTO {

    private String taskId;
    private String taskName;
    private String taskDefinitionKey;
    private String processInstanceId;
    private String processDefinitionKey;
    private String businessKey;
    private String assignee;
    private LocalDateTime createdTime;
    private Map<String, Object> variables;
}