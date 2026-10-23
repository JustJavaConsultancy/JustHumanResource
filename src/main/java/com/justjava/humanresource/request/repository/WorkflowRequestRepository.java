package com.justjava.humanresource.request.repository;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.enums.*;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface WorkflowRequestRepository extends JpaRepository<WorkflowRequest,Long> {
 List<WorkflowRequest> findByRequesterEmployeeIdOrderByCreatedAtDesc(Long id);
 List<WorkflowRequest> findAllByOrderByCreatedAtDesc();
 List<WorkflowRequest> findByStatusOrderByCreatedAtDesc(RequestStatus status);
 List<WorkflowRequest> findByRequestTypeOrderByCreatedAtDesc(RequestType type);


 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
 @Query("select r from WorkflowRequest r where r.id = :id")
 Optional<WorkflowRequest> findByIdForUpdate(@Param("id") Long id);

 /** Requests currently handed off to this employee by Finance (for the recipient's pending items). */
 List<WorkflowRequest> findByFinanceOverrideRecipientIdOrderByCreatedAtDesc(Long recipientEmployeeId);
}