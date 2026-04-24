package com.justjava.humanresource.kpi.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ONE-TIME MIGRATION — DELETE THIS CLASS AFTER SUCCESSFUL DEPLOYMENT.
 *
 * Adds the impact_salary column to kpi_definition (if it doesn't already exist)
 * and sets the existing "Target" KPI to impact salary = true.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImpactSalaryMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Step 1: Add the column if it doesn't already exist
            jdbcTemplate.execute(
                    """
                    DO $$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_name = 'kpi_definition'
                              AND column_name = 'impact_salary'
                        ) THEN
                            ALTER TABLE kpi_definition
                                ADD COLUMN impact_salary BOOLEAN NOT NULL DEFAULT FALSE;
                            RAISE NOTICE 'Column impact_salary added successfully.';
                        ELSE
                            RAISE NOTICE 'Column impact_salary already exists, skipping.';
                        END IF;
                    END$$;
                    """
            );

            // Step 2: Set the "Target" KPI to impact salary
            int updated = jdbcTemplate.update(
                    "UPDATE kpi_definition SET impact_salary = TRUE WHERE name = 'Target'"
            );

            log.info("ImpactSalaryMigration: {} KPI(s) set to impact salary.", updated);

        } catch (Exception e) {
            log.error("ImpactSalaryMigration failed: {}", e.getMessage(), e);
            // We don't rethrow — a failure here should not prevent the app from starting
        }
    }
}