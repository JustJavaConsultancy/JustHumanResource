package com.justjava.humanresource.request.grouped;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;

public interface GroupedApprovalLevelRepository extends JpaRepository<GroupedApprovalLevel, Long> {

    List<GroupedApprovalLevel> findByCycleIdOrderByOrdinal(Long id);
}
