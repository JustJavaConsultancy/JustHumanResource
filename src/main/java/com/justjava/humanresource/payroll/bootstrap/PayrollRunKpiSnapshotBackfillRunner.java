package com.justjava.humanresource.payroll.bootstrap;

import com.justjava.humanresource.payroll.service.PayrollRunKpiSnapshotBackfillService;
import com.justjava.humanresource.payroll.service.PayrollRunKpiSnapshotBackfillService.BackfillResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * ONE-OFF, TEMPORARY startup runner for the legacy Salary-KPI snapshot
 * backfill (Step 11 of the salary-impact-KPI plan).
 *
 * <p><b>DELETE THIS FILE</b> (and {@link PayrollRunKpiSnapshotBackfillService})
 * once the backfill has run successfully everywhere it's needed. This is a
 * migration tool, not a permanent part of the app.
 *
 * <p>Inert unless explicitly turned on — it will NOT run just because it's
 * on the classpath. Enable via config or environment variables:
 *
 * <pre>
 *   payroll.kpi-backfill.enabled=true
 *   payroll.kpi-backfill.dry-run=true   (default; set false to actually write)
 * </pre>
 *
 * or as env vars: {@code PAYROLL_KPI_BACKFILL_ENABLED=true},
 * {@code PAYROLL_KPI_BACKFILL_DRY_RUN=false}.
 *
 * <p>Recommended sequence:
 * <ol>
 *   <li>Deploy with {@code enabled=true}, {@code dry-run=true} (or just
 *       {@code enabled=true} since dry-run defaults to true). Restart once.
 *       Check the logs for scanned/skipped/backfilled counts.</li>
 *   <li>If the numbers look right, set {@code dry-run=false} and restart
 *       once more — this run actually writes snapshot rows and sets
 *       {@code salaryKpiScore}.</li>
 *   <li>Set {@code enabled=false} (or remove the properties) and delete
 *       this file + the backfill service from the codebase.</li>
 * </ol>
 *
 * <p>Safe if it accidentally runs twice: the underlying service only
 * targets POSTED runs that don't already have snapshot rows, so a repeat
 * run finds nothing left to do.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payroll.kpi-backfill.enabled", havingValue = "true")
public class PayrollRunKpiSnapshotBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PayrollRunKpiSnapshotBackfillRunner.class);

    private final PayrollRunKpiSnapshotBackfillService backfillService;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {

        boolean dryRun = environment.getProperty(
                "payroll.kpi-backfill.dry-run", Boolean.class, true
        );

        log.warn("=== Salary-KPI legacy backfill starting (dryRun={}) ===", dryRun);

        try {
            BackfillResult result = backfillService.backfill(dryRun);

            log.warn(
                    "=== Salary-KPI legacy backfill finished: scanned={}, skippedNoMeasurement={}, backfilled={}, dryRun={} ===",
                    result.scanned(), result.skippedNoMeasurement(), result.backfilled(), result.dryRun()
            );

            if (dryRun) {
                log.warn("This was a DRY RUN — no data was written. Set payroll.kpi-backfill.dry-run=false to apply.");
            } else {
                log.warn("Backfill WROTE data. Set payroll.kpi-backfill.enabled=false and delete this runner + the backfill service now.");
            }
        } catch (Exception e) {
            log.error("Salary-KPI legacy backfill failed — application continuing to start normally.", e);
        }
    }
}