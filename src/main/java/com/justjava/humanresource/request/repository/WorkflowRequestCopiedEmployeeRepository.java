package com.justjava.humanresource.request.repository;

import com.justjava.humanresource.request.entity.WorkflowRequestCopiedEmployee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WorkflowRequestCopiedEmployeeRepository
        extends JpaRepository<WorkflowRequestCopiedEmployee, Long> {

    List<WorkflowRequestCopiedEmployee> findByWorkflowRequestIdOrderByCreatedAtAscIdAsc(Long workflowRequestId);

    List<WorkflowRequestCopiedEmployee> findByEmployeeIdIn(Collection<Long> employeeIds);

    boolean existsByWorkflowRequestIdAndEmployeeId(Long workflowRequestId, Long employeeId);

    void deleteByWorkflowRequestId(Long workflowRequestId);
}
