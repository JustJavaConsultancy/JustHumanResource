package com.justjava.humanresource.employeeexit.service;

import com.justjava.humanresource.employeeexit.entity.EmployeeExitCase;
import com.justjava.humanresource.employeeexit.entity.ExitSettlement;
import com.justjava.humanresource.employeeexit.entity.ExitSettlementPayrollPosting;
import com.justjava.humanresource.employeeexit.enums.SettlementStatus;
import com.justjava.humanresource.employeeexit.repository.EmployeeExitCaseRepository;
import com.justjava.humanresource.employeeexit.repository.ExitSettlementLineRepository;
import com.justjava.humanresource.employeeexit.repository.ExitSettlementPayrollLineMappingRepository;
import com.justjava.humanresource.employeeexit.repository.ExitSettlementPayrollPostingRepository;
import com.justjava.humanresource.employeeexit.repository.ExitSettlementRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollAccountingChangeDetector;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;




@ExtendWith(MockitoExtension.class)
class ExitPayrollImpactChangeHookTest {

    @Mock private ExitSettlementRepository settlements;
    @Mock private ExitSettlementLineRepository settlementLines;
    @Mock private ExitSettlementPayrollPostingRepository postings;
    @Mock private ExitSettlementPayrollLineMappingRepository mappings;
    @Mock private EmployeeExitCaseRepository exits;
    @Mock private EmployeeRepository employees;
    @Mock private PayrollPeriodRepository periods;
    @Mock private PayrollRunRepository runs;
    @Mock private PayrollLineItemRepository payrollLines;
    @Mock private EmployeeExitActivityService activity;
    @Mock private PayrollAccountingChangeDetector changeDetector;

    @InjectMocks
    private ExitPayrollImpactService service;

    @Test
    void approvedExitSettlementInvokesClosedPeriodChangeDetector() {
        Long settlementId = 12L;
        Long exitId = 22L;
        Long periodId = 32L;
        Long companyId = 42L;
        Long employeeId = 52L;
        Long runId = 62L;
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);

        ExitSettlement settlement = new ExitSettlement();
        settlement.setId(settlementId);
        settlement.setExitCaseId(exitId);
        settlement.setSettlementVersion(1);
        settlement.setCalculationFingerprint("fixture");
        settlement.setStatus(SettlementStatus.APPROVED);
        settlement.setGrossEarnings(BigDecimal.ZERO);
        settlement.setTotalDeductions(BigDecimal.ZERO);
        settlement.setNetSettlement(BigDecimal.ZERO);
        EmployeeExitCase exitCase = new EmployeeExitCase();
        exitCase.setId(exitId);
        exitCase.setCompanyId(companyId);
        exitCase.setEmployeeId(employeeId);
        PayrollPeriod period = new PayrollPeriod();
        period.setId(periodId);
        period.setCompanyId(companyId);
        period.setPeriodStart(start);
        period.setPeriodEnd(end);
        PayrollRun base = new PayrollRun();
        base.setId(runId);
        base.setPeriodStart(start);
        base.setPeriodEnd(end);
        base.setGrossPay(BigDecimal.ZERO);
        base.setTotalDeductions(BigDecimal.ZERO);
        base.setNetPay(BigDecimal.ZERO);
        ExitSettlementPayrollPosting posting = new ExitSettlementPayrollPosting();
        posting.setId(72L);

        when(settlements.findById(settlementId)).thenReturn(Optional.of(settlement));
        when(postings.findByPostingKey(any())).thenReturn(Optional.empty());
        when(exits.findById(exitId)).thenReturn(Optional.of(exitCase));
        when(periods.findById(periodId)).thenReturn(Optional.of(period));
        when(runs.findLatestPostedRunForEmployeeAndPeriod(employeeId, start, end))
                .thenReturn(Optional.of(base));
        when(runs.findMaxVersionForEmployeeAndPeriod(employeeId, start, end)).thenReturn(1);
        when(runs.saveAndFlush(any(PayrollRun.class))).thenAnswer(invocation -> {
            PayrollRun saved = invocation.getArgument(0);
            saved.setId(runId + 1);
            return saved;
        });
        when(postings.saveAndFlush(any(ExitSettlementPayrollPosting.class))).thenReturn(posting);
        when(payrollLines.findByPayrollRunId(runId)).thenReturn(List.of());
        when(settlementLines.findBySettlementIdOrderById(settlementId)).thenReturn(List.of());
        service.postApprovedSettlement(settlementId, periodId, 99L);

        verify(changeDetector).detect(runId + 1, companyId, start, end);
    }
}
