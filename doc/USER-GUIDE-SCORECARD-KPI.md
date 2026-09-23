# Scorecard KPI User Guide

This guide explains how to use the KPI Scorecard module in JustHumanResource from setup to reporting. It is written for HR/KPI administrators, employees, managers, and HR leadership.

## Purpose Of The Scorecard KPI Module

The Scorecard KPI module helps the organisation:

- Design balanced scorecard templates using a hierarchy of **Perspective -> Objective -> Indicator**.
- Import scorecard templates from Excel.
- Publish versioned templates.
- Assign templates to employees, job steps, or departments.
- Let employees complete self-review and evidence submission.
- Let managers review, score, comment, and finalize KPI lines.
- Monitor template health, review queues, assignments, and imports.
- Generate performance reports, completion reports, and exports.

## User Roles

| Role | Main Responsibility |
| --- | --- |
| HR/KPI Admin | Designs templates, imports templates, publishes templates, assigns templates, monitors status, and exports data. |
| Employee | Reviews assigned KPI lines, enters self-score or rubric selection, adds comments, and submits evidence links. |
| Manager | Reviews employee KPI lines, selects rubric bands or enters scores, comments, and confirms final weighted score. |
| Executive/HR Leadership | Reviews dashboards, performance trends, department performance, rubric distribution, completion rates, and exports reports. |

## Before You Start

Make sure these records already exist:

| Required Record | Why It Is Needed |
| --- | --- |
| Employees | Required for employee scorecards and appraisals. |
| Departments | Required for department-level template assignment and reporting filters. |
| Job grades or job steps | Required for job-step or grade-level assignment and reporting filters. |
| KPI definitions | Required when building scorecard templates manually. |
| Appraisal cycles | Required for performance reporting by cycle. |
| Scoring rubrics | Optional, but recommended for consistent employee and manager scoring. |

> **Note:** If a department, job step, employee, or KPI definition is missing, ask the HR setup user or system administrator to create it first.

## Main Navigation

After signing in, open **KPI Management** from the main menu.

Common KPI Scorecard pages are:

| Page Or Area | How To Find It In The Application | Used By |
| --- | --- | --- |
| KPI Management | Main menu or sidebar, select **KPI Management**. This is the central KPI workspace. | HR/KPI Admin |
| Balanced Scorecard Designer | From **KPI Management**, select **Balanced Scorecards**. Use this page to design, import, publish, assign, and manage templates. | HR/KPI Admin |
| KPI Admin Monitoring Dashboard | From **Balanced Scorecards**, click **Admin Dashboard** near the top action buttons. Use it to monitor template status, assignments, and review queues. | HR/KPI Admin |
| Performance Reports | From **Balanced Scorecards**, click **Reports**, or open the reporting option from the KPI menu if shown. Use it for trends, averages, completion, and exports. | HR/KPI Admin, HR Leadership |
| Appraisal List | From **KPI Management**, select the **Appraisal** tab or appraisal menu item. Use it to find employee appraisals and review tasks. | Employees, Managers, HR/KPI Admin |
| Appraisal Scorecard Detail | From the **Appraisal** list, open an employee appraisal, then choose the scorecard/detail action. Use it to generate lines, self-score, manager-score, and review summaries. | Employees, Managers, HR/KPI Admin |

Exact menu labels may vary by role and deployment. If a menu item is not visible, confirm that the signed-in user has the correct role or permissions.

## End-To-End Scorecard Flow

The full scorecard process normally follows this order:

1. Prepare KPI definitions and scoring rubrics.
2. Create or import a scorecard template.
3. Validate template weights and hierarchy.
4. Save the template as a draft.
5. Publish the template.
6. Assign the published template to a scope.
7. Generate appraisal scorecard lines.
8. Employee completes self-review and evidence.
9. Manager reviews and scores KPI lines.
10. HR/KPI Admin monitors completion.
11. HR leadership reviews reports and exports.

## Step 1. Prepare KPI Definitions

**Where to go:** Open **KPI Management** from the main menu or sidebar, then click the **KPI Definitions** tab.

1. Sign in as HR/KPI Admin.
2. Open **KPI Management**.
3. Open the **KPI Definitions** tab.
4. Create the KPI definitions that will be used in templates.
5. For balanced scorecard templates, create definitions that represent:
   - Perspectives, such as Financial, Customer, Internal Process, Learning and Growth.
   - Objectives, such as Improve Employee Retention.
   - Indicators, such as Reduce Attrition Rate.
6. Confirm each KPI definition has a clear name, code, unit, target value, and active status.

### Expected Result

- The KPI definitions are available in the Scorecard Designer dropdown.
- Definitions can be selected when creating Perspective, Objective, and Indicator rows.

## Step 2. Create Or Edit Scoring Rubrics

**Where to go:** Open **KPI Management**, select **Balanced Scorecards**, then click the **Rubrics** tab.

1. Sign in as HR/KPI Admin.
2. Open **KPI Management**.
3. Open **Balanced Scorecards** from the KPI area.
4. Click the **Rubrics** tab.
5. Click **New** to create a new rubric, or click **Edit** beside an existing rubric.
6. Enter a rubric name and description.
7. Add score bands.
8. For each band, enter:
   - Label, such as Excellent, Good, Average, Needs Improvement.
   - Minimum score.
   - Maximum score.
   - Numeric score that should be applied when the band is selected.
   - Description that explains what the band means.
9. Click **Save Rubric**.

### Expected Result

- The rubric appears in the Rubric Library.
- The rubric can be selected as the default rubric for a scorecard template.
- During appraisal scoring, the rubric band can auto-fill the score.

## Step 3. Create A Scorecard Template Manually

**Where to go:** Open **KPI Management**, select **Balanced Scorecards**, then click the **Designer** tab.

1. Sign in as HR/KPI Admin.
2. Open **Balanced Scorecards**.
3. Click the **Designer** tab.
4. Enter the **Template Name**.
5. Enter the **Role Name** if the template is specific to a role.
6. Select a **Default Rubric** if one should apply.
7. Click **Perspective** to add a top-level row.
8. Select the KPI definition for the perspective.
9. Enter the perspective weight.
10. Click **Objective** to add a second-level row.
11. Select the KPI definition for the objective.
12. Select the perspective as its parent.
13. Enter the objective weight.
14. Click **Indicator** to add a measurable KPI line.
15. Select the KPI definition for the indicator.
16. Select the objective or perspective as its parent.
17. Enter the indicator weight.
18. Select frequency if applicable.
19. Mark the row as mandatory if it must always be reviewed.
20. Use the up and down controls to adjust row order.
21. Review the **Hierarchy Preview** and **Validation** panel.

### Weight Rules

- Root perspective weights must total **1.00**.
- Child rows under a parent must total the parent weight.
- Every row must have a positive weight.
- Every non-perspective row must have a parent.
- A KPI definition should not be selected more than once in the same template.

### Expected Result

- The hierarchy preview shows the scorecard tree.
- Validation badges show which rows are valid.
- The effective weight shows **1.00** when the template is balanced.

## Step 4. Save Draft Or Publish Template

**Where to go:** Open **Balanced Scorecards** from the KPI area, then use the **Designer** tab.

### Save Draft

Use **Save Draft** when the template is still being prepared.

1. Complete the required template name.
2. Add at least the rows you want to save.
3. Click **Save Draft**.

### Expected Result

- The template is saved as a draft.
- Draft templates can be edited again.
- Draft templates are not available for assignment until published.

### Publish

Use **Publish** when the template has been validated and is ready for use.

1. Confirm that validation shows no blocking errors.
2. Confirm that root weights total **1.00**.
3. Confirm that child weights match parent weights.
4. Click **Publish**.
5. Read the confirmation message.
6. Confirm publishing.

### Expected Result

- The template status becomes **Published**.
- A version snapshot is created.
- The template becomes available for assignment.
- Published templates are treated as immutable. To make changes, duplicate the template and publish a new version.

## Step 5. Review Template Library And Version History

**Where to go:** Open **Balanced Scorecards** from the KPI area, then click the **Library** tab.

1. Open **Balanced Scorecards**.
2. Click the **Library** tab.
3. Review templates by name, role, status, source, and total weight.
4. Click **Edit** to load a draft or existing template into the designer.
5. When a template is loaded, review the **Template Version History** panel.
6. Use **Duplicate** when you need a new draft based on an existing template.
7. Use **Archive** when a template should no longer be assigned.

### Expected Result

- Draft, published, and archived states are visible.
- Published version history is visible for templates that have been published.
- Archived templates are removed from normal active use.

## Step 6. Import A Scorecard From Excel

**Where to go:** Open **Balanced Scorecards** from the KPI area, then click the **Import & Apply** tab.

1. Open **Balanced Scorecards**.
2. Click **Import & Apply**.
3. In **Import Excel Scorecard**, enter the template name.
4. Enter the role name if applicable.
5. Choose the Excel file.
6. Click **Preview Import**.
7. Wait for the workbook preview to load.

### Preview And Correct Rows

After preview:

1. Review the detected template name, row count, and total weight points.
2. Check the blocking errors section.
3. Check warnings.
4. Review the duplicate detection panel.
5. Edit rows directly in the preview if a value is wrong.
6. Use **Add Row** if a missing row needs to be inserted.
7. Use **Remove** beside a row if it should not be imported.
8. Click **Download Error Report** if you need a CSV list of errors and warnings.

### Confirm Import

1. Resolve all blocking errors.
2. Confirm that preview weights total **100 points**.
3. Click **Confirm Preview**.

### Expected Result

- A new scorecard template is created from the preview.
- KPI definitions are created or reused.
- The import summary shows the number of definitions and template items created.
- The imported template appears in the Template Library.

## Step 7. Assign A Published Template

**Where to go:** Open **Balanced Scorecards** from the KPI area, then click the **Import & Apply** tab.

1. Open **Balanced Scorecards**.
2. Click **Import & Apply**.
3. In **Apply Template**, select a published template.
4. Select the assignment scope:
   - Job Step
   - Employee
   - Department
5. Select the specific job step, employee, or department.
6. Choose whether to **Replace existing assignments** in the selected scope.
7. Click **Preview Apply**.
8. Review:
   - Scope.
   - Template rows.
   - Assignments to add.
   - Assignments to remove.
   - Warnings.
9. Click **Apply Template**.

### Expected Result

- KPI assignments are created for the selected scope.
- Assignment history is updated.
- Employees in the selected scope can have KPI lines generated for appraisal.

## Step 8. Monitor Scorecard Administration

**Where to go:** Open **Balanced Scorecards**, then click **Admin Dashboard** in the top action area.

1. Open the **KPI Admin Monitoring Dashboard**.
2. Review template status counts:
   - Draft.
   - Published.
   - Archived.
3. Review active assignments by scope:
   - Employee.
   - Job step.
   - Department.
4. Review pending self-review count.
5. Review pending manager-review count.
6. Check recently imported templates.
7. Check recently published versions.
8. Review templates with weight or validation issues.
9. Open the designer if a template issue needs correction.

### Expected Result

- HR/KPI Admin can quickly see whether templates, assignments, and appraisal queues need attention.
- Validation issues are visible before they affect active appraisal operations.

## Step 9. Generate Appraisal Scorecard Lines

**Where to go:** Open **KPI Management**, select **Appraisal**, then open the employee appraisal scorecard.

1. Sign in as HR/KPI Admin or a manager with permission.
2. Open **KPI Management**.
3. Open **Appraisal**.
4. Find the employee appraisal.
5. Open the scorecard detail.
6. Click **Generate Lines**.

### Expected Result

- The system creates missing KPI appraisal lines from the employee's effective assignments.
- Existing lines are not duplicated.
- Measurement scores are pulled in where available.
- Weighted line score and perspective summary become available.

## Step 10. Employee Self-Review

**Where to go:** Open **KPI Management**, select **Appraisal**, find your appraisal record, then open the scorecard/detail action.

1. Sign in as the employee.
2. Open the appraisal or KPI scorecard page assigned to you.
3. Review the KPI lines.
4. For each line, read:
   - KPI name.
   - KPI code.
   - Weight.
   - Measurement score if available.
   - Rubric bands if available.
5. If rubric bands are available, select the band that matches your self-assessment.
6. When a rubric band is selected, the self-score is filled automatically.
7. If no rubric band is selected, enter a manual self-score between **0 and 100**.
8. Add a self-comment.
9. Add an evidence URL if supporting proof is required.
10. Click the employee/self save action for the line.
11. Repeat for all KPI lines.
12. Watch the completion progress indicator.

### Evidence Link Rules

- Use a valid `http://` or `https://` link.
- Link to accessible evidence, such as a document, ticket, report, dashboard, or project record.
- Do not paste private links unless the reviewer has access.

### Expected Result

- The line save status changes to **Saved**.
- The completion progress increases.
- The weighted score preview updates where applicable.

## Step 11. Manager Review And Scoring

**Where to go:** Open **KPI Management**, select **Appraisal**, find the employee appraisal assigned for manager review, then open the scorecard/detail action.

1. Sign in as the manager or HR/KPI Admin.
2. Open **KPI Management**.
3. Open **Appraisal**.
4. Open the employee's appraisal scorecard.
5. Review the employee's self-score, comment, and evidence.
6. Review measurement score where available.
7. For each KPI line, select a manager rubric band if available.
8. When a rubric band is selected, the manager score is filled automatically and manual entry is disabled.
9. If no rubric band is selected, enter a manager score between **0 and 100**.
10. Add a manager comment.
11. Save the manager line.
12. Review the weighted score preview.
13. Continue until all lines are reviewed.

### Expected Result

- Manager scores override self scores for final line scoring.
- The final weighted score preview updates.
- Perspective summary shows achieved score by perspective.
- Save status confirms each line has been saved.

## Step 12. Review Perspective Summary

**Where to go:** Open the employee's appraisal scorecard from the **Appraisal** list, then review the **Perspective Summary** section above the scorecard lines.

1. Open the employee scorecard.
2. Review the **Perspective Summary** section.
3. Check each perspective's:
   - Weight.
   - Possible score.
   - Achieved score.
4. Compare high and low scoring perspectives.
5. Use the summary to support manager feedback and development planning.

### Expected Result

- The manager can see which scorecard perspectives contributed most to the final score.
- HR can use perspective results for performance calibration.

## Step 13. Performance Reporting

**Where to go:** Open **Balanced Scorecards**, then click **Reports**. If your menu has a separate reporting option, choose **Performance Reports** from the KPI reporting area.

1. Open **Performance Reports**.
2. Use filters as needed:
   - Appraisal cycle.
   - Department.
   - Job step or grade.
   - Employee.
   - Template.
   - Completion status.
3. Click **Apply Filters**.
4. Review the summary cards:
   - Cycle.
   - Average score.
   - Completed appraisals.
   - Completion rate.
5. Review **Top Performers**.
6. Review **Bottom Performers**.
7. Review department average scores.
8. Review job step or grade average scores.
9. Review perspective breakdown.
10. Review rubric distribution.
11. Review trend over time.
12. Review completion by department and status.

### Expected Result

- HR leadership can compare performance across cycles, departments, grades, employees, perspectives, and completion status.
- HR/KPI Admin can identify delayed reviews or incomplete departments.

## Step 14. Export Reports

**Where to go:** Open **Performance Reports** from the KPI reporting area or by clicking **Reports** on the Balanced Scorecards page.

Available export options include:

| Export | Use |
| --- | --- |
| Templates CSV | Review scorecard template inventory. |
| Assignment History CSV | Audit when templates were applied and to whom. |
| Appraisal Lines CSV | Analyze detailed employee KPI line scores. |
| Department Summary CSV | Share department-level averages and completion data. |
| Executive CSV | Share a compact executive report. |
| Executive PDF | Share a printable executive dashboard summary. |

### Export Steps

1. Open **Performance Reports**.
2. Apply filters if needed.
3. Click the export link for the data you need.
4. Save the downloaded file.
5. Open the file in Excel, a PDF viewer, or the reporting tool used by your organisation.

### Expected Result

- The file downloads successfully.
- CSV files contain structured columns.
- PDF export contains a concise executive summary.

## Common Administrative Scenarios

### Create A New Scorecard For A Role

1. Create or confirm KPI definitions.
2. Create or confirm scoring rubric.
3. Open Scorecard Designer.
4. Build Perspective -> Objective -> Indicator rows.
5. Save as draft.
6. Validate weights.
7. Publish.
8. Assign to the job step or department.
9. Generate appraisal lines when the appraisal cycle starts.

### Update A Published Scorecard

Published templates should not be directly changed.

1. Open the Template Library.
2. Find the published template.
3. Click **Duplicate**.
4. Edit the duplicated draft.
5. Validate weights.
6. Publish the new version.
7. Assign the new version to the relevant scope.
8. Archive the old template only when it should no longer be used.

### Correct An Imported Template

1. Preview the import before confirming.
2. Correct row values in the preview.
3. Remove duplicates or invalid rows.
4. Download the error report if needed.
5. Confirm import only after blocking errors are resolved.
6. If a confirmed import still needs correction, duplicate or edit the draft before publishing.

### Find Reviews Waiting For Action

1. Open the KPI Admin Monitoring Dashboard.
2. Check pending self-review and manager-review counts.
3. Open the appraisal list.
4. Follow up with employees or managers based on the pending queue.

## Troubleshooting

| Problem | Likely Cause | What To Do |
| --- | --- | --- |
| Template cannot be published | Root weights do not total 1.00, child weights do not match parent, or required fields are missing. | Review the Validation panel and correct all blocking errors. |
| Template does not appear in Apply Template dropdown | Template is still draft or archived. | Publish the template first. |
| Import preview shows blocking errors | Missing perspective, indicator, invalid weight, duplicate row, or total weight is not 100 points. | Edit preview rows, add missing values, remove duplicates, and confirm total weight. |
| Employee has no scorecard lines | KPI assignments were not applied or lines were not generated. | Apply a published template to the correct scope, then generate lines from the appraisal scorecard. |
| Rubric band does not appear on appraisal line | No rubric is linked to the KPI definition, parent definition, or template default. | Assign the correct rubric in the scorecard/template setup. |
| Manual score is disabled | A rubric band is selected. | Clear the rubric band selection to enter a manual score. |
| Evidence link is rejected | Link is not a valid HTTP or HTTPS URL. | Paste a full valid link beginning with `http://` or `https://`. |
| Report has no data | Filters are too narrow or appraisals are not scored yet. | Clear filters, select another cycle, or confirm appraisals have saved scores. |
| Department report is incomplete | Employees may not be assigned to departments. | Confirm employee department data is correct. |

## Best Practices

- Keep scorecard templates simple enough for managers and employees to understand.
- Use consistent perspective names across templates.
- Use rubrics for roles where qualitative judgement is required.
- Save drafts while building; publish only after weights are fully validated.
- Duplicate published templates before making structural changes.
- Use assignment preview before replacing existing assignments.
- Require evidence links for high-impact KPI lines.
- Review the monitoring dashboard weekly during active appraisal cycles.
- Export line scores and department summaries before calibration meetings.
- Archive outdated templates after confirming they are no longer assigned.

## Recommended Testing Checklist

Before using a new scorecard process in production, test the following:

1. Create a rubric with at least four bands.
2. Create a template manually.
3. Save the template as draft.
4. Publish the template.
5. Duplicate the template.
6. Import a sample Excel scorecard.
7. Correct a row in import preview.
8. Confirm import.
9. Apply a template to a test employee.
10. Generate appraisal lines.
11. Complete employee self-review.
12. Complete manager review.
13. Validate weighted score and perspective summary.
14. Open monitoring dashboard.
15. Open performance reports.
16. Test filters.
17. Download each export type.

## Support Information To Capture

When reporting an issue, capture:

- User role.
- Page name and where it was opened from.
- Template name and status.
- Employee name and appraisal cycle.
- Scope used for assignment.
- Screenshot of validation errors.
- Screenshot of import preview errors if importing.
- Exact error message.
- Date and time of the issue.
