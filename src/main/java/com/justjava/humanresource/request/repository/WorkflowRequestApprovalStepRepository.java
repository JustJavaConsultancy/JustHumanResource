package com.justjava.humanresource.request.repository;
import com.justjava.humanresource.request.entity.WorkflowRequestApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WorkflowRequestApprovalStepRepository extends JpaRepository<WorkflowRequestApprovalStep,Long> { List<WorkflowRequestApprovalStep> findByWorkflowRequestIdOrderBySequenceNo(Long id); Optional<WorkflowRequestApprovalStep> findByFlowableTaskId(String taskId); Optional<WorkflowRequestApprovalStep> findByWorkflowRequestIdAndSequenceNo(Long id,Integer sequenceNo); }
