# Salary-Impact KPI On Payslips - Full Implementation Plan

**How to use this document:** Give the coding AI **Step 0** first every time you start a fresh session with it. The AI you are giving this to does not have access to the codebase, so each step lists the existing files you must paste in, the files it should create, the files it should edit, and exactly what behavior to implement. Work through the steps in order. Do not skip the verification checklist for each step.

---

## Issues, Findings, And Solution Summary

### What We Want To Add

Some KPI definitions have an `Impact Salary` checkbox. When KPI measurements are recorded for those salary-impacting KPIs, payroll uses the KPI score to calculate the employee's salary for that payroll period.

The payroll calculation is already working. The missing feature is visibility: the salary-impacting KPI that affected a payslip should be shown on every payslip surface.

The KPI display should appear:

- On the employee self-service payroll page `employees/payroll.html`, in the main current payslip view, before the Gross Pay / Net Pay headline section.
- In the employee self-service current payslip modal, before Pension Contributions.
- In the employee self-service previous payslip modals, before Pension Contributions.
- In the HR payroll page current payslip modals in `payroll/fragments/employee-payroll.html`, before Pension Contributions.
- In the HR payroll page previous payslip modals, before Pension Contributions.
- In the server-generated PDF used for emailed payslips, before Pension Contributions.
- In downloadable PDFs generated from the browser modal. These are screenshots of the modal, so they will be covered once the modal HTML is updated.

The KPI section must only appear when that particular payslip has one or more salary-impacting KPI snapshots. If no salary-impacting KPI score was recorded for that payslip, do not show the section at all.

### Recommended Payslip Wording

Do not display `actualValue%` as the main salary-impact value. The current payroll logic does not use raw `actualValue` directly. It uses normalized `score`, calculated as:

```text
(actualValue / targetValue) * 100, capped at 100
```

So the payslip should show the salary-impact KPI score, not blindly append `%` to the raw actual value.

For one salary-impact KPI, use:

```text
Salary KPI: Monthly Target - 60%
This payslip was calculated using the salary-impact KPI score.
```

For multiple salary-impact KPIs, use:

```text
Salary KPI
Monthly Target - 60%
Quality Score - 80%

Applied salary KPI score: 70%
This payslip was calculated using the average salary-impact KPI score.
```

Implementation rule:

- Display each salary-impact KPI line as `KPI name - score%`.
- If there is exactly one salary-impact KPI snapshot, label the section `Salary KPI`.
- If there is more than one, still label the section `Salary KPI`, list all KPI lines, then show `Applied salary KPI score: X%`.
- Add the explanatory note under the KPI lines.
- The note should not appear when the whole KPI section is hidden.

### Current Codebase Findings

The salary-impact calculation currently lives in:

- `src/main/java/com/justjava/humanresource/kpi/service/KpiMeasurementService.java`

The important method is:

```java
getEmployeeKpiScore(Long employeeId, YearMonth period)
```

It:

- Loads KPI measurements for the employee and period.
- Filters only measurements where `m.getKpi().isImpactSalary()` is true.
- If none exist, returns `100`.
- If some exist, averages their normalized `score`.

Payroll applies that score in:

- `src/main/java/com/justjava/humanresource/payroll/workflow/impl/PayrollOrchestrationServiceImpl.java`

In the gross-based payroll path, it does:

```java
BigDecimal kpiScore = kpiMeasurementService.getEmployeeKpiScore(employee.getId(), YearMonth.from(payrollDate));
BigDecimal performanceFactor = kpiScore.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
grossPay = configuredGross.multiply(performanceFactor).setScale(2, RoundingMode.HALF_UP);
```

The existing payslip DTOs do not carry KPI data:

- `src/main/java/com/justjava/humanresource/payroll/entity/PaySlipDTO.java`
- `src/main/java/com/justjava/humanresource/payroll/dto/PayrollRunDTO.java`

The existing `PaySlip` entity only snapshots:

- employee
- payroll run
- pay date
- gross pay
- total deductions
- net pay
- version number

It does not snapshot KPI name, KPI score, actual value, target value, or whether a KPI affected salary.

The server-generated PDF is:

- `src/main/java/com/justjava/humanresource/utils/PaySlipPdfService.java`

The emailed payslip flow uses that server PDF through:

- `src/main/java/com/justjava/humanresource/utils/PaySlipEmailService.java`

The HR payroll page uses:

- `src/main/java/com/justjava/humanresource/payroll/PayrollController.java`
- `src/main/resources/templates/payroll/fragments/employee-payroll.html`

The employee self-service payroll page uses:

- `src/main/java/com/justjava/humanresource/employee/EmployeeController.java`
- `src/main/resources/templates/employees/payroll.html`

### Why A Snapshot Is Required

If the payslip display looks up KPI measurements live every time the page opens, old payslips can become historically wrong.

Examples:

- A KPI definition name is edited after payroll was posted.
- A KPI target value is edited after payroll was posted.
- A KPI measurement is edited after payroll was posted.
- A salary-impact KPI is later unchecked or deleted.
- A past payslip is viewed months later.

For audit-quality payroll, the payslip should display the KPI information that was used when that payroll run was calculated, not whatever the KPI tables say today.

Therefore, store a payroll-run KPI snapshot at calculation time. Payslips should display from that snapshot only.

### Overall Solution

Implement this in backend and frontend steps:

1. Add a payroll-run KPI snapshot entity and repository.
2. Add a small DTO for showing salary-impact KPI snapshots on payslips.
3. Add KPI service methods that return the exact salary-impacting measurements and compute the applied salary KPI score from them.
4. During payroll calculation, snapshot salary-impacting KPI lines onto the payroll run and store the applied salary KPI score on the payroll run.
5. Update payslip and payroll-run DTOs to carry salary KPI display data.
6. Update `PaySlipServiceImpl` and `PayrollRunServiceImpl` to load snapshot data into DTOs.
7. Update `PayrollController` where it rebuilds `PaySlipDTO` objects so the KPI fields are not dropped.
8. Update the employee self-service payroll page main view and modals.
9. Update the HR employee-payroll modals.
10. Update the server-side PDF generator used by email.
11. Add optional one-time legacy backfill only if the business wants old pre-feature payslips to show historical KPI data.
12. Add focused tests and manual QA for current/past, single/multiple KPI, and no-KPI cases.

---

## Business Rules

### Display Rules

- Show the Salary KPI section only if the payslip DTO has at least one salary-impact KPI snapshot.
- Hide the entire Salary KPI section if the list is null or empty.
- Do not show a placeholder such as `No KPI`.
- Do not show a default `100%` KPI section when no salary-impact KPI measurement exists.
- Do not use live KPI measurement lookup directly in templates.
- Do not use live KPI measurement lookup directly in `PaySlipPdfService`.
- Do not display KPI definitions that are salary-impacting but have no measurement for the payroll period.
- Do not display KPI measurements for KPIs where `impactSalary` was false at calculation time.

### Calculation Rules

- Preserve existing payroll calculation behavior.
- Existing fallback remains: no salary-impacting KPI measurement means payroll uses neutral score `100`.
- That neutral fallback must not create a visible payslip KPI section.
- If one salary-impacting KPI measurement exists, applied salary KPI score is that measurement's normalized `score`.
- If multiple salary-impacting KPI measurements exist, applied salary KPI score is their average, rounded exactly like the existing calculation.
- Gross-based payroll continues to use the applied salary KPI score as `score / 100`.
- Basic-based payroll should not be behaviorally changed. If the current code only applies KPI to gross-based payroll, do not expand KPI impact to basic-based payroll unless separately requested.

### Snapshot Rules

- Create salary KPI snapshots during payroll calculation, not while rendering the payslip.
- Snapshot rows belong to a specific `PayrollRun`, so each payroll version has its own KPI audit data.
- If a posted payroll run is amended and recalculated, the amendment run should have its own snapshots.
- Do not overwrite old posted run snapshots when creating a new payroll run version.
- If an in-progress run is recalculated, replace that in-progress run's KPI snapshots with fresh snapshots.
- If there are no salary-impacting measurements for the period, delete any existing in-progress snapshots for that run and set the run's applied salary KPI score to null.
- Do not blindly copy old KPI snapshots during period carry-forward. A new period should only show a Salary KPI section when that period's own payroll calculation captured salary-impacting KPI data.

### Historic Payslip Rules

- For payslips generated after this feature, display KPI data from the snapshot table.
- For old payslips that existed before this feature, there will be no snapshot rows. By default, do not display KPI for those payslips because the exact audit snapshot was not captured at posting time.
- If the business wants existing old payslips to show KPI, implement the optional legacy backfill step. The backfill should be clearly understood as reconstructing from existing KPI tables, not a perfect original audit record.

---

## Master File Manifest

| # | File | Action | Introduced In |
|---|------|--------|---------------|
| 1 | `src/main/java/com/justjava/humanresource/payroll/entity/PayrollRunKpiSnapshot.java` | NEW | Step 1 |
| 2 | `src/main/java/com/justjava/humanresource/payroll/repositories/PayrollRunKpiSnapshotRepository.java` | NEW | Step 1 |
| 3 | `src/main/java/com/justjava/humanresource/payroll/dto/SalaryImpactKpiSnapshotDTO.java` | NEW | Step 2 |
| 4 | `src/main/java/com/justjava/humanresource/payroll/service/PayrollRunKpiSnapshotService.java` | NEW | Step 2 |
| 5 | `src/main/java/com/justjava/humanresource/kpi/repositories/KpiMeasurementRepository.java` | EDIT | Step 3 |
| 6 | `src/main/java/com/justjava/humanresource/kpi/service/KpiMeasurementService.java` | EDIT | Step 3 |
| 7 | `src/main/java/com/justjava/humanresource/payroll/entity/PayrollRun.java` | EDIT | Step 4 |
| 8 | `src/main/java/com/justjava/humanresource/payroll/workflow/impl/PayrollOrchestrationServiceImpl.java` | EDIT | Step 4 |
| 9 | `src/main/java/com/justjava/humanresource/payroll/entity/PaySlipDTO.java` | EDIT | Step 5 |
| 10 | `src/main/java/com/justjava/humanresource/payroll/dto/PayrollRunDTO.java` | EDIT | Step 5 |
| 11 | `src/main/java/com/justjava/humanresource/payroll/service/impl/PaySlipServiceImpl.java` | EDIT | Step 6 |
| 12 | `src/main/java/com/justjava/humanresource/payroll/service/impl/PayrollRunServiceImpl.java` | EDIT | Step 6 |
| 13 | `src/main/java/com/justjava/humanresource/payroll/PayrollController.java` | EDIT | Step 7 |
| 14 | `src/main/resources/templates/employees/payroll.html` | EDIT | Step 8 |
| 15 | `src/main/resources/templates/payroll/fragments/employee-payroll.html` | EDIT | Step 9 |
| 16 | `src/main/java/com/justjava/humanresource/utils/PaySlipPdfService.java` | EDIT | Step 10 |
| 17 | `src/main/java/com/justjava/humanresource/utils/PaySlipEmailService.java` | REVIEW ONLY | Step 10 |
| 18 | `src/main/java/com/justjava/humanresource/workflow/delegate/CarryForwardRunsDelegate.java` | REVIEW ONLY | Step 11 |
| 19 | `src/main/java/com/justjava/humanresource/workflow/delegate/CarryForwardLineItemsDelegate.java` | REVIEW ONLY | Step 11 |
| 20 | Payroll/KPI tests under `src/test/java` | EDIT or NEW | Step 12 |

Files that should not need changes:

- `src/main/java/com/justjava/humanresource/payroll/entity/PaySlip.java`
- `src/main/java/com/justjava/humanresource/payroll/service/PaySlipService.java`
- `src/main/java/com/justjava/humanresource/utils/ResendService.java`
- `src/main/java/com/justjava/humanresource/payroll/entity/PayrollLineItem.java`
- `src/main/resources/processes/*`

Only change those files if compilation or tests prove the current implementation needs it.

---

## Step 0 - Context Package To Give The AI Before Every Step

Give this text to the coding AI before every implementation session:

```text
I'm working on a Spring Boot HR system called "humanresource", root package
com.justjava.humanresource. It uses Java, Spring Boot, Spring Data JPA,
Flowable, Thymeleaf, PDFBox, and Lombok.

Use existing project conventions:
- Services use @Service and often @RequiredArgsConstructor.
- Entities extend BaseEntity.
- Repositories extend JpaRepository.
- The project currently relies on Hibernate ddl-auto: update in application.yml.
- Payroll runs are versioned by employee and payroll period.
- PaySlipDTO is in package com.justjava.humanresource.payroll.entity.
- PayrollRunDTO is in package com.justjava.humanresource.payroll.dto.
- The employee self-service payroll page is templates/employees/payroll.html.
- The HR payroll employee fragment is templates/payroll/fragments/employee-payroll.html.
- Emailed payslip PDFs are generated by PaySlipPdfService.

Business requirement:
- Some KPI definitions have impactSalary=true.
- Payroll already uses salary-impacting KPI measurements to calculate gross-based salary.
- Add visibility of the salary-impact KPI on payslips.
- Show the Salary KPI section only when that exact payslip/payroll run has salary-impact KPI snapshot data.
- Do not show a default 100% KPI section when no salary-impact KPI measurement exists.
- Do not recalculate or live-read KPI data when rendering old payslips.
- Snapshot salary-impact KPI data at payroll calculation time for audit correctness.
- Display each KPI line as "KPI name - score%".
- If multiple salary-impact KPIs exist, also show "Applied salary KPI score: X%".
- Add note: "This payslip was calculated using the salary-impact KPI score."
- Place Salary KPI before Pension Contributions in payslip modals and PDFs.
- Place Salary KPI before Gross Pay / Net Pay in the employee self-service main payroll page.
- Do not change the existing payroll calculation outcome except to store snapshot fields.
- Do not change allowance, deduction, pension, PAYE, email sending, or payroll workflow behavior beyond this feature.
```

---

## Step 1 - Add Payroll Run KPI Snapshot Persistence

**Goal:** Add an audit-safe table that stores the KPI data used by a specific payroll run.

### Files To Give The AI

Paste these files in full:

- `src/main/java/com/justjava/humanresource/payroll/entity/PayrollRun.java`
- `src/main/java/com/justjava/humanresource/kpi/entity/KpiMeasurement.java`
- `src/main/java/com/justjava/humanresource/kpi/entity/KpiDefinition.java`
- `src/main/java/com/justjava/humanresource/hr/entity/Employee.java`
- `src/main/java/com/justjava/humanresource/core/entity/BaseEntity.java`
- `src/main/java/com/justjava/humanresource/payroll/entity/PayrollLineItem.java`
- `src/main/java/com/justjava/humanresource/payroll/repositories/PayrollLineItemRepository.java`
- `src/main/resources/application.yml`

### Files The AI Should Create

- `src/main/java/com/justjava/humanresource/payroll/entity/PayrollRunKpiSnapshot.java`
- `src/main/java/com/justjava/humanresource/payroll/repositories/PayrollRunKpiSnapshotRepository.java`

### Files The AI Should Edit

- None in this step unless imports or package paths require it.

### What To Create

Create `PayrollRunKpiSnapshot` in package:

```java
com.justjava.humanresource.payroll.entity
```

Recommended fields:

```java
@ManyToOne(optional = false)
@JoinColumn(name = "payroll_run_id", nullable = false)
private PayrollRun payrollRun;

@ManyToOne(optional = false)
@JoinColumn(name = "employee_id", nullable = false)
private Employee employee;

@Column(name = "period", nullable = false)
private YearMonth period;

@Column(name = "kpi_id_snapshot")
private Long kpiIdSnapshot;

@Column(name = "kpi_code")
private String kpiCode;

@Column(name = "kpi_name", nullable = false)
private String kpiName;

@Column(name = "kpi_unit")
private String kpiUnit;

@Column(name = "target_value", precision = 19, scale = 4)
private BigDecimal targetValue;

@Column(name = "actual_value", precision = 19, scale = 4)
private BigDecimal actualValue;

@Column(name = "score", precision = 7, scale = 2, nullable = false)
private BigDecimal score;

@Column(name = "impact_salary", nullable = false)
private boolean impactSalary = true;

@Column(name = "snapshot_source", nullable = false)
private String snapshotSource = "CALCULATION";
```

Use Lombok style matching nearby entities:

```java
@Getter
@Setter
@Entity
@Table(...)
public class PayrollRunKpiSnapshot extends BaseEntity
```

Recommended table name:

```java
payroll_run_kpi_snapshots
```

Recommended indexes:

- `payroll_run_id`
- `employee_id, period`

Recommended unique constraint:

- `payroll_run_id, kpi_id_snapshot`

Important:

- Do not use a required foreign key to `KpiDefinition`. The point of this table is to survive KPI definition changes or deletion.
- Store the KPI id as a scalar snapshot field only.
- Store name/code/unit/target/actual/score as scalar values.
- Use nullable `kpiIdSnapshot` only if the current schema or deleted KPI cases require it. Normal calculation snapshots should have it.

Create `PayrollRunKpiSnapshotRepository` in package:

```java
com.justjava.humanresource.payroll.repositories
```

Required methods:

```java
List<PayrollRunKpiSnapshot> findByPayrollRunIdOrderByKpiNameAsc(Long payrollRunId);

void deleteByPayrollRunId(Long payrollRunId);

boolean existsByPayrollRunId(Long payrollRunId);
```

Optional useful method for backfill/diagnostics:

```java
List<PayrollRunKpiSnapshot> findByEmployeeIdAndPeriodOrderByKpiNameAsc(Long employeeId, YearMonth period);
```

### Verification Before Moving On

- Entity compiles.
- Repository compiles.
- No existing code path is changed yet.
- Hibernate can create the new table in local development.

---

## Step 2 - Add Payslip KPI DTO And Snapshot Service

**Goal:** Provide a small display DTO and central service so mapping logic is not duplicated everywhere.

### Files To Give The AI

Paste these files in full:

- `src/main/java/com/justjava/humanresource/payroll/entity/PayrollRunKpiSnapshot.java` after Step 1
- `src/main/java/com/justjava/humanresource/payroll/repositories/PayrollRunKpiSnapshotRepository.java` after Step 1
- `src/main/java/com/justjava/humanresource/kpi/entity/KpiMeasurement.java`
- `src/main/java/com/justjava/humanresource/kpi/entity/KpiDefinition.java`
- `src/main/java/com/justjava/humanresource/kpi/enums/KpiUnit.java`

### Files The AI Should Create

- `src/main/java/com/justjava/humanresource/payroll/dto/SalaryImpactKpiSnapshotDTO.java`
- `src/main/java/com/justjava/humanresource/payroll/service/PayrollRunKpiSnapshotService.java`

### What To Create

Create `SalaryImpactKpiSnapshotDTO` in package:

```java
com.justjava.humanresource.payroll.dto
```

Recommended fields:

```java
Long kpiId;
String kpiCode;
String kpiName;
String kpiUnit;
BigDecimal targetValue;
BigDecimal actualValue;
BigDecimal score;
String snapshotSource;
```

Use a compact Lombok DTO style already used in the project:

```java
@Value
@Builder
public class SalaryImpactKpiSnapshotDTO { ... }
```

Create `PayrollRunKpiSnapshotService` in package:

```java
com.justjava.humanresource.payroll.service
```

Recommended responsibilities:

1. Replace snapshots for an in-progress payroll run.
2. Convert snapshot rows to `SalaryImpactKpiSnapshotDTO`.
3. Return an empty list when no snapshots exist.

Recommended public methods:

```java
@Transactional
public void replaceSnapshots(PayrollRun run, List<KpiMeasurement> measurements, BigDecimal appliedSalaryKpiScore)

@Transactional(readOnly = true)
public List<SalaryImpactKpiSnapshotDTO> getSnapshotsForPayrollRun(Long payrollRunId)
```

Behavior for `replaceSnapshots(...)`:

- Delete existing snapshot rows for the payroll run.
- If `measurements` is null or empty:
  - set `run.setSalaryKpiScore(null)` after Step 4 adds the field
  - return without creating snapshots
- If there are measurements:
  - create one `PayrollRunKpiSnapshot` row per measurement
  - copy `employee`, `period`, KPI id/code/name/unit/target, actual value, score
  - set `snapshotSource = "CALCULATION"`
  - set `impactSalary = true`
  - set `run.setSalaryKpiScore(appliedSalaryKpiScore)` after Step 4 adds the field

Important:

- If Step 4 has not added `salaryKpiScore` to `PayrollRun` yet, either add the field in Step 2 with the entity edit or leave a TODO and complete it in Step 4. Prefer adding it in Step 4 so this step stays focused.
- The service should not decide which measurements affect salary. It should only snapshot the measurements passed to it.
- The service should not fetch live KPI measurements for rendering. Rendering uses snapshot rows only.

### Verification Before Moving On

- DTO compiles.
- Service compiles after Step 4, or has a clearly marked TODO if the run field is not added yet.
- No template changes yet.

---

## Step 3 - Add Detailed Salary-Impact KPI Lookup

**Goal:** Let payroll calculation fetch both the applied score and the KPI lines that must be snapshotted.

### Files To Give The AI

Paste these files in full:

- `src/main/java/com/justjava/humanresource/kpi/repositories/KpiMeasurementRepository.java`
- `src/main/java/com/justjava/humanresource/kpi/service/KpiMeasurementService.java`
- `src/main/java/com/justjava/humanresource/kpi/entity/KpiMeasurement.java`
- `src/main/java/com/justjava/humanresource/kpi/entity/KpiDefinition.java`

### Files The AI Should Edit

- `src/main/java/com/justjava/humanresource/kpi/repositories/KpiMeasurementRepository.java`
- `src/main/java/com/justjava/humanresource/kpi/service/KpiMeasurementService.java`

### What To Do

In `KpiMeasurementRepository`, add a query that fetches the KPI definition eagerly:

```java
@Query("""
   SELECT m
   FROM KpiMeasurement m
   JOIN FETCH m.kpi k
   JOIN FETCH m.employee e
   WHERE e.id = :employeeId
   AND m.period = :period
   AND k.impactSalary = true
   ORDER BY k.name ASC
   """)
List<KpiMeasurement> findSalaryImpactingDetailedByEmployeeAndPeriod(
        @Param("employeeId") Long employeeId,
        @Param("period") YearMonth period
);
```

In `KpiMeasurementService`, add:

```java
@Transactional(readOnly = true)
public List<KpiMeasurement> getSalaryImpactingMeasurementsForEmployee(Long employeeId, YearMonth period)
```

This method should:

- Validate `employeeId` and `period`.
- Use the new repository query.
- Return an empty list when none exist.

Also add:

```java
public BigDecimal calculateSalaryImpactScore(List<KpiMeasurement> salaryImpactingMeasurements)
```

This method should:

- Return `BigDecimal.valueOf(100)` when the list is null or empty.
- Sum `m.getScore()`.
- Divide by list size with scale `2` and `RoundingMode.HALF_UP`.
- Match the current behavior of `getEmployeeKpiScore(...)`.

Then refactor existing `getEmployeeKpiScore(...)` to call these methods:

```java
List<KpiMeasurement> salaryImpacting = getSalaryImpactingMeasurementsForEmployee(employeeId, period);
return calculateSalaryImpactScore(salaryImpacting);
```

Important:

- Preserve existing calculation behavior exactly.
- Do not switch to `getEffectiveMeasurementsForEmployee(...)` unless the business explicitly wants that behavior change. Existing payroll calculation currently uses all employee measurements for the period where the KPI definition has `impactSalary=true`.
- Keep the neutral fallback of `100` for calculation.
- The neutral fallback should not create snapshot rows later.

### Verification Before Moving On

- Existing KPI measurement features still compile.
- Existing `getEmployeeKpiScore(...)` returns the same result as before.
- The new detailed lookup returns only salary-impacting KPI measurements.

---

## Step 4 - Snapshot KPI Data During Payroll Calculation

**Goal:** Store the salary-impact KPI snapshot when payroll calculates earnings.

### Files To Give The AI

Paste these files in full:

- `src/main/java/com/justjava/humanresource/payroll/workflow/impl/PayrollOrchestrationServiceImpl.java`
- `src/main/java/com/justjava/humanresource/payroll/entity/PayrollRun.java`
- `src/main/java/com/justjava/humanresource/payroll/repositories/PayrollRunRepository.java`
- `src/main/java/com/justjava/humanresource/kpi/service/KpiMeasurementService.java` after Step 3
- `src/main/java/com/justjava/humanresource/payroll/service/PayrollRunKpiSnapshotService.java` after Step 2
- `src/main/java/com/justjava/humanresource/workflow/delegate/CarryForwardRunsDelegate.java`
- `src/main/java/com/justjava/humanresource/workflow/delegate/CarryForwardLineItemsDelegate.java`

### Files The AI Should Edit

- `src/main/java/com/justjava/humanresource/payroll/entity/PayrollRun.java`
- `src/main/java/com/justjava/humanresource/payroll/workflow/impl/PayrollOrchestrationServiceImpl.java`

### What To Do In `PayrollRun`

Add a nullable column for the applied salary KPI score:

```java
@Column(name = "salary_kpi_score", precision = 7, scale = 2)
private BigDecimal salaryKpiScore;
```

Meaning:

- `null` means no salary-impact KPI measurement was captured for this payroll run.
- Non-null means the payroll run had salary-impact KPI measurements and this is the applied average score.

Do not default this field to `100`. If there was no salary-impact KPI measurement, it must remain null so the payslip section stays hidden.

### What To Do In `PayrollOrchestrationServiceImpl`

Inject:

```java
private final PayrollRunKpiSnapshotService payrollRunKpiSnapshotService;
```

In `calculateEarnings(Long payrollRunId)`, find the gross-based path where the current code calls:

```java
BigDecimal kpiScore = kpiMeasurementService.getEmployeeKpiScore(employee.getId(), YearMonth.from(payrollDate));
```

Replace that local logic with the detailed flow:

```java
YearMonth payrollPeriod = YearMonth.from(payrollDate);
List<KpiMeasurement> salaryImpactingMeasurements =
        kpiMeasurementService.getSalaryImpactingMeasurementsForEmployee(employee.getId(), payrollPeriod);

BigDecimal kpiScore =
        kpiMeasurementService.calculateSalaryImpactScore(salaryImpactingMeasurements);

payrollRunKpiSnapshotService.replaceSnapshots(run, salaryImpactingMeasurements, kpiScore);
```

Then continue using `kpiScore` exactly as the current code does:

```java
BigDecimal performanceFactor = kpiScore.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
grossPay = configuredGross.multiply(performanceFactor).setScale(2, RoundingMode.HALF_UP);
```

Also handle the basic-based path:

- If KPI does not affect basic-based payroll in the current system, do not change the calculation.
- Make sure old snapshots do not remain on an in-progress basic-based recalculation.
- Call `payrollRunKpiSnapshotService.replaceSnapshots(run, List.of(), null)` or equivalent in the basic-based path if a reused in-progress run might previously have snapshots.

Important:

- The snapshot should be written during calculation, before finalization.
- If the run is recalculated while `IN_PROGRESS`, replace its snapshots.
- Do not alter posted older versions.
- If an amendment run is created, it should get its own snapshots when `calculateEarnings` runs.
- If no salary-impacting measurements exist, `salaryKpiScore` should become null and no snapshot rows should remain for that run.

### Carry-Forward Warning

The project has:

- `CarryForwardRunsDelegate`
- `CarryForwardLineItemsDelegate`
- `PayrollRunRepository.bulkCarryForwardChunk(...)`

These copy posted runs/line items into a new period. Do not blindly copy `PayrollRunKpiSnapshot` rows into the new period.

Reason:

- The business rule is to show Salary KPI only when that payslip's own period has recorded salary-impact KPI data.
- A new period should not show last month's KPI name/score just because runs were carried forward.

If the new `salary_kpi_score` column is added to `payroll_runs`, ensure the native insert in `bulkCarryForwardChunk(...)` either:

- omits the column so it defaults to null, or
- explicitly inserts null for `salary_kpi_score`.

### Verification Before Moving On

- Gross-based payroll output remains unchanged for existing KPI cases.
- Gross-based payroll with no salary-impact KPI still uses 100 and creates no snapshots.
- Gross-based payroll with one salary-impact KPI creates one snapshot row and sets `run.salaryKpiScore`.
- Gross-based payroll with multiple salary-impact KPIs creates multiple snapshot rows and stores the average score.
- Basic-based payroll behavior is unchanged and does not accidentally show KPI.
- Recalculating an in-progress run replaces old snapshots.
- Amending a posted run creates snapshots on the new version only.

---

## Step 5 - Add Salary KPI Fields To DTOs

**Goal:** Carry snapshot data to all payslip templates and PDF generation.

### Files To Give The AI

Paste these files in full:

- `src/main/java/com/justjava/humanresource/payroll/entity/PaySlipDTO.java`
- `src/main/java/com/justjava/humanresource/payroll/dto/PayrollRunDTO.java`
- `src/main/java/com/justjava/humanresource/payroll/dto/SalaryImpactKpiSnapshotDTO.java` after Step 2

### Files The AI Should Edit

- `src/main/java/com/justjava/humanresource/payroll/entity/PaySlipDTO.java`
- `src/main/java/com/justjava/humanresource/payroll/dto/PayrollRunDTO.java`

### What To Do

Add these fields to both DTOs:

```java
private BigDecimal salaryKpiScore;
private List<SalaryImpactKpiSnapshotDTO> salaryImpactKpis;
```

For `PaySlipDTO`, it uses `@Value @Builder`, so add:

```java
BigDecimal salaryKpiScore;
List<SalaryImpactKpiSnapshotDTO> salaryImpactKpis;
```

For `PayrollRunDTO`, it uses mutable Lombok:

```java
private BigDecimal salaryKpiScore;
private List<SalaryImpactKpiSnapshotDTO> salaryImpactKpis;
```

Add the import in both files:

```java
import com.justjava.humanresource.payroll.dto.SalaryImpactKpiSnapshotDTO;
```

Optional convenience method:

```java
public boolean hasSalaryImpactKpi() {
    return salaryImpactKpis != null && !salaryImpactKpis.isEmpty();
}
```

Only add the convenience method if it fits cleanly. Templates can also check the list directly.

Important:

- Do not put KPI data into allowances or deductions.
- Do not fake KPI as a payroll line item.
- Do not include KPI in totals.
- The KPI section is explanatory/audit information only.

### Verification Before Moving On

- Both DTOs compile.
- Existing DTO constructors remain compatible.
- If `PayrollRunDTO` has a JPQL projection constructor, do not break it. Leave the new KPI fields null in projection constructor unless the query is updated separately.

---

## Step 6 - Populate Salary KPI Fields In Payslip And Payroll Run Mapping

**Goal:** Ensure all current/past payslip surfaces receive snapshot data through existing DTOs.

### Files To Give The AI

Paste these files in full:

- `src/main/java/com/justjava/humanresource/payroll/service/impl/PaySlipServiceImpl.java`
- `src/main/java/com/justjava/humanresource/payroll/service/impl/PayrollRunServiceImpl.java`
- `src/main/java/com/justjava/humanresource/payroll/entity/PaySlipDTO.java` after Step 5
- `src/main/java/com/justjava/humanresource/payroll/dto/PayrollRunDTO.java` after Step 5
- `src/main/java/com/justjava/humanresource/payroll/entity/PayrollRun.java` after Step 4
- `src/main/java/com/justjava/humanresource/payroll/service/PayrollRunKpiSnapshotService.java` after Step 2

### Files The AI Should Edit

- `src/main/java/com/justjava/humanresource/payroll/service/impl/PaySlipServiceImpl.java`
- `src/main/java/com/justjava/humanresource/payroll/service/impl/PayrollRunServiceImpl.java`

### What To Do In `PaySlipServiceImpl`

Inject:

```java
private final PayrollRunKpiSnapshotService payrollRunKpiSnapshotService;
```

In `mapToDto(PaySlip paySlip)`, after obtaining:

```java
PayrollRun run = paySlip.getPayrollRun();
```

load:

```java
List<SalaryImpactKpiSnapshotDTO> salaryImpactKpis =
        payrollRunKpiSnapshotService.getSnapshotsForPayrollRun(run.getId());
```

Then add to the builder:

```java
.salaryKpiScore(run.getSalaryKpiScore())
.salaryImpactKpis(salaryImpactKpis)
```

Important:

- Current and closed period payslips both use `mapToDto`, so this one mapping change should cover current/past `PaySlipDTO` flows.
- Do not query live `KpiMeasurement` in `mapToDto`.
- Do not calculate KPI score here.

### What To Do In `PayrollRunServiceImpl`

Inject:

```java
private final PayrollRunKpiSnapshotService payrollRunKpiSnapshotService;
```

In `buildDto(PayrollRun run)`, load snapshots:

```java
List<SalaryImpactKpiSnapshotDTO> salaryImpactKpis =
        payrollRunKpiSnapshotService.getSnapshotsForPayrollRun(run.getId());
```

Then add to the builder:

```java
.salaryKpiScore(run.getSalaryKpiScore())
.salaryImpactKpis(salaryImpactKpis)
```

Important:

- `EmployeeController.getPayroll(...)` uses `payrollRunService.getEmployeePayrollRun(...)` for the current employee self-service page, so `PayrollRunDTO` must be populated.
- HR current/past modals use `PaySlipDTO`.
- Employee previous modals use `PaySlipDTO`.

### Verification Before Moving On

- Employee current payroll DTO contains KPI snapshots when the current payroll run has snapshots.
- Current PaySlipDTO contains KPI snapshots.
- Past PaySlipDTO contains KPI snapshots.
- No KPI section data appears for runs without snapshot rows.

---

## Step 7 - Preserve KPI Fields In PayrollController DTO Rebuild

**Goal:** Avoid dropping KPI data when the HR payroll controller rebuilds current payslip DTOs to attach future allowances.

### Files To Give The AI

Paste these files in full:

- `src/main/java/com/justjava/humanresource/payroll/PayrollController.java`
- `src/main/java/com/justjava/humanresource/payroll/entity/PaySlipDTO.java` after Step 5

### Files The AI Should Edit

- `src/main/java/com/justjava/humanresource/payroll/PayrollController.java`

### What To Do

In `getEmployeePayroll(Model model)`, the controller gets current payslips and then rebuilds each `PaySlipDTO` with:

```java
return PaySlipDTO.builder()
    ...
    .futureAllowances(futureAllowances)
    ...
    .build();
```

Add the new KPI fields to that rebuild:

```java
.salaryKpiScore(ps.getSalaryKpiScore())
.salaryImpactKpis(ps.getSalaryImpactKpis())
```

Also review whether existing fields are already being dropped in this rebuild:

- `jobGradeName`
- `companyLogoData`
- `companyLogoContentType`

If those fields are needed for PDFs or future UI, preserve them too:

```java
.jobGradeName(ps.getJobGradeName())
.companyLogoData(ps.getCompanyLogoData())
.companyLogoContentType(ps.getCompanyLogoContentType())
```

Important:

- Do not change filtering/security behavior.
- Do not change future allowance behavior.
- This is only a DTO-copy preservation fix.

### Verification Before Moving On

- HR current payslip modals still receive future allowances.
- HR current payslip modals also receive salary KPI snapshots.
- No existing current payslip field is lost due to the rebuild.

---

## Step 8 - Update Employee Self-Service Payroll Page

**Goal:** Show Salary KPI on the employee payroll main page and employee current/past modals.

### Files To Give The AI

Paste these files in full:

- `src/main/resources/templates/employees/payroll.html`
- `src/main/java/com/justjava/humanresource/employee/EmployeeController.java`
- `src/main/java/com/justjava/humanresource/payroll/dto/PayrollRunDTO.java` after Step 5
- `src/main/java/com/justjava/humanresource/payroll/entity/PaySlipDTO.java` after Step 5

### Files The AI Should Edit

- `src/main/resources/templates/employees/payroll.html`

### What To Do In The Main Current Payslip View

Find the `Earnings Summary` card.

Inside it, find:

```html
<!-- Gross / Net headline figures -->
```

Add the Salary KPI block immediately before the Gross / Net block.

Use a condition like:

```html
th:if="${latestPaySlip.salaryImpactKpis != null and not #lists.isEmpty(latestPaySlip.salaryImpactKpis)}"
```

Recommended content:

```html
<div th:if="${latestPaySlip.salaryImpactKpis != null and not #lists.isEmpty(latestPaySlip.salaryImpactKpis)}"
     class="mb-5 p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-100 dark:border-blue-800 rounded-xl">
  <div class="flex items-center justify-between gap-3 mb-2">
    <span class="text-xs font-semibold text-blue-800 dark:text-blue-200 uppercase tracking-wider">Salary KPI</span>
    <span th:if="${latestPaySlip.salaryKpiScore != null and #lists.size(latestPaySlip.salaryImpactKpis) > 1}"
          class="text-xs font-semibold text-blue-700 dark:text-blue-300">
      Applied score:
      <span th:text="${#numbers.formatDecimal(latestPaySlip.salaryKpiScore, 0, 'COMMA', 2, 'POINT')}">0.00</span>%
    </span>
  </div>
  <div class="space-y-1">
    <div th:each="kpi : ${latestPaySlip.salaryImpactKpis}"
         class="flex justify-between text-sm">
      <span th:text="${kpi.kpiName}">Monthly Target</span>
      <span class="font-semibold">
        <span th:text="${#numbers.formatDecimal(kpi.score, 0, 'COMMA', 2, 'POINT')}">60.00</span>%
      </span>
    </div>
  </div>
  <p class="mt-2 text-xs text-blue-700 dark:text-blue-300">
    This payslip was calculated using the salary-impact KPI score.
  </p>
</div>
```

Adjust classes to match the existing page style if needed. Keep it compact.

### What To Do In Current Payslip Modal

Find the current modal:

```html
<div id="viewPayslipModal" class="modal-overlay">
```

Inside it, find:

```html
<!-- Pension Contributions Section -->
```

Add the Salary KPI block immediately before Pension Contributions.

Use `latestPaySlip.salaryImpactKpis` and `latestPaySlip.salaryKpiScore`.

Keep the same wording:

- Header: `Salary KPI`
- Lines: KPI name and score percent.
- Optional multiple-KPI summary: `Applied salary KPI score: X%`
- Note: `This payslip was calculated using the salary-impact KPI score.`

### What To Do In Previous Payslip Modals

Find:

```html
<th:block th:each="payslip : ${previousPaySlip}">
```

Inside each previous payslip modal, find:

```html
<!-- Pension Contributions Section -->
```

Add the Salary KPI block immediately before Pension Contributions.

Use:

```html
payslip.salaryImpactKpis
payslip.salaryKpiScore
```

Important:

- Do not show Salary KPI in the previous payslip table unless requested. The current requirement is for payslip views/modals/PDFs, not the table list.
- Browser-generated PDF downloads clone the modal HTML, so the modal block will appear in those PDFs automatically.
- Make sure the print/download action buttons remain hidden as before during print/download.

### Verification Before Moving On

- Employee payroll main page shows Salary KPI before Gross Pay / Net Pay only when current run has snapshots.
- Employee current modal shows Salary KPI before Pension Contributions only when snapshots exist.
- Employee past modal shows Salary KPI before Pension Contributions only when that past payslip's run has snapshots.
- Employee browser PDF download includes the KPI block because it captures modal HTML.
- No KPI block appears for payslips without snapshots.

---

## Step 9 - Update HR Employee Payroll Modals

**Goal:** Show Salary KPI in HR current and past payslip modals.

### Files To Give The AI

Paste these files in full:

- `src/main/resources/templates/payroll/fragments/employee-payroll.html`
- `src/main/java/com/justjava/humanresource/payroll/PayrollController.java` after Step 7
- `src/main/java/com/justjava/humanresource/payroll/entity/PaySlipDTO.java` after Step 5

### Files The AI Should Edit

- `src/main/resources/templates/payroll/fragments/employee-payroll.html`

### What To Do In Current Payslip Modals

Find current payslip modals:

```html
<th:block th:if="${currentPaySlips != null}" th:each="ps : ${currentPaySlips}">
```

Inside the modal, find:

```html
<!-- Pension Contributions Section -->
```

Add Salary KPI immediately before Pension Contributions.

Use:

```html
ps.salaryImpactKpis
ps.salaryKpiScore
```

The block can use inline styles because this template already uses many inline styles in the modal. Recommended structure:

```html
<div th:if="${ps.salaryImpactKpis != null and not #lists.isEmpty(ps.salaryImpactKpis)}"
     style="margin-bottom:1rem;padding:0.6rem 0.75rem;background:#eff6ff;border:1px solid #bfdbfe;border-radius:8px;">
  <h5 style="font-size:0.72rem;font-weight:600;color:#1e40af;text-transform:uppercase;letter-spacing:0.05em;margin:0 0 0.5rem;">Salary KPI</h5>
  <div th:each="kpi : ${ps.salaryImpactKpis}" class="ps-pay-item" style="font-size:0.875rem;">
    <span th:text="${kpi.kpiName}">Monthly Target</span>
    <span style="font-weight:600;">
      <span th:text="${#numbers.formatDecimal(kpi.score, 0, 'COMMA', 2, 'POINT')}">60.00</span>%
    </span>
  </div>
  <div th:if="${ps.salaryKpiScore != null and #lists.size(ps.salaryImpactKpis) > 1}"
       style="display:flex;justify-content:space-between;font-size:0.82rem;font-weight:600;margin-top:0.4rem;padding-top:0.4rem;border-top:1px solid #bfdbfe;color:#1d4ed8;">
    <span>Applied salary KPI score</span>
    <span><span th:text="${#numbers.formatDecimal(ps.salaryKpiScore, 0, 'COMMA', 2, 'POINT')}">70.00</span>%</span>
  </div>
  <p style="font-size:0.76rem;color:#1d4ed8;margin:0.5rem 0 0;">
    This payslip was calculated using the salary-impact KPI score.
  </p>
</div>
```

### What To Do In Past Payslip Modals

Find past payslip modals:

```html
<th:block th:if="${previousPeriods != null}">
```

Inside each modal, find:

```html
<!-- Pension Contributions Section -->
```

Add the same Salary KPI block immediately before Pension Contributions.

Use:

```html
paySlip.salaryImpactKpis
paySlip.salaryKpiScore
```

Important:

- Do not add a KPI column to the HR payroll tables unless separately requested.
- Do not affect email selection logic.
- Browser-generated PDF downloads from these modals will include the KPI block automatically.

### Verification Before Moving On

- HR current payslip modal shows Salary KPI before Pension Contributions when snapshots exist.
- HR past payslip modal shows Salary KPI before Pension Contributions when snapshots exist.
- HR modal browser PDF downloads include the Salary KPI block.
- Email buttons and modal open/download/print behavior still work.
- No Salary KPI block appears for payslips without snapshots.

---

## Step 10 - Update Server-Side PDF Used For Email

**Goal:** Ensure emailed PDF attachments include the Salary KPI section.

### Files To Give The AI

Paste these files in full:

- `src/main/java/com/justjava/humanresource/utils/PaySlipPdfService.java`
- `src/main/java/com/justjava/humanresource/utils/PaySlipEmailService.java`
- `src/main/java/com/justjava/humanresource/payroll/entity/PaySlipDTO.java` after Step 5
- `src/main/java/com/justjava/humanresource/payroll/dto/SalaryImpactKpiSnapshotDTO.java` after Step 2

### Files The AI Should Edit

- `src/main/java/com/justjava/humanresource/utils/PaySlipPdfService.java`

### Files The AI Should Review Only

- `src/main/java/com/justjava/humanresource/utils/PaySlipEmailService.java`

### What To Do

In `PaySlipPdfService.generate(PaySlipDTO paySlip)`, find this current order:

```java
renderer.text("Pension Scheme: " + value(paySlip.getAppliedPensionSchemeName()));
renderer.text("Bank: " + bank(paySlip));
renderer.gap(10);

renderer.section("Pension Contributions");
```

Insert Salary KPI after the bank/header lines and before Pension Contributions:

```java
List<SalaryImpactKpiSnapshotDTO> salaryImpactKpis = paySlip.getSalaryImpactKpis();
if (salaryImpactKpis != null && !salaryImpactKpis.isEmpty()) {
    renderer.section("Salary KPI");
    renderer.tableHeader("KPI", "Score");
    for (SalaryImpactKpiSnapshotDTO kpi : salaryImpactKpis) {
        renderer.tableRow(value(kpi.getKpiName()), percent(kpi.getScore()));
    }
    if (salaryImpactKpis.size() > 1 && paySlip.getSalaryKpiScore() != null) {
        renderer.tableRow("Applied salary KPI score", percent(paySlip.getSalaryKpiScore()));
    }
    renderer.text("This payslip was calculated using the salary-impact KPI score.");
    renderer.gap(8);
}

renderer.section("Pension Contributions");
```

Add helper:

```java
private static String percent(BigDecimal value) {
    BigDecimal safe = value != null ? value : BigDecimal.ZERO;
    NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
    format.setMinimumFractionDigits(2);
    format.setMaximumFractionDigits(2);
    return format.format(safe) + "%";
}
```

Review `PaySlipEmailService` only:

- It already calls `paySlipPdfService.generate(paySlip)`.
- If `PaySlipDTO` is populated correctly by `PaySlipServiceImpl`, no email service change should be required.

Important:

- Do not put Salary KPI after pension. The requirement is before pension.
- Do not add KPI text to the email body unless separately requested.
- Do not recalculate KPI in the PDF service.
- Do not query repositories from the PDF service.

### Verification Before Moving On

- Server-generated PDF includes Salary KPI before Pension Contributions when snapshots exist.
- Server-generated PDF omits Salary KPI completely when no snapshots exist.
- Email current payslips include the updated PDF.
- Email past payslips include the updated PDF.
- PDF still renders allowances, deductions, pension, totals, bank, and logo as before.

---

## Step 11 - Optional Legacy Backfill For Existing Old Payslips

**Goal:** Only if required, reconstruct snapshot rows for payslips that existed before this feature.

### When To Do This Step

Do this only if the business wants old closed payslips, created before this feature, to show Salary KPI where a matching salary-impacting KPI measurement exists.

Skip this step if the business accepts:

```text
Only payslips calculated after this feature will show audit-safe Salary KPI snapshots.
```

### Important Limitation

Backfill is not perfect audit history. It uses today's KPI measurement and KPI definition tables to reconstruct what probably affected that period.

It can be wrong if, after the original payroll:

- KPI definition names changed.
- KPI targets changed.
- KPI impactSalary flags changed.
- KPI measurements were edited.
- KPI definitions were deleted.

Therefore, if backfill is implemented, mark those rows with:

```text
snapshotSource = LEGACY_BACKFILL
```

### Files To Give The AI If Doing Backfill

Paste these files in full:

- `src/main/java/com/justjava/humanresource/payroll/entity/PaySlip.java`
- `src/main/java/com/justjava/humanresource/payroll/entity/PayrollRun.java`
- `src/main/java/com/justjava/humanresource/payroll/entity/PayrollRunKpiSnapshot.java`
- `src/main/java/com/justjava/humanresource/payroll/repositories/PaySlipRepository.java`
- `src/main/java/com/justjava/humanresource/payroll/repositories/PayrollRunRepository.java`
- `src/main/java/com/justjava/humanresource/payroll/repositories/PayrollRunKpiSnapshotRepository.java`
- `src/main/java/com/justjava/humanresource/kpi/repositories/KpiMeasurementRepository.java`
- `src/main/java/com/justjava/humanresource/kpi/service/KpiMeasurementService.java`

### Files The AI May Create

Choose one of these approaches:

Option A, admin/manual service:

- `src/main/java/com/justjava/humanresource/payroll/service/PayrollRunKpiSnapshotBackfillService.java`

Option B, one-off command runner guarded by property:

- `src/main/java/com/justjava/humanresource/payroll/bootstrap/PayrollRunKpiSnapshotBackfillRunner.java`

Prefer Option A unless the team explicitly wants an automatic startup runner.

### What Backfill Should Do

For each posted payroll run without snapshot rows:

1. Determine the period from `run.getPayrollDate()` or `YearMonth.from(run.getPayrollDate())`.
2. Load salary-impacting measurements for `run.employee.id` and that period.
3. If none exist:
   - leave `run.salaryKpiScore` null
   - create no snapshot rows
4. If measurements exist:
   - calculate average salary KPI score using the same service method
   - create snapshot rows with `snapshotSource = "LEGACY_BACKFILL"`
   - set `run.salaryKpiScore` to the average

Add a dry-run mode if exposing this as an admin operation.

Important:

- Do not run this automatically in production without explicit approval.
- Do not backfill unposted/in-progress runs.
- Do not backfill runs that already have snapshot rows.
- Do not overwrite calculation snapshots.

### Verification Before Moving On

- Backfill reports how many runs were scanned, skipped, and backfilled.
- Backfilled payslips show Salary KPI.
- Non-backfilled/no-measurement payslips still hide Salary KPI.
- Existing calculated snapshots are not overwritten.

---

## Step 12 - Tests

**Goal:** Add focused tests around snapshot creation, DTO mapping, and rendering decisions.

### Files To Give The AI

Paste these files in full if they exist:

- Existing KPI measurement service tests
- Existing payroll orchestration/calculation tests
- Existing payslip service tests
- Existing employee controller payroll tests
- Existing PDF service tests
- `src/main/java/com/justjava/humanresource/kpi/service/KpiMeasurementService.java`
- `src/main/java/com/justjava/humanresource/kpi/repositories/KpiMeasurementRepository.java`
- `src/main/java/com/justjava/humanresource/payroll/workflow/impl/PayrollOrchestrationServiceImpl.java`
- `src/main/java/com/justjava/humanresource/payroll/service/impl/PaySlipServiceImpl.java`
- `src/main/java/com/justjava/humanresource/payroll/service/impl/PayrollRunServiceImpl.java`
- `src/main/java/com/justjava/humanresource/payroll/service/PayrollRunKpiSnapshotService.java`
- `src/main/java/com/justjava/humanresource/payroll/entity/PayrollRun.java`
- `src/main/java/com/justjava/humanresource/payroll/entity/PayrollRunKpiSnapshot.java`
- `src/main/java/com/justjava/humanresource/payroll/entity/PaySlipDTO.java`
- `src/main/java/com/justjava/humanresource/payroll/dto/PayrollRunDTO.java`
- `src/main/java/com/justjava/humanresource/utils/PaySlipPdfService.java`

### Files The AI Should Create Or Edit

Preferred new test files:

- `src/test/java/com/justjava/humanresource/payroll/service/PayrollRunKpiSnapshotServiceTest.java`
- `src/test/java/com/justjava/humanresource/kpi/service/KpiMeasurementServiceSalaryImpactTest.java`
- `src/test/java/com/justjava/humanresource/payroll/service/impl/PaySlipServiceImplKpiSnapshotTest.java`
- `src/test/java/com/justjava/humanresource/utils/PaySlipPdfServiceKpiTest.java`

If the project already has better matching test classes, add focused tests there instead.

### Test Cases To Include

KPI score tests:

- No salary-impacting measurements returns calculation score `100`.
- No salary-impacting measurements produces no snapshot rows.
- One salary-impacting measurement returns its `score`.
- Multiple salary-impacting measurements return the rounded average score.
- Non-salary-impacting KPI measurements are ignored.
- Raw `actualValue` is not used as the salary percentage unless it also results in that normalized `score`.

Snapshot service tests:

- `replaceSnapshots` deletes old snapshots for the same in-progress run.
- `replaceSnapshots` creates one row per salary-impacting measurement.
- Snapshot stores KPI id, code, name, unit, target, actual, score, employee, period, and source.
- Empty measurements clear snapshots and clear `run.salaryKpiScore`.
- Existing snapshots for a different payroll run/version are not touched.

Payroll calculation tests:

- Gross-based payroll with salary KPI still calculates the same gross pay as before.
- Gross-based payroll with no salary KPI still calculates as 100%.
- Gross-based payroll stores `salaryKpiScore` only when salary-impacting measurements exist.
- Basic-based payroll behavior remains unchanged.
- Amendment run gets its own KPI snapshot.

DTO mapping tests:

- `PaySlipServiceImpl.mapToDto` includes salary KPI snapshots.
- `PaySlipServiceImpl.mapToDto` leaves salary KPI list empty when no snapshots exist.
- `PayrollRunServiceImpl.buildDto` includes salary KPI snapshots for employee current payroll page.
- `PayrollController` current payslip rebuild preserves salary KPI fields.

PDF tests:

- PDF generation output includes Salary KPI text when snapshots exist.
- PDF generation output includes KPI name and percent score.
- PDF generation output includes the note.
- PDF generation output omits Salary KPI when no snapshots exist.
- Pension Contributions still appears after Salary KPI.

Template/manual rendering tests:

- Employee self-service main page shows Salary KPI before Gross Pay / Net Pay.
- Employee current modal shows Salary KPI before Pension Contributions.
- Employee past modal shows Salary KPI before Pension Contributions.
- HR current modal shows Salary KPI before Pension Contributions.
- HR past modal shows Salary KPI before Pension Contributions.
- Browser modal PDF includes Salary KPI because it captures modal content.

### Verification Command

Run:

```powershell
.\mvnw test
```

If full test suite is slow, run the focused tests first, then run the full suite before merging.

---

## Step 13 - Manual End-To-End QA

Run these in a local or staging environment.

### Current Payroll, No Salary KPI Measurement

- [ ] Create or select employee with no salary-impacting KPI measurement for the current payroll period.
- [ ] Run payroll.
- [ ] Confirm salary calculation uses neutral 100 fallback as before.
- [ ] Confirm no `PayrollRunKpiSnapshot` rows exist for that payroll run.
- [ ] Employee payroll main page does not show Salary KPI.
- [ ] Employee current payslip modal does not show Salary KPI.
- [ ] HR current payslip modal does not show Salary KPI.
- [ ] Emailed current PDF does not show Salary KPI.

### Current Payroll, One Salary KPI Measurement

- [ ] Create KPI definition with `impactSalary=true`.
- [ ] Assign KPI to employee.
- [ ] Record measurement for current period.
- [ ] Run payroll.
- [ ] Confirm `PayrollRun.salaryKpiScore` equals the normalized score.
- [ ] Confirm one snapshot row exists.
- [ ] Employee payroll main page shows Salary KPI before Gross Pay / Net Pay.
- [ ] Employee current modal shows Salary KPI before Pension Contributions.
- [ ] HR current modal shows Salary KPI before Pension Contributions.
- [ ] Browser PDF download includes Salary KPI.
- [ ] Emailed PDF includes Salary KPI before Pension Contributions.

### Current Payroll, Multiple Salary KPI Measurements

- [ ] Create two or more KPI definitions with `impactSalary=true`.
- [ ] Record measurements for same employee and period.
- [ ] Run payroll.
- [ ] Confirm applied salary KPI score is the average of the KPI scores.
- [ ] Confirm all KPI lines are snapshotted.
- [ ] Confirm payslip shows each KPI line.
- [ ] Confirm payslip shows `Applied salary KPI score`.
- [ ] Confirm explanatory note appears once.

### Past Payslips

- [ ] Close a period after generating payslips with salary KPI snapshots.
- [ ] Open employee self-service past payslip modal.
- [ ] Confirm Salary KPI appears before Pension Contributions.
- [ ] Open HR past payslip modal.
- [ ] Confirm Salary KPI appears before Pension Contributions.
- [ ] Email selected past payslip.
- [ ] Confirm attached PDF contains Salary KPI before Pension Contributions.
- [ ] Confirm an older payslip with no snapshot does not show Salary KPI.

### Amendment/Recalculation

- [ ] Post payroll run with salary KPI score.
- [ ] Amend/recalculate while period is still open.
- [ ] Confirm new payroll run version has its own KPI snapshots.
- [ ] Confirm old posted version snapshots are not overwritten.
- [ ] Confirm latest payslip displays the latest run version snapshot.

### Carry-Forward

- [ ] Close/carry forward a period.
- [ ] Confirm KPI snapshots are not blindly copied from old period to new period.
- [ ] Confirm new period payslip only shows Salary KPI after payroll calculation captures current-period salary KPI measurements.

---

## Implementation Notes And Pitfalls

### Do Not Use Actual Value As The Percent

Bad display:

```text
KPI: Monthly Target - actualValue%
```

Why this is bad:

- KPI unit can be `NUMBER`, `HOURS`, or another unit.
- Target might not be 100.
- Salary uses normalized `score`, not raw `actualValue`.

Correct display:

```text
Salary KPI: Monthly Target - score%
```

Optional detail for later:

```text
Actual: 60 / Target: 100
```

Do not add this optional detail unless the product wants more detail on the payslip.

### Do Not Store KPI As Allowance Or Deduction

The Salary KPI section is explanatory. It should not affect totals, line-item reports, statutory deductions, pension reports, allowance totals, or journal entries.

### Do Not Live Query KPI In Templates

Templates should only use fields already on:

- `PaySlipDTO`
- `PayrollRunDTO`

### Be Careful With Existing DTO Rebuilds

`PayrollController.getEmployeePayroll(...)` rebuilds `PaySlipDTO` to add future allowances. Any new fields added to `PaySlipDTO` must be copied there, or HR current payslip modals will silently lose the KPI data.

### Be Careful With Historical Accuracy

Past payslips should display from `PayrollRunKpiSnapshot`, not current `KpiMeasurement`.

If there are no snapshot rows, hide the section.

### Be Careful With Null Versus 100

Calculation fallback:

```text
no salary-impact KPI measurement -> use 100 for payroll calculation
```

Display rule:

```text
no salary-impact KPI snapshot -> show nothing
```

So `PayrollRun.salaryKpiScore` must be nullable and should not default to 100.

---

## Final Expected Result

After implementation:

- Payroll calculation still works as before.
- Salary-impact KPI details are snapshotted per payroll run/version.
- Payslip rendering uses the snapshot, not live KPI tables.
- Current employee payroll main page shows Salary KPI before Gross Pay / Net Pay when applicable.
- Employee current payslip modal shows Salary KPI before Pension Contributions when applicable.
- Employee past payslip modals show Salary KPI before Pension Contributions when applicable.
- HR current payslip modals show Salary KPI before Pension Contributions when applicable.
- HR past payslip modals show Salary KPI before Pension Contributions when applicable.
- Browser-generated modal PDFs include Salary KPI because the modal contains it.
- Emailed server-generated PDFs include Salary KPI before Pension Contributions.
- Payslips without salary-impact KPI snapshots do not show any KPI section.
- Past payslips remain audit-safe because they display stored payroll-run KPI snapshots.
- Existing allowances, deductions, pension, PAYE, net pay, gross pay, and email behavior are not changed.
