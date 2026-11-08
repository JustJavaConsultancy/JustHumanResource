package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.repositories.PaySlipRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;




@ExtendWith(MockitoExtension.class)
class PayrollPeriodServiceImplTest {

    @Mock
    private PayrollPeriodRepository payrollPeriodRepository;

    @Mock
    private PayrollRunRepository payrollRunRepository;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private PaySlipRepository paySlipRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private PayrollPeriodServiceImpl service;

    @Test
    void opensInitialPeriodWithSuppliedPastDatesAndStoresPlannedEnd() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 31);

        when(payrollPeriodRepository.existsByCompanyIdAndStatus(1L, PayrollPeriodStatus.OPEN)).thenReturn(false);
        when(payrollPeriodRepository.existsOverlappingPeriod(1L, start, end)).thenReturn(false);
        when(payrollPeriodRepository.save(any(PayrollPeriod.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayrollPeriod period = service.openInitialPeriod(1L, start, end);

        assertEquals(start, period.getPeriodStart());
        assertEquals(end, period.getPeriodEnd());
        assertEquals(end, period.getPlannedPeriodEnd());
        assertEquals(PayrollPeriodStatus.OPEN, period.getStatus());
    }

    @Test
    void rejectsOpeningAnOverlappingPeriod() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 31);

        when(payrollPeriodRepository.existsByCompanyIdAndStatus(1L, PayrollPeriodStatus.OPEN)).thenReturn(false);
        when(payrollPeriodRepository.existsOverlappingPeriod(1L, start, end)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.openInitialPeriod(1L, start, end));
    }

    @Test
    void extendsOpenPeriodEndWithoutChangingPlannedEnd() {
        PayrollPeriod current = new PayrollPeriod();
        current.setId(10L);
        current.setCompanyId(1L);
        current.setPeriodStart(LocalDate.of(2026, 6, 1));
        current.setPeriodEnd(LocalDate.of(2026, 7, 1));
        current.setPlannedPeriodEnd(LocalDate.of(2026, 7, 1));
        current.setStatus(PayrollPeriodStatus.OPEN);

        LocalDate newEnd = LocalDate.of(2026, 7, 10);

        when(payrollPeriodRepository.findByCompanyIdAndStatus(1L, PayrollPeriodStatus.OPEN))
                .thenReturn(Optional.of(current));
        when(payrollPeriodRepository.existsOverlappingPeriodExcludingId(
                1L, 10L, current.getPeriodStart(), newEnd)).thenReturn(false);
        when(payrollPeriodRepository.save(any(PayrollPeriod.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayrollPeriod extended = service.extendOpenPeriodEnd(1L, newEnd);

        assertEquals(newEnd, extended.getPeriodEnd());
        assertEquals(LocalDate.of(2026, 7, 1), extended.getPlannedPeriodEnd());
        assertEquals(PayrollPeriodStatus.OPEN, extended.getStatus());
    }

    @Test
    void closesUsingActualPeriodEndAndOpensNextCalendarAlignedPeriod() {
        PayrollPeriod current = new PayrollPeriod();
        current.setId(10L);
        current.setCompanyId(1L);
        current.setPeriodStart(LocalDate.of(2026, 6, 1));
        current.setPeriodEnd(LocalDate.of(2026, 7, 1));
        current.setPlannedPeriodEnd(LocalDate.of(2026, 7, 1));
        current.setStatus(PayrollPeriodStatus.OPEN);

        LocalDate actualEnd = LocalDate.of(2026, 7, 10);

        when(payrollPeriodRepository.findByCompanyIdAndStatus(1L, PayrollPeriodStatus.OPEN))
                .thenReturn(Optional.of(current));
        when(payrollPeriodRepository.existsOverlappingPeriodExcludingId(
                1L, 10L, current.getPeriodStart(), actualEnd)).thenReturn(false);
        when(payrollRunRepository.countLatestByCompanyAndPayrollDateBetweenAndStatusNot(
                1L, current.getPeriodStart(), actualEnd, com.justjava.humanresource.core.enums.PayrollRunStatus.POSTED))
                .thenReturn(0L);
        when(payrollRunRepository.countByEmployee_Department_Company_IdAndPayrollDateBetween(
                1L, LocalDate.of(2026, 7, 11), YearMonth.of(2026, 7).atEndOfMonth()))
                .thenReturn(0L);
        when(payrollPeriodRepository.save(any(PayrollPeriod.class))).thenAnswer(invocation -> {
            PayrollPeriod savedPeriod = invocation.getArgument(0);
            if (savedPeriod.getId() == null) {
                savedPeriod.setId(11L);
            }
            return savedPeriod;
        });

        service.closeAndOpenNext(1L, actualEnd);

        ArgumentCaptor<PayrollPeriod> periodCaptor = ArgumentCaptor.forClass(PayrollPeriod.class);
        verify(payrollPeriodRepository, org.mockito.Mockito.times(2)).save(periodCaptor.capture());
        List<PayrollPeriod> saved = periodCaptor.getAllValues();

        PayrollPeriod closed = saved.get(0);
        assertEquals(actualEnd, closed.getPeriodEnd());
        assertEquals(LocalDate.of(2026, 7, 1), closed.getPlannedPeriodEnd());
        assertEquals(PayrollPeriodStatus.CLOSED, closed.getStatus());
        assertNotNull(closed.getClosedOn());

        PayrollPeriod next = saved.get(1);
        assertEquals(LocalDate.of(2026, 7, 11), next.getPeriodStart());
        assertEquals(LocalDate.of(2026, 7, 31), next.getPeriodEnd());
        assertEquals(LocalDate.of(2026, 7, 31), next.getPlannedPeriodEnd());
        assertEquals(PayrollPeriodStatus.OPEN, next.getStatus());

        verify(runtimeService).startProcessInstanceByKey(
                eq("batchPayrollProcess"),
                eq("BATCH_PERIOD_11"),
                any(Map.class)
        );
    }
}
