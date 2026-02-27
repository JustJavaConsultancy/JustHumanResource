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
}