package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.KpiDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface KpiDefinitionRepository
        extends JpaRepository<KpiDefinition, Long> {

    Optional<KpiDefinition> findByCode(String code);

    List<KpiDefinition> findByParentDefinition_Id(Long parentId);

    boolean existsByParentDefinition_Id(Long parentId);

    @Query("SELECT DISTINCT k FROM KpiDefinition k LEFT JOIN FETCH k.children")
    List<KpiDefinition> findAllWithChildren();
}

