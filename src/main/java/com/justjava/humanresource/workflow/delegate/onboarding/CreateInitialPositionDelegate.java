package com.justjava.humanresource.workflow.delegate.onboarding;


import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeePositionHistory;
import com.justjava.humanresource.hr.repository.EmployeePositionHistoryRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Component("createInitialPositionDelegate")
@RequiredArgsConstructor
@Transactional
public class CreateInitialPositionDelegate implements JavaDelegate {

    private final EmployeeRepository employeeRepository;
    private final EmployeePositionHistoryRepository positionRepository;

    @Override
    public void execute(DelegateExecution execution) {

        Long employeeId = (Long) execution.getVariable("employeeId");

        if (employeeId == null) {
            throw new IllegalStateException("Missing process variable: employeeId");
        }

        Employee employee =
                employeeRepository.findById(employeeId)
                        .orElseThrow(() ->
                                new IllegalStateException("Employee not found: " + employeeId)
                        );

        /*
         * Idempotency protection:
         * If a position history already exists for this employee,
         * do not create another initial record.
         */
        boolean alreadyExists =
                positionRepository.existsByEmployee_IdAndCurrentTrue(employeeId);

        if (alreadyExists) {
            log.warn("Initial position already exists for employeeId={}, skipping creation",
                    employeeId);
            return;
        }

        LocalDate effectiveDate =
                employee.getDateOfHire() != null
                        ? employee.getDateOfHire()
                        : LocalDate.now();

        EmployeePositionHistory history =
                EmployeePositionHistory.builder()
                        .employee(employee)
                        .department(employee.getDepartment())
                        .jobStep(employee.getJobStep())
                        .payGroup(employee.getPayGroup())
                        .effectiveFrom(effectiveDate)
                        .current(true)
                        .status(employee.getStatus())
                        .build();

        positionRepository.save(history);

        log.info("Initial position created for employeeId={} effectiveFrom={}",
                employeeId, effectiveDate);
    }
}
