package com.justjava.humanresource.kpi.delegate;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.kpi.entity.KpiAssignment;
import com.justjava.humanresource.kpi.repositories.KpiAssignmentRepository;
import com.justjava.humanresource.kpi.repositories.KpiDefinitionRepository;
import com.justjava.humanresource.kpi.service.KpiAssignmentService;
import com.justjava.humanresource.onboarding.entity.EmployeeOnboarding;
import com.justjava.humanresource.onboarding.enums.OnboardingStatus;
import com.justjava.humanresource.onboarding.repositories.EmployeeOnboardingRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component("assignEmployeeKpisDelegate")
@RequiredArgsConstructor
public class AssignEmployeeKpisDelegate implements JavaDelegate {

    private final EmployeeRepository employeeRepository;
    private final KpiAssignmentService kpiAssignmentService;
    private final KpiAssignmentRepository assignmentRepository;
    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final EmployeeOnboardingRepository onboardingRepository;

    @Override
    @Transactional
    public void execute(DelegateExecution execution) {

        Long employeeId = (Long) execution.getVariable("employeeId");

        Employee employee =
                employeeRepository.findById(employeeId).orElseThrow();

        // Load role-based KPIs
        List<KpiAssignment> roleKpis =
                assignmentRepository.findByJobStep_Id(employee.getJobStep().getId());

        for (KpiAssignment roleAssignment : roleKpis) {
            kpiAssignmentService.assignToEmployee(
                    roleAssignment.getKpi(),
                    employee,
                    roleAssignment.getWeight()
            );
        }

        employee.setKpiEnabled(true);
        employeeRepository.save(employee);

        Long onboardingId = (Long) execution.getVariable("onboardingId");

        EmployeeOnboarding onboarding =
                onboardingRepository.findById(onboardingId)
                        .orElseThrow();

        onboarding.setStatus(OnboardingStatus.KPI_ASSIGNED);
        onboardingRepository.save(onboarding);
    }
}
