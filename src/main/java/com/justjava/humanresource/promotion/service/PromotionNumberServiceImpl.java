package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.promotion.entity.PromotionNumberCounter;
import com.justjava.humanresource.promotion.repository.PromotionNumberCounterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Year;

@Service
public class PromotionNumberServiceImpl implements PromotionNumberService {

    private static final String PREFIX = "PROMO";

    private final PromotionNumberCounterRepository counterRepository;
    private final TransactionTemplate newTransaction;

    public PromotionNumberServiceImpl(PromotionNumberCounterRepository counterRepository,
                                      PlatformTransactionManager transactionManager) {
        this.counterRepository = counterRepository;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public String generateRequestNumber() {
        int year = Year.now().getValue();
        ensureCounterExists(year);

        PromotionNumberCounter counter = counterRepository.findForUpdate(PREFIX, year)
                .orElseThrow(() -> new IllegalStateException("Promotion number counter missing for " + year));
        long next = counter.getLastNumber() + 1;
        counter.setLastNumber(next);
        counterRepository.save(counter);

        return String.format("%s-%d-%04d", PREFIX, year, next);
    }

    private void ensureCounterExists(int year) {
        try {
            newTransaction.executeWithoutResult(status -> {
                if (counterRepository.findForUpdate(PREFIX, year).isEmpty()) {
                    PromotionNumberCounter counter = new PromotionNumberCounter();
                    counter.setPrefix(PREFIX);
                    counter.setYear(year);
                    counter.setLastNumber(0L);
                    counterRepository.saveAndFlush(counter);
                }
            });
        } catch (DataIntegrityViolationException ignored) {
            // Another transaction created the row; the locking read in generateRequestNumber will find it.
        }
    }
}
