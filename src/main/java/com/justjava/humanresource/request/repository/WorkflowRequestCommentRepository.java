package com.justjava.humanresource.request.repository;
import com.justjava.humanresource.request.entity.WorkflowRequestComment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WorkflowRequestCommentRepository extends JpaRepository<WorkflowRequestComment,Long> { List<WorkflowRequestComment> findByWorkflowRequestIdOrderByCreatedAt(Long id); }
