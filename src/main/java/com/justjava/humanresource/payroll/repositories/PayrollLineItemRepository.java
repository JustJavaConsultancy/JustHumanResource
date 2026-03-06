package com.justjava.humanresource.payroll.repositories;


import com.justjava.humanresource.payroll.entity.PayrollLineItem;
import com.justjava.humanresource.payroll.enums.PayComponentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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
    @Query("""
       SELECT COALESCE(SUM(p.amount), 0)
       FROM PayrollLineItem p
       WHERE p.payrollRun.id = :runId
       AND p.taxable = true
       """)
    BigDecimal sumTaxableEarnings(@Param("runId") Long runId);

    @Query("""
       SELECT COALESCE(SUM(p.amount), 0)
       FROM PayrollLineItem p
       WHERE p.payrollRun.id = :runId
       AND p.componentCode = :code
       """)
    BigDecimal sumByRunAndCode(
            @Param("runId") Long runId,
            @Param("code") String code
    );
}
