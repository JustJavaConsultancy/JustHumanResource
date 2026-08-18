package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollPeriodRepository
        extends JpaRepository<PayrollPeriod, Long> {

    /* ============================================================
       COMPANY-SCOPED LOOKUPS
       ============================================================ */

    Optional<PayrollPeriod> findByCompanyIdAndStatus(
            Long companyId,
            PayrollPeriodStatus status
    );

    boolean existsByCompanyIdAndStatus(
            Long companyId,
            PayrollPeriodStatus status
    );

    List<PayrollPeriod> findByCompanyIdOrderByPeriodStartDesc(
            Long companyId
    );

    /* ============================================================
       DATE-BASED LOOKUP (CRITICAL)
       ============================================================ */

    Optional<PayrollPeriod>
    findByCompanyIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
            Long companyId,
            LocalDate start,
            LocalDate end
    );

    /* ============================================================
       PERIOD DUPLICATE PROTECTION
       ============================================================ */

    boolean existsByCompanyIdAndPeriodStartAndPeriodEnd(
            Long companyId,
            LocalDate periodStart,
            LocalDate periodEnd
    );
    Optional<PayrollPeriod> findByCompanyIdAndStatusIn(
            Long companyId,
            List<PayrollPeriodStatus> statuses
    );

    /* ============================================================
       RETRO PROCESSING: closed periods in a date window
       ============================================================ */

    /**
     * Returns all CLOSED periods for a company whose {@code periodStart}
     * falls on or after {@code from} and whose {@code periodEnd} falls
     * strictly before {@code to}, ordered chronologically.
     *
     * <p>Used by retro-adjustment processing to enumerate every closed
     * period that lies between a salary change's effective date and the
     * current open period.</p>
     */
    @org.springframework.data.jpa.repository.Query("""
        SELECT p FROM PayrollPeriod p
        WHERE p.companyId = :companyId
          AND p.status = com.justjava.humanresource.payroll.enums.PayrollPeriodStatus.CLOSED
          AND p.periodStart >= :from
          AND p.periodEnd   <  :to
        ORDER BY p.periodStart ASC
    """)
    List<PayrollPeriod> findClosedPeriodsBetween(
            @org.springframework.data.repository.query.Param("companyId") Long companyId,
            @org.springframework.data.repository.query.Param("from")      LocalDate from,
            @org.springframework.data.repository.query.Param("to")        LocalDate to
    );
}