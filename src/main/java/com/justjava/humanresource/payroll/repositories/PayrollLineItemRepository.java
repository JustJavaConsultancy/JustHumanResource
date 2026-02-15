package com.justjava.humanresource.payroll.repositories;


import com.justjava.humanresource.payroll.entity.PayrollLineItem;
import com.justjava.humanresource.payroll.enums.PayComponentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollLineItemRepository
        extends JpaRepository<PayrollLineItem, Long> {

    /* ============================================================
       FETCH BY RUN
       ============================================================ */

    List<PayrollLineItem> findByPayrollRunId(Long payrollRunId);

    /* ============================================================
       FETCH BY RUN AND COMPONENT TYPE
       ============================================================ */

    List<PayrollLineItem> findByPayrollRunIdAndComponentType(
            Long payrollRunId,
            PayComponentType componentType
    );

    /* ============================================================
       DELETE FOR IDEMPOTENCY
       ============================================================ */

    void deleteByPayrollRunIdAndComponentType(
            Long payrollRunId,
            PayComponentType componentType
    );

    /* ============================================================
       FETCH TAXABLE COMPONENTS
       ============================================================ */

    List<PayrollLineItem> findByPayrollRunIdAndComponentTypeAndTaxableTrue(
            Long payrollRunId,
            PayComponentType componentType
    );
}
