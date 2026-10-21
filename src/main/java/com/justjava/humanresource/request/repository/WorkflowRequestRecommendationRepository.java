package com.justjava.humanresource.request.repository;

import com.justjava.humanresource.request.entity.WorkflowRequestRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRequestRecommendationRepository
        extends JpaRepository<WorkflowRequestRecommendation, Long> {

    List<WorkflowRequestRecommendation> findByWorkflowRequestIdOrderByCreatedAtAscIdAsc(Long workflowRequestId);
}
