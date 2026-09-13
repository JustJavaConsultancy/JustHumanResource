package com.justjava.humanresource.employeeexit.service;

import com.justjava.humanresource.employeeexit.enums.ExitDocumentType;
import com.justjava.humanresource.employeeexit.enums.ExitType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


@Service
public class ExitRequiredDocumentService {

    private static final Map<ExitType, Set<ExitDocumentType>> CONFIRMED_RULES = new EnumMap<>(ExitType.class);

    static {
        CONFIRMED_RULES.put(ExitType.RESIGNATION, EnumSet.of(ExitDocumentType.RESIGNATION_LETTER));
        CONFIRMED_RULES.put(ExitType.TERMINATION, EnumSet.of(ExitDocumentType.TERMINATION_LETTER));
        // RETIREMENT, CONTRACT_EXPIRY, REDUNDANCY, DEATH_IN_SERVICE, ABANDONMENT, OTHER:
        // intentionally unmapped - no confirmed required document yet. See class Javadoc.
    }


    public Set<ExitDocumentType> requiredDocuments(ExitType exitType) {
        return CONFIRMED_RULES.getOrDefault(exitType, Set.of());
    }
}