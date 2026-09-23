package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.repositories.KpiDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class KpiDefinitionService {

    private final KpiDefinitionRepository repository;



    public KpiDefinition create(KpiDefinition kpi) {
        kpi.setActive(true);
        kpi.setParentDefinition(resolveParent(kpi, null));
        return repository.save(kpi);
    }

    public KpiDefinition update(Long id, KpiDefinition kpi) {
        KpiDefinition existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("KPI not found: " + id));

        existing.setCode(kpi.getCode());
        existing.setName(kpi.getName());
        existing.setDescription(kpi.getDescription());
        existing.setCategory(kpi.getCategory());
        existing.setTargetValue(kpi.getTargetValue());
        existing.setUnit(kpi.getUnit());
        existing.setHierarchyRole(kpi.getHierarchyRole());
        existing.setFrequency(kpi.getFrequency());
        existing.setImpactSalary(kpi.isImpactSalary());
        existing.setScoringRubric(kpi.getScoringRubric());
        existing.setParentDefinition(resolveParent(kpi, id));


        return repository.save(existing);
    }
    public List<KpiDefinition> getAll() {
        return repository.findAllWithChildren();
    }

    public boolean hasChildren(Long id) {
        return repository.existsByParentDefinition_Id(id);
    }

    private KpiDefinition resolveParent(KpiDefinition kpi, Long currentId) {
        if (kpi.getParentDefinition() == null || kpi.getParentDefinition().getId() == null) {
            return null;
        }

        Long parentId = kpi.getParentDefinition().getId();
        if (currentId != null && currentId.equals(parentId)) {
            throw new IllegalArgumentException("A KPI definition cannot be its own parent.");
        }

        KpiDefinition parent = repository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent KPI not found: " + parentId));

        validateNoCircularParent(parent, currentId);
        return parent;
    }

    private void validateNoCircularParent(KpiDefinition parent, Long currentId) {
        KpiDefinition cursor = parent;
        while (cursor != null) {
            if (currentId != null && currentId.equals(cursor.getId())) {
                throw new IllegalArgumentException("KPI parent hierarchy cannot be circular.");
            }
            cursor = cursor.getParentDefinition();
        }
    }
}

