package com.justjava.humanresource.employee;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ONE-TIME CLEANUP UTILITY — DELETE AFTER USE
 *
 * Deletes employees with IDs and all their
 * related records across referencing tables.
 *
 * Runs automatically on app startup.
 * Once confirmed successful, delete this class.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeCleanupRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    // ✅ Change these IDs whenever you need to delete different employees
    private static final List<Object> EMPLOYEE_IDS = List.of(399, 400, 401);

    @Override
    @Transactional
    public void run(String... args) {
        log.warn("========================================================");
        log.warn("  EMPLOYEE CLEANUP RUNNER STARTING...");
        log.warn("  Targeting employee IDs: {}", EMPLOYEE_IDS);
        log.warn("========================================================");

        try {
            // --- Child tables first (referencing employees) ---
            deleteFrom("emergency_contacts",        "employee_id");
            deleteFrom("employee_allowances",        "employee_id");
            deleteFrom("employee_bank_details",      "employee_id");
            deleteFrom("employee_deductions",        "employee_id");
            deleteFrom("employee_documents",         "employee_id");
            deleteFrom("employee_onboarding",        "employee_id");
            deleteFrom("employee_pensions",          "employee_id");
            deleteFrom("employee_position_history",  "employee_id");
            deleteFrom("employee_reporting_lines",   "employee_id");
            deleteFrom("employee_reporting_lines",   "manager_id");   // also referenced as manager
            deleteFrom("hr_employee_appraisal",      "employee_id");
            deleteFrom("hr_promotion_request",       "employee_id");
            deleteFrom("kpi_assignment",             "employee_id");
            deleteFrom("kpi_measurement",            "employee_id");
            deleteFrom("pay_slips",                  "employee_id");
            deleteFrom("payroll_line_items",         "employee_id");
            deleteFrom("payroll_routing_configs",    "payroll_approver_id");
            deleteFrom("payroll_runs",               "employee_id");

            // --- Parent table last ---
            int deleted = deleteFrom("employees", "id");

            log.warn("========================================================");
            log.warn("  ✅ CLEANUP COMPLETE — {} employee record(s) deleted", deleted);
            log.warn("  ⚠️  Please delete EmployeeCleanupRunner.java now!");
            log.warn("========================================================");

        } catch (Exception e) {
            log.error("❌ CLEANUP FAILED — transaction will be rolled back", e);
            throw e; // triggers @Transactional rollback
        }
    }

    /**
     * Deletes rows from the given table where the column matches any of the EMPLOYEE_IDS.
     * Returns the number of rows deleted.
     */
    private int deleteFrom(String table, String column) {
        String placeholders = String.join(", ", EMPLOYEE_IDS.stream()
                .map(id -> "?")
                .toArray(String[]::new));

        String sql = String.format("DELETE FROM %s WHERE %s IN (%s)", table, column, placeholders);
        int rows = jdbcTemplate.update(sql, EMPLOYEE_IDS.toArray());

        log.info("  Deleted {} row(s) from {} where {} IN {}", rows, table, column, EMPLOYEE_IDS);
        return rows;
    }
}
