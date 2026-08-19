package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.PayrollRunStatus;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.payroll.entity.PayrollLineItem;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.enums.PayComponentType;
import com.justjava.humanresource.payroll.repositories.AllowanceRepository;
import com.justjava.humanresource.payroll.repositories.DeductionRepository;
import com.justjava.humanresource.payroll.repositories.PayrollAccountingSettingsRepository;
import com.justjava.humanresource.payroll.repositories.PayrollJournalBatchRepository;
import com.justjava.humanresource.payroll.repositories.PayrollJournalEntryRepository;
import com.justjava.humanresource.payroll.repositories.PayrollLineItemRepository;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.diagnostics.PayrollJournalImbalanceException;
import com.justjava.humanresource.payroll.statutory.repositories.PensionSchemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollJournalServiceTest {

    @Mock
    private PayrollRunRepository payrollRunRepository;
    @Mock
    private PayrollLineItemRepository payrollLineItemRepository;
    @Mock
    private PayrollJournalEntryRepository journalRepository;
    @Mock
    private PayrollJournalBatchRepository batchRepository;
    @Mock
    private PayrollAccountingSettingsRepository settingsRepository;
    @Mock
    private PayrollPeriodService payrollPeriodService;
    @Mock
    private PayrollPeriodRepository payrollPeriodRepository;
    @Mock
    private AllowanceRepository allowanceRepository;
    @Mock
    private DeductionRepository deductionRepository;
    @Mock
    private PensionSchemeRepository pensionSchemeRepository;

    private PayrollJournalService service;

    @BeforeEach
    void setUp() {
        service = new PayrollJournalService(
                payrollRunRepository,
                payrollLineItemRepository,
                journalRepository,
                batchRepository,
                settingsRepository,
                payrollPeriodService,
                payrollPeriodRepository,
                allowanceRepository,
                deductionRepository,
                pensionSchemeRepository
        );
    }

    @Test
    void generateJournalEntries_whenUnbalanced_shouldThrowDiagnosticException() {
        Long companyId = 1L;
        Long periodId = 10L;
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        PayrollPeriod period = period(companyId, periodId, start, end);
        PayrollRun run = payrollRun(20L, employee(30L), start, end);
        PayrollLineItem earning = lineItem(40L, run, PayComponentType.EARNING, "BASIC", "Basic Salary", "100.00");

        when(payrollPeriodRepository.findById(periodId)).thenReturn(Optional.of(period));
        when(journalRepository.findByCompanyIdAndPayrollPeriodId(companyId, periodId)).thenReturn(List.of());
        when(batchRepository.findByCompanyIdAndPayrollPeriodId(companyId, periodId)).thenReturn(Optional.empty());
        when(payrollRunRepository.findLatestByCompanyAndPeriodAndStatus(companyId, start, end, PayrollRunStatus.POSTED))
                .thenReturn(List.of(run));
        when(settingsRepository.findByCompanyId(companyId)).thenReturn(Optional.empty());
        when(settingsRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(allowanceRepository.findAll()).thenReturn(List.of());
        when(deductionRepository.findAll()).thenReturn(List.of());
        when(pensionSchemeRepository.findEffectiveScheme(end, com.justjava.humanresource.core.enums.RecordStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(payrollLineItemRepository.findByPayrollRunId(run.getId())).thenReturn(List.of(earning));

        PayrollJournalImbalanceException ex = assertThrows(
                PayrollJournalImbalanceException.class,
                () -> service.generateJournalEntries(companyId, periodId, start, end)
        );

        assertEquals(new BigDecimal("100.00"), ex.getDiagnostics().getTotalDebit());
        assertEquals(new BigDecimal("80.00"), ex.getDiagnostics().getTotalCredit());
        assertEquals(new BigDecimal("20.00"), ex.getDiagnostics().getDifference());
        assertEquals(2, ex.getDiagnostics().getGeneratedLineCount());
        assertEquals(1, ex.getDiagnostics().getPayrollRuns().size());
        assertTrue(ex.getDiagnostics().toLogReport().contains("BASIC"));
        assertTrue(ex.getDiagnostics().toLogReport().contains("NET_PAY"));
        verify(batchRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(journalRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateJournalEntries_whenResidualExists_shouldPostBalancedResidualAdjustmentPair() {
        Long companyId = 1L;
        Long periodId = 10L;
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        PayrollPeriod period = period(companyId, periodId, start, end);
        PayrollRun run = payrollRun(20L, employee(30L), start, end);
        PayrollLineItem earning = lineItem(40L, run, PayComponentType.EARNING, "BASIC", "Basic Salary", "100.00");
        PayrollLineItem paye = lineItem(41L, run, PayComponentType.DEDUCTION, "PAYE", "PAYE Tax", "20.00");
        PayrollLineItem residual = lineItem(42L, run, PayComponentType.EARNING, "RESIDUAL", "Residual Adjustment", "20.00");

        when(payrollPeriodRepository.findById(periodId)).thenReturn(Optional.of(period));
        when(journalRepository.findByCompanyIdAndPayrollPeriodId(companyId, periodId)).thenReturn(List.of());
        when(batchRepository.findByCompanyIdAndPayrollPeriodId(companyId, periodId)).thenReturn(Optional.empty());
        when(payrollRunRepository.findLatestByCompanyAndPeriodAndStatus(companyId, start, end, PayrollRunStatus.POSTED))
                .thenReturn(List.of(run));
        when(settingsRepository.findByCompanyId(companyId)).thenReturn(Optional.empty());
        when(settingsRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(allowanceRepository.findAll()).thenReturn(List.of());
        when(deductionRepository.findAll()).thenReturn(List.of());
        when(pensionSchemeRepository.findEffectiveScheme(end, com.justjava.humanresource.core.enums.RecordStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(payrollLineItemRepository.findByPayrollRunId(run.getId())).thenReturn(List.of(earning, paye, residual));
        when(batchRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.generateJournalEntries(companyId, periodId, start, end);

        verify(batchRepository).save(org.mockito.ArgumentMatchers.argThat(batch ->
                new BigDecimal("120.00").equals(batch.getTotalDebit())
                        && new BigDecimal("120.00").equals(batch.getTotalCredit())
                        && batch.isBalanced()
        ));
        verify(journalRepository, times(5)).save(org.mockito.ArgumentMatchers.any());
    }

    private PayrollPeriod period(Long companyId, Long periodId, LocalDate start, LocalDate end) {
        PayrollPeriod period = new PayrollPeriod();
        period.setId(periodId);
        period.setCompanyId(companyId);
        period.setPeriodStart(start);
        period.setPeriodEnd(end);
        return period;
    }

    private PayrollRun payrollRun(Long runId, Employee employee, LocalDate start, LocalDate end) {
        PayrollRun run = new PayrollRun();
        run.setId(runId);
        run.setEmployee(employee);
        run.setPeriodStart(start);
        run.setPeriodEnd(end);
        run.setPayrollDate(end);
        run.setStatus(PayrollRunStatus.POSTED);
        run.setGrossPay(new BigDecimal("100.00"));
        run.setTotalDeductions(new BigDecimal("20.00"));
        run.setNetPay(new BigDecimal("80.00"));
        return run;
    }

    private Employee employee(Long employeeId) {
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setEmployeeNumber("EMP001");
        employee.setFirstName("Ada");
        employee.setLastName("Lovelace");
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        return employee;
    }

    private PayrollLineItem lineItem(
            Long lineId,
            PayrollRun run,
            PayComponentType type,
            String code,
            String description,
            String amount) {

        PayrollLineItem line = new PayrollLineItem();
        line.setId(lineId);
        line.setPayrollRun(run);
        line.setEmployee(run.getEmployee());
        line.setComponentType(type);
        line.setComponentCode(code);
        line.setDescription(description);
        line.setAmount(new BigDecimal(amount));
        return line;
    }
}
