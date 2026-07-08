package com.justjava.humanresource.request.repository;
import com.justjava.humanresource.request.entity.WorkflowRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WorkflowRequestItemRepository extends JpaRepository<WorkflowRequestItem,Long> { List<WorkflowRequestItem> findByWorkflowRequestIdOrderById(Long id); long countByWorkflowRequestId(Long id); }
