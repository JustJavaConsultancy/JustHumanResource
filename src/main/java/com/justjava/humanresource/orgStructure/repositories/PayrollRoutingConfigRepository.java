package com.justjava.humanresource.orgStructure.repositories;

import com.justjava.humanresource.orgStructure.entity.PayrollRoutingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;
public interface PayrollRoutingConfigRepository extends JpaRepository<PayrollRoutingConfig, Long> {

    @Query("""
        SELECT p FROM PayrollRoutingConfig p
        WHERE p.department.id = :deptId
          AND :date BETWEEN p.effectiveFrom
          AND COALESCE(p.effectiveTo, :date)
    """)
    Optional<PayrollRoutingConfig> findEffectiveConfig(Long deptId, LocalDate date);
}
