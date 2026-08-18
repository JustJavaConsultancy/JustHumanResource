package com.justjava.humanresource.request.repository;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.enums.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WorkflowRequestRepository extends JpaRepository<WorkflowRequest,Long> {
 List<WorkflowRequest> findByRequesterEmployeeIdOrderByCreatedAtDesc(Long id);
 List<WorkflowRequest> findAllByOrderByCreatedAtDesc();
 List<WorkflowRequest> findByStatusOrderByCreatedAtDesc(RequestStatus status);
 List<WorkflowRequest> findByRequestTypeOrderByCreatedAtDesc(RequestType type);
}
