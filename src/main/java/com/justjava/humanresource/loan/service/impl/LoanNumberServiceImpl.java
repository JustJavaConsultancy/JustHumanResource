package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.loan.entity.LoanNumberCounter;
import com.justjava.humanresource.loan.repository.LoanNumberCounterRepository;
import com.justjava.humanresource.loan.service.LoanNumberService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Year;

@Service
public class LoanNumberServiceImpl implements LoanNumberService {

    private static final String PREFIX = "LOAN";

    private final LoanNumberCounterRepository counterRepository;
    private final TransactionTemplate newTransaction;

    public LoanNumberServiceImpl(LoanNumberCounterRepository counterRepository,
                                 PlatformTransactionManager transactionManager) {
        this.counterRepository = counterRepository;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public String generateApplicationNumber() {
        int year = Year.now().getValue();

        // The first number of a year has no row to lock yet. Create it in its own short
        // transaction so a race on the unique constraint cannot poison the caller's transaction.
        ensureCounterExists(year);

        LoanNumberCounter counter = counterRepository.findForUpdate(PREFIX, year)
                .orElseThrow(() -> new IllegalStateException("Loan number counter missing for " + year));
        long next = counter.getLastNumber() + 1;
        counter.setLastNumber(next);
        counterRepository.save(counter);

        return String.format("%s-%d-%04d", PREFIX, year, next);
    }

    private void ensureCounterExists(int year) {
        try {
            newTransaction.executeWithoutResult(status -> {
                if (counterRepository.findForUpdate(PREFIX, year).isEmpty()) {
                    LoanNumberCounter counter = new LoanNumberCounter();
                    counter.setPrefix(PREFIX);
                    counter.setYear(year);
                    counter.setLastNumber(0L);
                    counterRepository.saveAndFlush(counter);
                }
            });
        } catch (DataIntegrityViolationException ignored) {
            // Another request created the row first; the lock query below will find it.
        }
    }
}
