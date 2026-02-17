package com.justjava.humanresource.hr.repository;


import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.EmployeePositionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeePositionHistoryRepository
        extends JpaRepository<EmployeePositionHistory, Long> {

    @Query("""
        SELECT eph FROM EmployeePositionHistory eph
        WHERE eph.employee.id = :employeeId
          AND eph.status = :status
          AND eph.effectiveFrom <= :date
          AND (eph.effectiveTo IS NULL OR eph.effectiveTo >= :date)
        ORDER BY eph.effectiveFrom DESC
    """)
    List<EmployeePositionHistory> findEffectivePosition(
            @Param("employeeId") Long employeeId,
            @Param("date") LocalDate date,
            @Param("status") RecordStatus status
    );

    default Optional<EmployeePositionHistory> resolvePosition(
            Long employeeId,
            LocalDate date,
            RecordStatus status
    ) {
        List<EmployeePositionHistory> list =
                findEffectivePosition(employeeId, date, status);

        if (list.isEmpty()) return Optional.empty();

        if (list.size() > 1) {
            throw new IllegalStateException(
                    "Multiple active positions found for employee "
                            + employeeId + " on " + date
            );
        }

        return Optional.of(list.get(0));
    }
    @Query("""
    SELECT eph FROM EmployeePositionHistory eph
    WHERE eph.payGroup.id = :payGroupId
      AND eph.status = :status
      AND eph.effectiveFrom <= :date
      AND (eph.effectiveTo IS NULL OR eph.effectiveTo >= :date)
""")
    List<EmployeePositionHistory> findEmployeesByPayGroupAndDate(
            @Param("payGroupId") Long payGroupId,
            @Param("date") LocalDate date,
            @Param("status") RecordStatus status
    );
    boolean existsByEmployee_IdAndCurrentTrue(Long employeeId);


    Optional<EmployeePositionHistory> findByEmployee_IdAndCurrentTrue(Long employeeId);

    List<EmployeePositionHistory> findByCurrentTrueAndEffectiveToIsNull();
    long countByCurrentTrue();

}
