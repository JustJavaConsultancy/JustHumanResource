package com.justjava.humanresource.workflow.delegate;

import com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component("carryForwardLineItemsDelegate")
@RequiredArgsConstructor
public class CarryForwardLineItemsDelegate implements JavaDelegate {

    private final PayrollLineItemRepository repository;

    private static final int BATCH_SIZE = 10000;

    @Override
    public void execute(DelegateExecution execution) {

        LocalDate oldStart = (LocalDate) execution.getVariable("oldPeriodStart");
        LocalDate oldEnd = (LocalDate) execution.getVariable("oldPeriodEnd");
        LocalDate newStart = (LocalDate) execution.getVariable("newPeriodStart");
        LocalDate newEnd = (LocalDate) execution.getVariable("newPeriodEnd");

        int page = 0;
        int processed;

        do {
            processed = repository.bulkCopyLineItemsChunk(
                    oldStart,
                    oldEnd,
                    newStart,
                    newEnd,
                    BATCH_SIZE,
                    page * BATCH_SIZE
            );

            log.info("Line items copied batch: {}", processed);

            page++;

        } while (processed == BATCH_SIZE);
    }
}