package com.justjava.humanresource.payroll.repositories;


import com.justjava.humanresource.payroll.entity.PayrollLineItem;
import com.justjava.humanresource.payroll.enums.PayComponentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    @Modifying
    @Query("""
    DELETE FROM PayrollLineItem li
    WHERE li.payrollRun.id = :payrollRunId
      AND li.componentCode IN :codes
""")
    void deleteByPayrollRunIdAndComponentCodeIn(
            @Param("payrollRunId") Long payrollRunId,
            @Param("codes") List<String> codes
    );
    @Modifying
    @Query("""
    DELETE FROM PayrollLineItem li
    WHERE li.payrollRun.id = :runId
      AND li.componentType = :type
      AND li.componentCode NOT IN :codes
""")
    void deleteByPayrollRunIdAndComponentTypeAndComponentCodeNotIn(
            @Param("runId") Long runId,
            @Param("type") PayComponentType type,
            @Param("codes") List<String> codes
    );
    @Query("""
    SELECT COALESCE(SUM(li.amount), 0)
    FROM PayrollLineItem li
    WHERE li.payrollRun.id = :runId
      AND li.componentCode IN ('PAYE', 'PENSION_EMP')
""")
    BigDecimal sumStatutoryDeductions(@Param("runId") Long runId);

    @Modifying
    @Query(value = """
INSERT INTO payroll_line_items (
    payroll_run_id,
    employee_id,
    component_code,
    description,
    amount,
    taxable,
    tax_relief,
    pensionable,
    part_of_gross,
    component_type,
    created_at,
    updated_at
)
SELECT
    new_pr.id,
    li.employee_id,
    li.component_code,
    li.description,
    li.amount,
    li.taxable,
    li.tax_relief,
    li.pensionable,
    li.part_of_gross,
    li.component_type,
    NOW(),
    NOW()
FROM payroll_line_items li
JOIN payroll_runs old_pr ON old_pr.id = li.payroll_run_id
JOIN payroll_runs new_pr ON new_pr.parent_run_id = old_pr.id
WHERE old_pr.period_start = :oldPeriodStart
AND old_pr.period_end = :oldPeriodEnd
AND new_pr.period_start = :newPeriodStart
AND new_pr.period_end = :newPeriodEnd
LIMIT :limit OFFSET :offset
""", nativeQuery = true)
    int bulkCopyLineItemsChunk(
            LocalDate oldPeriodStart,
            LocalDate oldPeriodEnd,
            LocalDate newPeriodStart,
            LocalDate newPeriodEnd,
            int limit,
            int offset
    );
}
