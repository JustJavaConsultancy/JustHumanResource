package com.justjava.humanresource.workflow.delegate.kpi;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.kpi.entity.KpiEvaluationCycle;
import com.justjava.humanresource.kpi.repositories.KpiEvaluationCycleRepository;
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

@Component("batchStartEvaluationDelegate")
@RequiredArgsConstructor
@Transactional
public class BatchStartEvaluationDelegate implements JavaDelegate {

    private static final int BATCH_SIZE = 200;

    private final EmployeeRepository employeeRepository;
    private final RuntimeService runtimeService;
    private final KpiEvaluationCycleRepository cycleRepository;

    @Override
    public void execute(DelegateExecution execution) {

        YearMonth period =
                (YearMonth) execution.getVariable("evaluationPeriod");

        System.out.println(" The Evaluation Period Here===="+period);
        if(period==null)
            period = YearMonth.now().minusMonths(1);

        Long cycleId =
                (Long) execution.getVariable("cycleId");

        KpiEvaluationCycle cycle =
                cycleRepository.findById(cycleId).orElseThrow();

        int page = 0;
        Page<Employee> result;

        do {
            result = employeeRepository.findEmployeesWithKpiMeasurementForPeriod(period,
                    PageRequest.of(page, BATCH_SIZE));

            System.out.println(" The Size of the Employees with KPI Measurement for this period "+period
            +" is "+result.getContent().size());

            for (Employee employee : result.getContent()) {

                if (!employee.isKpiEnabled())
                    continue;

                String businessKey =
                        "KPI_" + employee.getId() + "_" + period;

                boolean exists =
                        runtimeService.createProcessInstanceQuery()
                                .processDefinitionKey("kpiEvaluationProcess")
                                .processInstanceBusinessKey(businessKey)
                                .active()
                                .count() > 0;

                if (!exists) {

                    runtimeService.startProcessInstanceByKey(
                            "kpiEvaluationProcess",
                            businessKey,
                            Map.of(
                                    "employeeId", employee.getId(),
                                    "evaluationPeriod", period,
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
