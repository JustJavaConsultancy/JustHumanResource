package com.justjava.humanresource.request.grouped;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;

public interface GroupedApprovalAssignmentRepository extends JpaRepository<GroupedApprovalAssignment, Long> {

    List<GroupedApprovalAssignment> findByLevelIdOrderById(Long id);
    List<GroupedApprovalAssignment> findByRequestIdOrderById(Long id);
    Optional<GroupedApprovalAssignment> findByTaskId(String taskId);
}
