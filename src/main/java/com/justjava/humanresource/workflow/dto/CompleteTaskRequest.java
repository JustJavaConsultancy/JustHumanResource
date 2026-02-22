package com.justjava.humanresource.workflow.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CompleteTaskRequest {

    private String taskId;
    private Map<String, Object> variables;
}