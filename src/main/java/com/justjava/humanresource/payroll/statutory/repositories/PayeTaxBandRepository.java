package com.justjava.humanresource.payroll.statutory.repositories;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.statutory.entity.PayeTaxBand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PayeTaxBandRepository extends JpaRepository<PayeTaxBand, Long> {

    /*
     * Fetch active tax bands for a specific payroll date.
     * Supports retro payroll.
     */
    List<PayeTaxBand> findByEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqualAndStatusOrderByLowerBoundAsc(
            LocalDate date1,
            LocalDate date2,
            RecordStatus status
    );

    /*
     * Handles open-ended effectiveTo (null).
     */
    List<PayeTaxBand> findByEffectiveFromLessThanEqualAndEffectiveToIsNullAndStatusOrderByLowerBoundAsc(
            LocalDate date,
            RecordStatus status
    );

    /*
     * Optional: regime-specific query
     */
    List<PayeTaxBand> findByRegimeCodeAndEffectiveFromLessThanEqualAndStatusOrderByLowerBoundAsc(
            String regimeCode,
            LocalDate date,
            RecordStatus status
    );
}
