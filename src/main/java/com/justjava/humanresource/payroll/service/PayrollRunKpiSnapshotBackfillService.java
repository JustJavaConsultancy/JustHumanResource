package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.kpi.entity.KpiMeasurement;
import com.justjava.humanresource.kpi.service.KpiMeasurementService;
import com.justjava.humanresource.payroll.entity.PayrollRun;
import com.justjava.humanresource.payroll.entity.PayrollRunKpiSnapshot;
import com.justjava.humanresource.payroll.repositories.PayrollRunKpiSnapshotRepository;
import com.justjava.humanresource.payroll.repositories.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * One-time / on-demand backfill of {@link PayrollRunKpiSnapshot} rows for
 * POSTED {@link PayrollRun}s that predate the salary-impact-KPI-on-payslip
 * feature.
 *
 * <p><b>Not audit-perfect history:</b> this reconstructs salary KPI data
 * from TODAY'S {@code KpiMeasurement} / {@code KpiDefinition} tables, so it
 * can be wrong if those changed after the original payroll run was
 * calculated (KPI renamed, target changed, impactSalary flag changed,
 * measurement edited/deleted). Every row produced here is tagged
 * {@code snapshotSource = "LEGACY_BACKFILL"}, distinct from rows written
 * at calculation time ({@code "CALCULATION"}).
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Only POSTED runs are touched.</li>
 *   <li>Runs that already have snapshot rows are never overwritten
 *       (excluded at the query level).</li>
 *   <li>Runs with no salary-impacting measurements are left alone —
 *       {@code salaryKpiScore} stays null, no snapshot rows created.</li>
 * </ul>
 *
 * <p>Do NOT wire this into a scheduler or run it automatically on startup.
 * Expose it through an admin-only, explicitly-triggered endpoint or action,
 * and run with {@code dryRun = true} first.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PayrollRunKpiSnapshotBackfillService {

    private static final String LEGACY_SOURCE = "LEGACY_BACKFILL";
    private static final int PAGE_SIZE = 200;

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollRunKpiSnapshotRepository snapshotRepository;
    private final KpiMeasurementService kpiMeasurementService;

    /**
     * @param dryRun when true, nothing is written; the returned summary
     *               reports what WOULD have happened.
     */
    public BackfillResult backfill(boolean dryRun) {

        int scanned = 0;
        int skippedNoMeasurement = 0;
        int backfilled = 0;
        int pageIndex = 0;

        List<PayrollRun> content;

        do {
            // When actually writing, backfilled runs drop out of this query
            // immediately, so we always re-pull page 0. In dry-run mode
            // nothing changes underneath us, so we advance the page index.
            Page<PayrollRun> resultPage = payrollRunRepository.findPostedRunsWithoutKpiSnapshot(
                    PageRequest.of(dryRun ? pageIndex : 0, PAGE_SIZE)
            );
            content = resultPage.getContent();

            for (PayrollRun run : content) {
                scanned++;

                YearMonth period = YearMonth.from(run.getPayrollDate());

                List<KpiMeasurement> salaryImpacting =
                        kpiMeasurementService.getSalaryImpactingMeasurementsForEmployee(
                                run.getEmployee().getId(),
                                period
                        );

                if (salaryImpacting.isEmpty()) {
                    skippedNoMeasurement++;
                    continue;
                }

                BigDecimal score = kpiMeasurementService.calculateSalaryImpactScore(salaryImpacting);

                if (!dryRun) {
                    List<PayrollRunKpiSnapshot> snapshots = new ArrayList<>();
                    for (KpiMeasurement m : salaryImpacting) {
                        PayrollRunKpiSnapshot snapshot = new PayrollRunKpiSnapshot();
                        snapshot.setPayrollRun(run);
                        snapshot.setEmployee(run.getEmployee());
                        snapshot.setPeriod(period);
                        snapshot.setKpiIdSnapshot(m.getKpi().getId());
                        snapshot.setKpiCode(m.getKpi().getCode());
                        snapshot.setKpiName(m.getKpi().getName());
                        snapshot.setKpiUnit(m.getKpi().getUnit() != null ? m.getKpi().getUnit().name() : null);
                        snapshot.setTargetValue(m.getKpi().getTargetValue());
                        snapshot.setActualValue(m.getActualValue());
                        snapshot.setScore(m.getScore());
                        snapshot.setImpactSalary(true);
                        snapshot.setSnapshotSource(LEGACY_SOURCE);
                        snapshots.add(snapshot);
                    }
                    snapshotRepository.saveAll(snapshots);

                    run.setSalaryKpiScore(score);
                    payrollRunRepository.save(run);
                }

                backfilled++;
            }

            pageIndex++;
        } while (!content.isEmpty());

        return new BackfillResult(scanned, skippedNoMeasurement, backfilled, dryRun);
    }

    /**
     * @param scanned                total POSTED runs without existing snapshots examined
     * @param skippedNoMeasurement   runs with no salary-impacting measurement for their period
     * @param backfilled             runs that got (or, in dry-run, would get) snapshot rows + salaryKpiScore
     * @param dryRun                 whether this was a dry run
     */
    public record BackfillResult(int scanned, int skippedNoMeasurement, int backfilled, boolean dryRun) {}
}