package com.justjava.humanresource.payroll.statutory.repositories;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.statutory.entity.PensionScheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PensionSchemeRepository
        extends JpaRepository<PensionScheme, Long> {

    /* ============================================================
       EXISTING METHOD (Backward Compatibility)
       ============================================================ */

    List<PensionScheme> findByStatus(RecordStatus status);

    /* ============================================================
       UNIFIED EFFECTIVE-DATED QUERY
       ============================================================ */

    @Query("""
        SELECT p FROM PensionScheme p
        WHERE p.status = :status
          AND p.effectiveFrom <= :date
          AND (p.effectiveTo IS NULL OR p.effectiveTo >= :date)
        ORDER BY p.effectiveFrom DESC
    """)
    List<PensionScheme> findEffectiveSchemes(
            @Param("date") LocalDate date,
            @Param("status") RecordStatus status
    );

    /* ============================================================
       SAFE SINGLE-SCHEME RESOLUTION
       ============================================================ */

    default Optional<PensionScheme> findEffectiveScheme(
            LocalDate date,
            RecordStatus status
    ) {
        List<PensionScheme> schemes =
                findEffectiveSchemes(date, status);

        if (schemes.isEmpty()) {
            return Optional.empty();
        }

        if (schemes.size() > 1) {
            throw new IllegalStateException(
                    "Multiple active PensionSchemes found for date: " + date
            );
        }

        return Optional.of(schemes.get(0));
    }
}
