package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.KpiDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KpiDefinitionRepository
        extends JpaRepository<KpiDefinition, Long> {

    Optional<KpiDefinition> findByCode(String code);
}

