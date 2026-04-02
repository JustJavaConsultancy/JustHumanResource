package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.payroll.dto.PaymentStatus;
import com.justjava.humanresource.payroll.entity.PayrollPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PayrollPaymentRepository extends JpaRepository<PayrollPayment, Long> {

    /* ============================================================
       IDEMPOTENCY / EXISTENCE CHECKS
       ============================================================ */

    boolean existsByPayrollRunId(Long payrollRunId);

    boolean existsByPayrollRunIdAndStatusIn(
            Long payrollRunId,
            Collection<PaymentStatus> statuses
    );

    Optional<PayrollPayment> findByPayrollRunId(Long payrollRunId);

    /* ============================================================
       STATUS-BASED RETRIEVAL (PROCESSING ENGINE)
       ============================================================ */

    List<PayrollPayment> findByStatus(PaymentStatus status);

    Page<PayrollPayment> findByStatus(
            PaymentStatus status,
            Pageable pageable
    );

    List<PayrollPayment> findByStatusIn(Collection<PaymentStatus> statuses);

    /* ============================================================
       BULK PROCESSING HELPERS
       ============================================================ */

    Page<PayrollPayment> findByStatusIn(
            Collection<PaymentStatus> statuses,
            Pageable pageable
    );

    /* ============================================================
       COMPANY-LEVEL MONITORING
       ============================================================ */

    long countByCompanyIdAndStatus(Long companyId, PaymentStatus status);

    long countByCompanyIdAndStatusIn(
            Long companyId,
            Collection<PaymentStatus> statuses
    );

    /* ============================================================
       FAILURE HANDLING
       ============================================================ */

    List<PayrollPayment> findByStatusAndCompanyId(
            PaymentStatus status,
            Long companyId
    );

    /* ============================================================
       FLOWABLE COMPLETION CHECK HELPERS
       ============================================================ */

    default long countProcessing(Long companyId) {
        return countByCompanyIdAndStatus(companyId, PaymentStatus.PROCESSING);
    }

    default long countFailed(Long companyId) {
        return countByCompanyIdAndStatus(companyId, PaymentStatus.FAILED);
    }

    default long countPending(Long companyId) {
        return countByCompanyIdAndStatus(companyId, PaymentStatus.PENDING);
    }
    @Query("""
SELECT p FROM PayrollPayment p
WHERE p.status = com.justjava.humanresource.payroll.dto.PaymentStatus.FAILED
AND (p.nextRetryAt IS NULL OR p.nextRetryAt <= CURRENT_TIMESTAMP)
""")
    List<PayrollPayment> findRetryablePayments();

    Optional<PayrollPayment> findByExternalReference(String reference);
}