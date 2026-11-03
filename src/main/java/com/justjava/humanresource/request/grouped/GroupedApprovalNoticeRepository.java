package com.justjava.humanresource.request.grouped;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;

public interface GroupedApprovalNoticeRepository extends JpaRepository<GroupedApprovalNotice, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<GroupedApprovalNotice> findTop20ByDeliveredAtIsNullAndNextAttemptAtBeforeOrderById(LocalDateTime now);
    List<GroupedApprovalNotice> findTop100ByDeliveredAtIsNullAndAttemptsGreaterThanOrderById(int attempts);
}
