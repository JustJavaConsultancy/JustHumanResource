package com.justjava.humanresource.payroll.statutory.repositories;


import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.statutory.entity.PensionScheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PensionSchemeRepository
        extends JpaRepository<PensionScheme, Long> {

    /* ============================================================
       EXISTING METHOD (UNCHANGED)
       ============================================================ */

    List<PensionScheme> findByStatus(RecordStatus status);

    /* ============================================================
       EFFECTIVE-DATED SCHEMES (BOUNDED RANGE)
       ============================================================ */

    List<PensionScheme>
    findByEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualAndStatus(
            LocalDate date1,
            LocalDate date2,
            RecordStatus status
    );

    /* ============================================================
       OPEN-ENDED SCHEMES (effectiveTo IS NULL)
       ============================================================ */

    List<PensionScheme>
    findByEffectiveFromLessThanEqualAndEffectiveToIsNullAndStatus(
            LocalDate date,
            RecordStatus status
    );
}
