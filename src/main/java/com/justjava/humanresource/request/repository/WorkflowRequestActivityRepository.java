package com.justjava.humanresource.request.repository;
import com.justjava.humanresource.request.entity.WorkflowRequestActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WorkflowRequestActivityRepository extends JpaRepository<WorkflowRequestActivity,Long> { List<WorkflowRequestActivity> findByWorkflowRequestIdOrderByCreatedAt(Long id); }
