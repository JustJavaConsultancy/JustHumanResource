package com.justjava.humanresource.communication.repository;

import com.justjava.humanresource.communication.entity.HrBroadcastReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HrBroadcastReceiptRepository extends JpaRepository<HrBroadcastReceipt, Long> {

    Optional<HrBroadcastReceipt> findByBroadcast_IdAndEmployee_Id(Long broadcastId, Long employeeId);

    long countByBroadcast_IdAndDeliveredAtIsNotNull(Long broadcastId);

    long countByBroadcast_IdAndReadAtIsNotNull(Long broadcastId);

    @Query("""
            SELECT COUNT(r) > 0
            FROM HrBroadcastReceipt r
            WHERE r.broadcast.id = :broadcastId
              AND r.employee.id = :employeeId
              AND r.readAt IS NOT NULL
            """)
    boolean isReadByEmployee(@Param("broadcastId") Long broadcastId, @Param("employeeId") Long employeeId);
}
