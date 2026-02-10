package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.repositories.KpiDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KpiDefinitionService {

    private final KpiDefinitionRepository repository;

    public KpiDefinition create(KpiDefinition kpi) {
        kpi.setActive(true);
        return repository.save(kpi);
    }
}

