package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.payroll.dto.PaymentStatus;
import com.justjava.humanresource.payroll.entity.PayrollPayment;
import com.justjava.humanresource.payroll.repositories.PayrollPaymentRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.impl.PayrollPaymentServiceImpl;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollPaymentServiceImplTest {
    @Mock private PayrollRunRepository payrollRunRepository;
    @Mock private PayrollPaymentRepository paymentRepository;
    @Mock private BankIntegrationService bankService;
    @Mock private RuntimeService runtimeService;
    @Mock private EmployeeService employeeService;
    @Mock private PayrollJournalService journalService;

    private PayrollPaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PayrollPaymentServiceImpl(
                payrollRunRepository, paymentRepository, bankService, runtimeService, employeeService, journalService);
    }

    @Test
    void markBatchSuccessful_shouldAssumeSuccessWithoutCallingGateway() {
        PayrollPayment payment = payment("process-1", PaymentStatus.PENDING, "80.00");
        payment.setId(44L);
        when(paymentRepository.findByProcessInstanceId("process-1")).thenReturn(List.of(payment));

        service.markBatchSuccessful("process-1");

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("INTERNAL-PAY-44", payment.getExternalReference());
        verify(paymentRepository).saveAll(List.of(payment));
        verify(bankService, never()).initiateBulkTransfers(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void submitBatchToGateway_shouldMarkProcessingAndInvokeConcreteIntegration() {
        PayrollPayment payment = payment("process-1", PaymentStatus.PENDING, "80.00");
        payment.setId(44L);
        when(paymentRepository.findByProcessInstanceId("process-1")).thenReturn(List.of(payment));

        service.submitBatchToGateway("process-1");

        assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
        verify(paymentRepository).saveAll(List.of(payment));
        verify(bankService).initiateBulkTransfers(List.of(payment));
    }

    @Test
    void clearSuspenseForBatch_shouldRequireAllPaymentsSuccessfulAndUseProcessAsIdempotencyKey() {
        PayrollPayment successful = payment("process-1", PaymentStatus.SUCCESS, "80.00");
        when(paymentRepository.findByProcessInstanceId("process-1")).thenReturn(List.of(successful));

        service.clearSuspenseForBatch("process-1");

        verify(journalService).generatePaymentClearingEntries(1L, 10L, new BigDecimal("80.00"), "process-1");
    }

    @Test
    void clearSuspenseForBatch_shouldRejectPartialOrFailedBatch() {
        PayrollPayment successful = payment("process-1", PaymentStatus.SUCCESS, "40.00");
        PayrollPayment failed = payment("process-1", PaymentStatus.FAILED, "40.00");
        when(paymentRepository.findByProcessInstanceId("process-1")).thenReturn(List.of(successful, failed));

        assertThrows(IllegalStateException.class, () -> service.clearSuspenseForBatch("process-1"));

        verify(journalService, never()).generatePaymentClearingEntries(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    private PayrollPayment payment(String processId, PaymentStatus status, String amount) {
        PayrollPayment payment = new PayrollPayment();
        payment.setProcessInstanceId(processId);
        payment.setCompanyId(1L);
        payment.setPayrollPeriodId(10L);
        payment.setStatus(status);
        payment.setAmount(new BigDecimal(amount));
        return payment;
    }
}
