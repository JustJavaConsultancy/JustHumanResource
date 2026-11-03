package com.justjava.humanresource.request.grouped;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;

public interface GroupedApprovalVersionRepository extends JpaRepository<GroupedApprovalVersion, Long> {

    List<GroupedApprovalVersion> findAllByOrderByCreatedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from GroupedApprovalVersion v where v.id = :id")
    Optional<GroupedApprovalVersion> lockById(@Param("id") Long id);
}
