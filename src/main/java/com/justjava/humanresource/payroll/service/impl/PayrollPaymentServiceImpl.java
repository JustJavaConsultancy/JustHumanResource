package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeeBankDetail;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.payroll.dto.PaymentStatus;
import com.justjava.humanresource.payroll.dto.PayrollRunDTO;
import com.justjava.humanresource.payroll.entity.PayrollPayment;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.repositories.PayrollPaymentRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.BankIntegrationService;
import com.justjava.humanresource.payroll.service.PayrollJournalService;
import com.justjava.humanresource.payroll.service.PayrollPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.RuntimeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollPaymentServiceImpl implements PayrollPaymentService {
    private static final String PAYMENT_MADE_MESSAGE = "PAYMENT_MADE";

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollPaymentRepository paymentRepository;
    private final BankIntegrationService bankService;
    private final RuntimeService runtimeService;
    private final EmployeeService employeeService;
    private final PayrollJournalService payrollJournalService;

    @Value("${payroll.payment.enable-transfer:false}")
    private boolean enableTransfer;

    @Override
    @Transactional
    public void initiateBulkPayments(Long companyId, Long payrollPeriodId, String processInstanceId, LocalDate start, LocalDate end) {


        List<PayrollRunDTO> runs =
                payrollRunRepository.findLatestPayrollRunsForPeriod(companyId, start, end);

        for (PayrollRunDTO run : runs) {

            Employee employee = employeeService.getById(run.getEmployeeId());
            // Idempotency guard
            if (paymentRepository.existsByPayrollRunId(run.getPayrollRunId()))
                continue;

            List<EmployeeBankDetail> bankDetails = employee.getBankDetails();
            PayrollPayment payment = new PayrollPayment();
            payment.setPayrollRunId(run.getPayrollRunId());
            payment.setEmployeeId(run.getEmployeeId());
            payment.setCompanyId(companyId);
            payment.setPayrollPeriodId(payrollPeriodId);
            payment.setAmount(run.getNetPay());
            payment.setProcessInstanceId(processInstanceId);
            payment.setAccountName(bankDetails!=null&&!bankDetails.isEmpty()?bankDetails.get(0).getAccountName():"");
            payment.setAccountNumber(bankDetails!=null&&!bankDetails.isEmpty()?bankDetails.get(0).getAccountNumber():"");
            payment.setBankName(bankDetails!=null&&!bankDetails.isEmpty()?bankDetails.get(0).getBankName():"");

            payment.setStatus(PaymentStatus.PENDING);

            paymentRepository.save(payment);
        }
    }

    @Override
    @Transactional
    public void processPendingPayments() {
        Page<PayrollPayment> page = paymentRepository.findByStatus(
                PaymentStatus.PENDING,
                PageRequest.of(0, 1000)
        );

        List<PayrollPayment> batch = page.getContent();
        if (batch.isEmpty()) return;

        if (enableTransfer) {
            submitPayments(batch);
        } else {
            markPaymentsSuccessful(batch);
            batch.stream().map(PayrollPayment::getProcessInstanceId).filter(Objects::nonNull).distinct()
                    .forEach(this::tryNotifyPaymentMade);
        }
    }

    @Override
    @Transactional
    public void markBatchSuccessful(String processInstanceId) {
        List<PayrollPayment> payments = requireBatch(processInstanceId);
        markPaymentsSuccessful(payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .toList());
    }

    @Override
    @Transactional
    public void submitBatchToGateway(String processInstanceId) {
        List<PayrollPayment> pending = requireBatch(processInstanceId).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .toList();
        if (!pending.isEmpty()) {
            submitPayments(pending);
        }
    }

    @Override
    @Transactional
    public void clearSuspenseForBatch(String processInstanceId) {
        Map<PaymentPeriodKey, BigDecimal> totals = successfulBatchTotals(processInstanceId);
        totals.forEach((key, amount) -> payrollJournalService.generatePaymentClearingEntries(
                key.companyId(), key.payrollPeriodId(), amount, processInstanceId));
    }

    @Override
    @Transactional(readOnly = true)
    public void verifySuspenseClearedForBatch(String processInstanceId) {
        successfulBatchTotals(processInstanceId).keySet().forEach(key ->
                payrollJournalService.verifyPaymentSuspenseCleared(key.companyId(), key.payrollPeriodId()));
    }

    @Override
    @Transactional
    public void confirmPaymentsAndNotifyFlowable(
            Long companyId,
            String processInstanceId) {

        if (paymentRepository.countByProcessInstanceIdAndStatusIn(
                processInstanceId,
                List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING, PaymentStatus.FAILED)) > 0) {
            return;
        }

        tryNotifyPaymentMade(processInstanceId);
    }

    @Override
    @Transactional
    public void recordPaymentResult(String reference, String status, String failureReason) {
        PayrollPayment payment = paymentRepository
                .findByExternalReference(reference)
                .orElseThrow(() -> new IllegalStateException("Payment not found"));

        if ("success".equalsIgnoreCase(status)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setFailureReason(null);
        } else if ("failed".equalsIgnoreCase(status)) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(failureReason);
        }

        paymentRepository.save(payment);
        tryNotifyPaymentMade(payment.getProcessInstanceId());
    }

    @Override
    @Transactional
    public void confirmCompletedPayments() {
        paymentRepository.findSuccessProcessInstanceIds().forEach(processInstanceId -> {
            tryNotifyPaymentMade(processInstanceId);
        });
    }

    private Map<PaymentPeriodKey, BigDecimal> successfulBatchTotals(String processInstanceId) {
        List<PayrollPayment> payments = requireBatch(processInstanceId);
        long notSuccessful = payments.stream()
                .filter(payment -> payment.getStatus() != PaymentStatus.SUCCESS)
                .count();
        if (notSuccessful > 0) {
            throw new IllegalStateException("Cannot clear payroll suspense until every payment in the batch succeeds.");
        }
        return payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS)
                .collect(Collectors.groupingBy(
                        payment -> new PaymentPeriodKey(payment.getCompanyId(), payment.getPayrollPeriodId()),
                        Collectors.mapping(
                                payment -> payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                        )
                ));
    }

    private List<PayrollPayment> requireBatch(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalStateException("Payment batch process id is required.");
        }
        List<PayrollPayment> payments = paymentRepository.findByProcessInstanceId(processInstanceId);
        if (payments.isEmpty()) {
            throw new IllegalStateException("No payroll payments found for process " + processInstanceId + ".");
        }
        for (PayrollPayment payment : payments) {
            PaymentPeriodKey key = new PaymentPeriodKey(payment.getCompanyId(), payment.getPayrollPeriodId());
            if (key.companyId() == null || key.payrollPeriodId() == null) {
                throw new IllegalStateException("Payment is missing company or payroll period for clearing journal.");
            }
        }
        return payments;
    }

    private void markPaymentsSuccessful(List<PayrollPayment> payments) {
        payments.forEach(payment -> {
            payment.setExternalReference("INTERNAL-PAY-" + payment.getId());
            payment.setFailureReason(null);
            payment.setStatus(PaymentStatus.SUCCESS);
        });
        paymentRepository.saveAll(payments);
    }

    private void submitPayments(List<PayrollPayment> payments) {
        payments.forEach(payment -> {
            payment.setExternalReference("PAY-" + payment.getId() + "-" + System.currentTimeMillis());
            payment.setStatus(PaymentStatus.PROCESSING);
        });
        paymentRepository.saveAll(payments);
        bankService.initiateBulkTransfers(payments);
    }

    private void tryNotifyPaymentMade(String processInstanceId) {
        if (processInstanceId == null) {
            return;
        }

        if (paymentRepository.countByProcessInstanceIdAndStatusIn(
                processInstanceId,
                List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING, PaymentStatus.FAILED)) > 0) {
            return;
        }

        Execution execution = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .messageEventSubscriptionName(PAYMENT_MADE_MESSAGE)
                .singleResult();

        if (execution == null) {
            return;
        }

        try {
            runtimeService.messageEventReceived(PAYMENT_MADE_MESSAGE, execution.getId());
        } catch (Exception ex) {
            log.warn("Unable to deliver {} for process {}: {}", PAYMENT_MADE_MESSAGE, processInstanceId, ex.getMessage());
        }
    }

    private record PaymentPeriodKey(Long companyId, Long payrollPeriodId) {
    }
}
