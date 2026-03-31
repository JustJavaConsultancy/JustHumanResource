package com.justjava.humanresource.workflow.delegate;

import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component("carryForwardRunsDelegate")
@RequiredArgsConstructor
public class CarryForwardRunsDelegate implements JavaDelegate {

    private final PayrollRunRepository repository;

    private static final int BATCH_SIZE = 5000;

    @Override
    public void execute(DelegateExecution execution) {

        Long companyId = (Long) execution.getVariable("companyId");
        LocalDate oldStart = (LocalDate) execution.getVariable("oldPeriodStart");
        LocalDate oldEnd = (LocalDate) execution.getVariable("oldPeriodEnd");
        LocalDate newStart = (LocalDate) execution.getVariable("newPeriodStart");
        LocalDate newEnd = (LocalDate) execution.getVariable("newPeriodEnd");

        int page = 0;
        int processed;

        do {
            processed = repository.bulkCarryForwardChunk(
                    companyId,
                    oldStart,
                    oldEnd,
                    newStart,
                    newEnd,
                    BATCH_SIZE,
                    page * BATCH_SIZE
            );

            log.info("Carry-forward runs processed batch: {}", processed);

            page++;

        } while (processed == BATCH_SIZE);
    }
}