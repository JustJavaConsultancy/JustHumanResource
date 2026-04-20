package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.entity.PayGroupTaxRelief;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PayGroupTaxReliefRepository
        extends JpaRepository<PayGroupTaxRelief, Long> {

    @Query("""
        SELECT p
        FROM PayGroupTaxRelief p
        WHERE p.payGroup.id = :payGroupId
          AND p.status = :status
          AND (p.effectiveFrom IS NULL OR p.effectiveFrom <= :date)
          AND (p.effectiveTo IS NULL OR p.effectiveTo >= :date)
    """)
    List<PayGroupTaxRelief> findActiveReliefs(
            Long payGroupId,
            LocalDate date,
            RecordStatus status
    );

    /* ============================================================
       ALL ASSIGNED TAX RELIEFS (INCLUDING FUTURE EFFECTIVE DATES)
       For UI display purposes - shows all assignments regardless of date
       ============================================================ */

    @Query("""
        SELECT p
        FROM PayGroupTaxRelief p
        WHERE p.payGroup.id = :payGroupId
          AND p.status = :status
          AND (p.effectiveTo IS NULL OR p.effectiveTo >= :date)
    """)
    List<PayGroupTaxRelief> findAllAssignedReliefs(
            Long payGroupId,
            LocalDate date,
            RecordStatus status
    );
}