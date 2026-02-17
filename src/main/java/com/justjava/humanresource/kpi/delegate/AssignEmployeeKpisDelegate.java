package com.justjava.humanresource.kpi.delegate;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.kpi.entity.KpiAssignment;
import com.justjava.humanresource.kpi.repositories.KpiAssignmentRepository;
import com.justjava.humanresource.kpi.service.KpiAssignmentService;
import com.justjava.humanresource.onboarding.entity.EmployeeOnboarding;
import com.justjava.humanresource.onboarding.enums.OnboardingStatus;
import com.justjava.humanresource.onboarding.repositories.EmployeeOnboardingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component("assignEmployeeKpisDelegate")
@RequiredArgsConstructor
public class AssignEmployeeKpisDelegate implements JavaDelegate {

    private final EmployeeRepository employeeRepository;
    private final KpiAssignmentRepository assignmentRepository;
    private final KpiAssignmentService kpiAssignmentService;
    private final EmployeeOnboardingRepository onboardingRepository;

    @Override
    @Transactional
    public void execute(DelegateExecution execution) {

        log.info("Starting KPI assignment delegate...");

        Long employeeId = (Long) execution.getVariable("employeeId");

        if (employeeId == null) {
            throw new IllegalStateException("Missing process variable: employeeId");
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new IllegalStateException("Employee not found: " + employeeId));

        if (employee.getJobStep() == null) {
            throw new IllegalStateException("Employee has no JobStep assigned.");
        }

        Long jobStepId = employee.getJobStep().getId();

        // 1️⃣ Fetch ACTIVE role-based KPI assignments
        List<KpiAssignment> roleAssignments =
                assignmentRepository.findByJobStep_IdAndActiveTrue(jobStepId);

        log.info("Found {} role-based KPI assignments for JobStep {}",
                roleAssignments.size(), jobStepId);

        // 2️⃣ Assign to employee (idempotent safe)
        for (KpiAssignment roleAssignment : roleAssignments) {

            Long kpiId = roleAssignment.getKpi().getId();

            boolean alreadyAssigned =
                    assignmentRepository.existsByEmployee_IdAndKpi_IdAndActiveTrue(
                            employeeId,
                            kpiId
                    );

            if (!alreadyAssigned) {

                log.info("Assigning KPI {} to employee {}",
                        kpiId, employeeId);

                // To DO Later when we are ready to assign default at onboarding
/*                kpiAssignmentService.assignToEmployee(
                        employeeId,
                        kpiId,
                        roleAssignment.getWeight()
                );*/

            } else {
                log.debug("KPI {} already assigned to employee {}, skipping.",
                        kpiId, employeeId);
            }
        }

        // 3️⃣ Enable KPI flag
        if (!employee.isKpiEnabled()) {
            employee.setKpiEnabled(true);
        }

        // 4️⃣ Update onboarding status (if applicable)
        Long onboardingId = (Long) execution.getVariable("onboardingId");

        if (onboardingId != null) {

            EmployeeOnboarding onboarding =
                    onboardingRepository.findById(onboardingId)
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "Onboarding record not found: " + onboardingId));

            onboarding.setStatus(OnboardingStatus.KPI_ASSIGNED);

            log.info("Onboarding {} marked as KPI_ASSIGNED", onboardingId);
        }

        log.info("KPI assignment delegate completed successfully for employee {}",
                employeeId);
    }
}
