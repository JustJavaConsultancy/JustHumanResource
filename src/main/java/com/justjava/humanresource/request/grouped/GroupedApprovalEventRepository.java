package com.justjava.humanresource.request.grouped;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;

public interface GroupedApprovalEventRepository extends JpaRepository<GroupedApprovalEvent, Long> {

    List<GroupedApprovalEvent> findByRequestIdOrderById(Long id);
}
