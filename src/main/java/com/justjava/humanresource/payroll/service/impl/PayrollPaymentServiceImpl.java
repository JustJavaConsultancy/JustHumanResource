package com.justjava.humanresource.payroll.service.impl;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.payroll.dto.PaymentStatus;
import com.justjava.humanresource.payroll.dto.PayrollRunDTO;
import com.justjava.humanresource.payroll.entity.PayrollPayment;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.repositories.PayrollPaymentRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import com.justjava.humanresource.payroll.service.BankIntegrationService;
import com.justjava.humanresource.payroll.service.PayrollPaymentService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollPaymentServiceImpl implements PayrollPaymentService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollPaymentRepository paymentRepository;
    private final BankIntegrationService bankService;
    private final RuntimeService runtimeService;
    private final EmployeeService employeeService;

    @Override
    @Transactional
    public void initiateBulkPayments(Long companyId,String processInstanceId, LocalDate start, LocalDate end) {


        List<PayrollRunDTO> runs =
                payrollRunRepository.findLatestPayrollRunsForPeriod(companyId, start, end);

        for (PayrollRunDTO run : runs) {

            Employee employee = employeeService.getById(run.getEmployeeId());
            // Idempotency guard
            if (paymentRepository.existsByPayrollRunId(run.getPayrollRunId()))
                continue;

            PayrollPayment payment = new PayrollPayment();
            payment.setPayrollRunId(run.getPayrollRunId());
            payment.setEmployeeId(run.getEmployeeId());
            payment.setCompanyId(companyId);
            payment.setAmount(run.getNetPay());
            payment.setProcessInstanceId(processInstanceId);
            payment.setAccountName(employee.getBankDetails().get(0).getAccountName());
            payment.setAccountNumber(employee.getBankDetails().get(0).getAccountNumber());
            payment.setBankName(employee.getBankDetails().get(0).getBankName());

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

        for (PayrollPayment p : batch) {
            String uniqueRef = "PAY-" + p.getId() + "-" + System.currentTimeMillis();

            p.setExternalReference(uniqueRef);
            p.setStatus(PaymentStatus.PROCESSING);
        }

        paymentRepository.saveAll(batch);

        bankService.initiateBulkTransfers(batch);
    }

    @Override
    @Transactional
    public void confirmPaymentsAndNotifyFlowable(
            Long companyId,
            String processInstanceId) {

        boolean allSuccessful =
                paymentRepository.countFailed(companyId) == 0 &&
                        paymentRepository.countProcessing(companyId) == 0;

        if (allSuccessful) {

            runtimeService.messageEventReceived(
                    "PAYMENT_MADE",
                    processInstanceId
            );
        }
    }
}