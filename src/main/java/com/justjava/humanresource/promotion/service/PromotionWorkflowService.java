package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.promotion.dto.PromotionDecisionCommand;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;

import java.util.List;

public interface PromotionWorkflowService {

    String startApproval(PromotionRequest request);

    List<FlowableTaskDTO> getMyTasks();

    void approve(String taskId, PromotionDecisionCommand command);

    void reject(String taskId, PromotionDecisionCommand command);

    void returnForCorrection(String taskId, PromotionDecisionCommand command);

    void cancelApproval(PromotionRequest request, String reason);

    String activeTaskId(PromotionRequest request);

    void assignActiveTask(PromotionRequest request, Long approverId);

    void assignActiveTaskToGroup(PromotionRequest request, String approverGroup);
}
