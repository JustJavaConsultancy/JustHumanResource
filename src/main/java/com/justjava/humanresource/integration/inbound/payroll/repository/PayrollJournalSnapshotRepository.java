package com.justjava.humanresource.integration.inbound.payroll.repository;

import com.justjava.humanresource.integration.inbound.payroll.entity.PayrollJournalSnapshot;
import com.justjava.humanresource.integration.inbound.payroll.enums.SnapshotStatus;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;




public interface PayrollJournalSnapshotRepository extends JpaRepository<PayrollJournalSnapshot, Long> {
    Optional<PayrollJournalSnapshot> findByJournalReferenceAndStatusNot(String journalReference, SnapshotStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PayrollJournalSnapshot s WHERE s.journalReference = :reference")
    Optional<PayrollJournalSnapshot> findByJournalReferenceForUpdate(@Param("reference") String reference);
    Optional<PayrollJournalSnapshot> findByPayrollPeriodIdAndStatusNot(Long payrollPeriodId, SnapshotStatus status);
    List<PayrollJournalSnapshot> findTop20ByCompanyIdAndStatusNotOrderByPeriodEndDesc(
            Long companyId, SnapshotStatus status);

    @Modifying
    @Query("""
            UPDATE PayrollJournalSnapshot s
            SET s.status = com.justjava.humanresource.integration.inbound.payroll.enums.SnapshotStatus.PUBLISHED,
                s.statusChangedAt = :publishedAt
            WHERE s.journalReference = :reference
              AND s.status = com.justjava.humanresource.integration.inbound.payroll.enums.SnapshotStatus.PENDING_PUBLISH
            """)
    int markPublished(@Param("reference") String reference, @Param("publishedAt") LocalDateTime publishedAt);
}
