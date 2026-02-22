package com.justjava.humanresource.workflow.delegate.kpi;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.kpi.entity.AppraisalCycle;
import com.justjava.humanresource.kpi.repositories.AppraisalCycleRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;

@Component("batchStartAppraisalDelegate")
@RequiredArgsConstructor
@Transactional
public class BatchStartAppraisalDelegate implements JavaDelegate {

    private static final int BATCH_SIZE = 200;

    private final EmployeeRepository employeeRepository;
    private final RuntimeService runtimeService;
    private final AppraisalCycleRepository cycleRepository;

    @Override
    public void execute(DelegateExecution execution) {

        Long cycleId = Long.valueOf(
                execution.getVariable("cycleId").toString()
        );

        AppraisalCycle cycle =
                cycleRepository.findById(cycleId)
                        .orElseThrow();

        System.out.println("Starting batch process for period: " + period);
        System.out.println("Circle ID: ============================" + cycleId);
        int page = 0;
        Page<Employee> result;

        do {

            result = employeeRepository.findAll(
                    PageRequest.of(page, BATCH_SIZE)
            );

            for (Employee employee : result.getContent()) {

                if (!employee.isKpiEnabled())
                    continue;

                String businessKey =
                        "APPRAISAL_" + employee.getId()
                                + "_Y" + cycle.getYear()
                                + "Q" + cycle.getQuarter();

                boolean exists =
                        runtimeService.createProcessInstanceQuery()
                                .processDefinitionKey("employeeAppraisalProcess")
                                .processInstanceBusinessKey(businessKey)
                                .active()
                                .count() > 0;

                if (!exists) {

                    runtimeService.startProcessInstanceByKey(
                            "employeeAppraisalProcess",
                            businessKey,
                            Map.of(
                                    "employeeId", employee.getId(),
                                    "cycleId", cycleId
                            )
                    );
                }

                cycle.setProcessedEmployees(
                        cycle.getProcessedEmployees() + 1
                );
            }

            page++;

        } while (result.hasNext());

        cycle.setCompleted(true);
        cycle.setCompletedAt(LocalDateTime.now());

        cycleRepository.save(cycle);
    }
}
