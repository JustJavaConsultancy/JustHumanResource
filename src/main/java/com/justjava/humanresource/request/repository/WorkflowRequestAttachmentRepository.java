package com.justjava.humanresource.request.repository;
import com.justjava.humanresource.request.entity.WorkflowRequestAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WorkflowRequestAttachmentRepository extends JpaRepository<WorkflowRequestAttachment,Long> { List<WorkflowRequestAttachment> findByWorkflowRequestIdOrderByCreatedAt(Long id); long countByWorkflowRequestId(Long id); }
