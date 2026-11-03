package com.justjava.humanresource.request.grouped;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;

public interface GroupedApprovalCycleRepository extends JpaRepository<GroupedApprovalCycle, Long> {

    List<GroupedApprovalCycle> findByRequestIdOrderByCycleNo(Long id);
    Optional<GroupedApprovalCycle> findFirstByRequestIdOrderByCycleNoDesc(Long id);
    List<GroupedApprovalCycle> findTop100ByStateOrderById(String state);
}
