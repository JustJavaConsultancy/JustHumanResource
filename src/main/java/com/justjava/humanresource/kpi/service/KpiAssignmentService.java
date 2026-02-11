package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.kpi.entity.KpiAssignment;
import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.repositories.KpiAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class KpiAssignmentService {

    private final KpiAssignmentRepository repository;

    public KpiAssignment assignToEmployee(
            KpiDefinition kpi,
            Employee employee,
            BigDecimal weight
    ) {
        return repository.save(
                KpiAssignment.builder()
                        .kpi(kpi)
                        .employee(employee)
                        .weight(weight)
                        .mandatory(true)
                        .build()
        );
    }
}

