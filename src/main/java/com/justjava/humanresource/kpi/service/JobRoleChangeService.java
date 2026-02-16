package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.kpi.entity.KpiAssignment;
import com.justjava.humanresource.kpi.repositories.KpiAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class JobRoleChangeService {

    private final EmployeeRepository employeeRepository;
    private final KpiAssignmentRepository kpiAssignmentRepository;
    private final KpiAssignmentService kpiAssignmentService;
    private final RuntimeService runtimeService;

    public void changeJobRole(Long employeeId, Long newRoleId) {

        Employee employee =
                employeeRepository.findById(employeeId).orElseThrow();

        // 1. Close existing KPI assignments
        List<KpiAssignment> activeKpis =
                kpiAssignmentRepository.findByEmployee_Id(employeeId);

        activeKpis.stream()
                .filter(KpiAssignment::isActive)
                .forEach(kpi -> {
                    kpi.setActive(false);
                    kpi.setValidTo(LocalDate.now());
                });

        kpiAssignmentRepository.saveAll(activeKpis);

        // 2. Change role
        employee.setJobStep(JobStep.builder().id(newRoleId).build());
        employeeRepository.save(employee);

        // 3. Trigger Flowable KPI re-assignment
        runtimeService.startProcessInstanceByKey(
                "kpiReassignmentProcess",
                Map.of("employeeId", employeeId)
        );
    }
}
