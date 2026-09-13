package com.justjava.humanresource.employeeexit;

import com.justjava.humanresource.employeeexit.entity.*;
import com.justjava.humanresource.employeeexit.enums.*;
import com.justjava.humanresource.employeeexit.repository.*;
import com.justjava.humanresource.employeeexit.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class EmployeeExitReadinessServiceTest {
    @Mock EmployeeExitService exits;
    @Mock ExitClearanceItemRepository clearances;
    @Mock ExitAssetReturnRepository assets;
    @Mock EmployeeExitDocumentRepository documents;
    @Mock ExitSettlementRepository settlements;
    @Mock ExitAccessActionRepository access;
    EmployeeExitReadinessService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeExitReadinessService(exits, clearances, assets, documents, settlements, access, new ExitRequiredDocumentService());
    }

    @Test
    void reportsDocumentAndSettlementBlockers() {
        EmployeeExitCase exit = new EmployeeExitCase();
        exit.setId(11L);
        exit.setExitType(ExitType.RESIGNATION);
        exit.setEffectiveExitDate(LocalDate.now());
        when(exits.require(11L)).thenReturn(exit);
        when(clearances.existsByExitCaseIdAndStatusNotIn(eq(11L), anyList())).thenReturn(false);
        when(assets.findByExitCaseIdOrderByAssetName(11L)).thenReturn(List.of());
        when(documents.existsByExitCaseIdAndDocumentType(11L, ExitDocumentType.RESIGNATION_LETTER)).thenReturn(false);
        when(settlements.findFirstByExitCaseIdOrderBySettlementVersionDesc(11L)).thenReturn(Optional.empty());

        var result = service.validate(11L);

        assertFalse(result.ready());
        assertTrue(result.blockers().stream().anyMatch(b -> b.category() == com.justjava.humanresource.employeeexit.dto.ExitReadinessBlocker.Category.MISSING_REQUIRED_DOCUMENT));
        assertTrue(result.blockers().stream().anyMatch(b -> b.category() == com.justjava.humanresource.employeeexit.dto.ExitReadinessBlocker.Category.SETTLEMENT_NOT_APPROVED));
    }

    @Test
    void terminationRequiresTerminationLetter() {
        EmployeeExitCase exit = new EmployeeExitCase();
        exit.setId(12L);
        exit.setExitType(ExitType.TERMINATION);
        exit.setEffectiveExitDate(LocalDate.now());
        when(exits.require(12L)).thenReturn(exit);
        when(clearances.existsByExitCaseIdAndStatusNotIn(eq(12L), anyList())).thenReturn(false);
        when(assets.findByExitCaseIdOrderByAssetName(12L)).thenReturn(List.of());
        when(documents.existsByExitCaseIdAndDocumentType(12L, ExitDocumentType.TERMINATION_LETTER)).thenReturn(false);
        when(settlements.findFirstByExitCaseIdOrderBySettlementVersionDesc(12L)).thenReturn(Optional.empty());

        var result = service.validate(12L);

        assertFalse(result.ready());
        assertTrue(result.blockers().stream().anyMatch(b -> b.category() == com.justjava.humanresource.employeeexit.dto.ExitReadinessBlocker.Category.MISSING_REQUIRED_DOCUMENT
                && b.message().contains("TERMINATION_LETTER")));
    }

    @Test
    void exitTypeWithoutConfirmedDocumentPolicyDoesNotGetMissingDocumentBlocker() {
        EmployeeExitCase exit = new EmployeeExitCase();
        exit.setId(13L);
        exit.setExitType(ExitType.RETIREMENT);
        exit.setEffectiveExitDate(LocalDate.now());
        ExitSettlement settlement = new ExitSettlement();
        settlement.setStatus(SettlementStatus.POSTED);
        when(exits.require(13L)).thenReturn(exit);
        when(clearances.existsByExitCaseIdAndStatusNotIn(eq(13L), anyList())).thenReturn(false);
        when(assets.findByExitCaseIdOrderByAssetName(13L)).thenReturn(List.of());
        when(settlements.findFirstByExitCaseIdOrderBySettlementVersionDesc(13L)).thenReturn(Optional.of(settlement));

        var result = service.validate(13L);

        assertTrue(result.blockers().stream().noneMatch(b -> b.category() == com.justjava.humanresource.employeeexit.dto.ExitReadinessBlocker.Category.MISSING_REQUIRED_DOCUMENT));
        verifyNoInteractions(documents);
    }

    @Test
    void effectiveDateAndAccessRevocationDoNotBlockOperationalReadiness() {
        EmployeeExitCase exit = new EmployeeExitCase();
        exit.setId(11L);
        exit.setExitType(ExitType.RETIREMENT);
        exit.setEffectiveExitDate(LocalDate.now().plusDays(3));
        ExitSettlement settlement = new ExitSettlement();
        settlement.setStatus(SettlementStatus.POSTED);

        when(exits.require(11L)).thenReturn(exit);
        when(clearances.existsByExitCaseIdAndStatusNotIn(eq(11L), anyList())).thenReturn(false);
        when(assets.findByExitCaseIdOrderByAssetName(11L)).thenReturn(List.of());
        when(settlements.findFirstByExitCaseIdOrderBySettlementVersionDesc(11L)).thenReturn(Optional.of(settlement));

        var result = service.validate(11L);

        assertTrue(result.ready());
        assertEquals(2, result.blockers().size());
    }
}