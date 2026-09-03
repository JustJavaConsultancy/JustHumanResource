package com.justjava.humanresource.employeeexit;

import com.justjava.humanresource.employeeexit.dto.SettlementLineCommand;
import com.justjava.humanresource.employeeexit.entity.*;
import com.justjava.humanresource.employeeexit.enums.*;
import com.justjava.humanresource.employeeexit.repository.*;
import com.justjava.humanresource.employeeexit.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class ExitSettlementServiceTest {
    @Mock ExitSettlementRepository settlements;
    @Mock ExitSettlementLineRepository lines;
    @Mock ExitAssetReturnRepository assets;
    @Mock EmployeeExitService exitService;
    @Mock EmployeeExitCaseRepository exits;
    @Mock EmployeeExitActivityService activity;
    @Mock ExitPackageCalculationService packageCalculation;
    ExitSettlementService service;

    @BeforeEach
    void setUp() {
        service = new ExitSettlementService(settlements, lines, assets, exitService, exits, activity, packageCalculation);
    }

    @Test
    void calculateFromPackageIncludesGeneratedLines() {
        EmployeeExitCase exit = exit();
        SettlementLineCommand generated = line("Severance", new BigDecimal("12000"), true);
        when(exitService.require(11L)).thenReturn(exit);
        when(packageCalculation.calculate(eq(exit), eq(new BigDecimal("100000")), eq(BigDecimal.ZERO))).thenReturn(List.of(generated));
        when(assets.findByExitCaseIdOrderByAssetName(11L)).thenReturn(List.of());
        when(settlements.findFirstByExitCaseIdOrderBySettlementVersionDesc(11L)).thenReturn(Optional.empty());
        when(settlements.saveAndFlush(any())).thenAnswer(i -> {
            ExitSettlement settlement = i.getArgument(0);
            settlement.setId(22L);
            return settlement;
        });
        when(lines.save(any())).thenAnswer(i -> i.getArgument(0));
        when(settlements.save(any())).thenAnswer(i -> i.getArgument(0));

        ExitSettlement settlement = service.calculateFromPackage(11L, List.of(), new BigDecimal("100000"), BigDecimal.ZERO, 7L);

        assertEquals(SettlementStatus.IN_APPROVAL, settlement.getStatus());
        assertEquals(new BigDecimal("12000.00"), settlement.getGrossEarnings());
        verify(lines).save(argThat(l -> "Severance".equals(l.getDescription()) && "EXIT_PACKAGE_RULE_1".equals(l.getSource())));
    }

    @Test
    void manualAdjustmentRequiresReason() {
        EmployeeExitCase exit = exit();
        SettlementLineCommand manual = line("Manual", BigDecimal.TEN, false);
        manual.setManualAdjustment(true);
        when(exitService.require(11L)).thenReturn(exit);
        when(assets.findByExitCaseIdOrderByAssetName(11L)).thenReturn(List.of());
        when(settlements.findFirstByExitCaseIdOrderBySettlementVersionDesc(11L)).thenReturn(Optional.empty());
        when(settlements.saveAndFlush(any())).thenAnswer(i -> {
            ExitSettlement settlement = i.getArgument(0);
            settlement.setId(22L);
            return settlement;
        });

        assertThrows(IllegalArgumentException.class, () -> service.calculate(11L, List.of(manual), 7L));
    }

    private EmployeeExitCase exit() {
        EmployeeExitCase exit = new EmployeeExitCase();
        exit.setId(11L);
        exit.setStatus(ExitStatus.APPROVED);
        exit.setProposedLastWorkingDate(LocalDate.now());
        return exit;
    }

    private SettlementLineCommand line(String description, BigDecimal amount, boolean earning) {
        SettlementLineCommand line = new SettlementLineCommand();
        line.setLineType(earning ? SettlementLineType.SEVERANCE : SettlementLineType.OTHER_DEDUCTION);
        line.setDescription(description);
        line.setAmount(amount);
        line.setEarning(earning);
        line.setSource("EXIT_PACKAGE_RULE_1");
        return line;
    }
}
