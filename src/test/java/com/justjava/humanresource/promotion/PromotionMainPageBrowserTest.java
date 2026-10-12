package com.justjava.humanresource.promotion;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromotionMainPageBrowserTest {

    @Test
    void dashboardListDetailDocumentsTasksConfigurationAndPayrollFlowsRenderAndPostExpectedActions() throws Exception {
        try (Playwright playwright = Playwright.create();
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage();

            page.setContent(pageHtml("dashboard"));
            page.waitForFunction("() => window.__promotionLoaded === true");

            assertThat(page.locator("#pendingApprovalCount")).hasText("3");
            assertThat(page.locator("#approvedCount")).hasText("2");
            assertThat(page.locator("#implementedCount")).hasText("1");
            assertThat(page.locator("#promotionTableBody")).containsText("PR-2026-0001");
            assertThat(page.locator("#promotionTableBody")).containsText("Submit");
            assertThat(page.locator("#promotionTableBody")).containsText("Approve");
            assertThat(page.locator("#promotionTableBody")).containsText("Implement");
            assertThat(page.locator("#taskTableBody")).containsText("Line Manager Review");
            assertThat(page.locator("#payrollHandoffTableBody")).containsText("Apply to Payroll");
            assertThat(page.locator("#statusReportBody")).containsText("DRAFT");
            assertThat(page.locator("#departmentReportBody")).containsText("Engineering");

            page.locator("#promotionTableBody button", new Page.LocatorOptions().setHasText("View")).first().click();
            assertThat(page.locator("#detailPanel")).not().hasClass("hidden");
            assertThat(page.locator("#detailTitle")).hasText("PR-2026-0001 - Ada Lovelace");
            assertThat(page.locator("#detailBody")).containsText("Matched rules: Engineering rule");
            assertThat(page.locator("#detailBody")).containsText("Missing before submit: Appraisal Summary");
            assertThat(page.locator("#detailBody")).containsText("proposal.pdf - APPRAISAL_SUMMARY");

            page.locator("#taskTableBody button", new Page.LocatorOptions().setHasText("Approve")).click();
            assertPosted(page, "/api/promotions/tasks/task-123/approve");

            page.locator("#promotionTableBody button", new Page.LocatorOptions().setHasText("Submit")).click();
            assertPosted(page, "/api/promotions/1/submit");

            page.locator("#promotionTableBody button", new Page.LocatorOptions().setHasText("Implement")).click();
            assertPosted(page, "/api/promotions/3/implement");

            page.locator("#payrollHandoffTableBody button", new Page.LocatorOptions().setHasText("Acknowledge")).click();
            assertPosted(page, "/api/promotions/payroll-handoffs/9/acknowledge");

            page.locator("#payrollHandoffTableBody button", new Page.LocatorOptions().setHasText("Apply to Payroll")).click();
            assertPosted(page, "/api/promotions/payroll-handoffs/9/apply");

            page.locator("#configurationName").fill("Promotion policy 2027");
            page.locator("#approvalReminderAfterHours").fill("12");
            page.locator("#approvalEscalationAfterHours").fill("24");
            page.locator("#escalationApproverGroup").fill("humanresource");
            page.locator("#requiredDocumentsBeforeSubmit").selectOption("APPRAISAL_SUMMARY");
            page.locator("#configurationForm button[type='submit']").click();
            assertRequested(page, "PUT", "/api/promotions/configuration");
            assertThat(page.locator("#configurationMessage")).hasText("Saved");
        }
    }

    @Test
    void focusedRoutesScrollToTheExpectedPanel() throws Exception {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            assertFocusedPanel(browser, "requests", "requestPanel");
            assertFocusedPanel(browser, "approvals", "taskPanel");
            assertFocusedPanel(browser, "reports", "reportsPanel");
            assertFocusedPanel(browser, "configuration", "configurationPanel");
            assertFocusedPanel(browser, "payrollHandoffs", "payrollHandoffPanel");
        }
    }

    private void assertFocusedPanel(Browser browser, String initialSection, String expectedPanelId) throws Exception {
        Page page = browser.newPage();
        page.setContent(pageHtml(initialSection));
        page.waitForFunction("() => window.__promotionLoaded === true");

        Object focusedPanel = page.evaluate("() => window.__scrollTargets[0]");

        assertTrue(expectedPanelId.equals(focusedPanel), "Expected focused panel " + expectedPanelId + " but got " + focusedPanel);
        page.close();
    }

    private String pageHtml(String initialSection) throws Exception {
        String html = Files.readString(Path.of("src/main/resources/templates/promotion/main.html"));
        html = html.replaceFirst("<main layout:fragment=\"content\" th:attr=\"data-initial-section=\\$\\{initialSection\\}\">",
                "<main layout:fragment=\"content\" data-initial-section=\"" + initialSection + "\">");
        return html.replace("</head>", "<script>" + apiStub() + "</script></head>");
    }

    private void assertPosted(Page page, String url) {
        assertRequested(page, "POST", url);
    }

    private void assertRequested(Page page, String method, String url) {
        Boolean requested = (Boolean) page.evaluate(
                "([method, url]) => window.__requests.some(request => request.url === url && request.method === method)",
                new String[]{method, url});
        assertTrue(requested, "Expected " + method + " to " + url);
    }

    private String apiStub() {
        return """
                window.__requests = [];
                window.__scrollTargets = [];
                window.__promotionLoaded = false;
                window.alert = (message) => { window.__lastAlert = message; };
                window.prompt = () => 'Approved in browser test';
                window.confirm = () => true;
                Element.prototype.scrollIntoView = function() { window.__scrollTargets.push(this.id); };
                const jsonResponse = (body, ok = true) => Promise.resolve({
                    ok,
                    json: async () => body,
                    text: async () => typeof body === 'string' ? body : JSON.stringify(body)
                });
                const requestRows = [
                    {
                        id: 1,
                        requestNumber: 'PR-2026-0001',
                        employeeName: 'Ada Lovelace',
                        departmentName: 'Engineering',
                        currentRoleName: 'Engineer',
                        proposedRoleName: 'Senior Engineer',
                        appraisalScore: 91,
                        effectiveDate: '2026-11-01',
                        status: 'DRAFT'
                    },
                    {
                        id: 2,
                        requestNumber: 'PR-2026-0002',
                        employeeName: 'Grace Hopper',
                        departmentName: 'Product',
                        currentRoleName: 'Analyst',
                        proposedRoleName: 'Lead Analyst',
                        appraisalScore: 88,
                        effectiveDate: '2026-11-15',
                        status: 'HR_REVIEW'
                    },
                    {
                        id: 3,
                        requestNumber: 'PR-2026-0003',
                        employeeName: 'Katherine Johnson',
                        departmentName: 'Finance',
                        currentRoleName: 'Accountant',
                        proposedRoleName: 'Senior Accountant',
                        appraisalScore: 94,
                        effectiveDate: '2026-12-01',
                        status: 'APPROVED'
                    }
                ];
                window.fetch = async (input, init = {}) => {
                    const url = String(input);
                    const method = init.method || 'GET';
                    window.__requests.push({ url, method, body: init.body || null });
                    if (url === '/api/promotions/reference/employees') {
                        return jsonResponse([{ id: 10, name: 'Ada Lovelace', departmentName: 'Engineering' }]);
                    }
                    if (url === '/api/promotions/reference/job-steps') {
                        return jsonResponse([{ id: 20, name: 'Senior Engineer', jobGradeName: 'G7' }]);
                    }
                    if (url === '/api/promotions/dashboard') {
                        return jsonResponse({ pendingApprovalCount: 3, approvedCount: 2, implementedCount: 1, monthlySalaryImpact: 250000 });
                    }
                    if (url === '/api/promotions/reports/status-summary') {
                        return jsonResponse([{ status: 'DRAFT', count: 1 }, { status: 'APPROVED', count: 1 }]);
                    }
                    if (url === '/api/promotions/reports/department-summary') {
                        return jsonResponse([{ departmentName: 'Engineering', requestCount: 2, implementedCount: 1, monthlySalaryImpact: 125000 }]);
                    }
                    if (url === '/api/promotions/reports/salary-impact') {
                        return jsonResponse({ totalMonthlyImpact: 250000, totalAnnualImpact: 3000000, averageSalaryIncreasePercent: 12.5 });
                    }
                    if (url === '/api/promotions/reports/approval-duration') {
                        return jsonResponse({ averageHoursToApproval: 18, longestHoursToApproval: 42, approvedRequestCount: 2 });
                    }
                    if (url.startsWith('/api/promotions/payroll-handoffs')) {
                        if (method === 'POST') return jsonResponse({ id: 9, status: 'APPLIED' });
                        return jsonResponse([{
                            id: 9,
                            effectiveDate: '2026-11-01',
                            payGroupName: 'Monthly',
                            currentSalary: 500000,
                            proposedSalary: 650000,
                            monthlyPayrollImpact: 150000,
                            annualPayrollImpact: 1800000,
                            status: 'PENDING'
                        }]);
                    }
                    if (url === '/api/promotions/tasks') {
                        return jsonResponse([{
                            taskId: 'task-123',
                            taskName: 'Line Manager Review',
                            businessKey: 'PR-2026-0001',
                            createdTime: '2026-10-11T09:00:00',
                            variables: { promotionRequestId: 1, requestNumber: 'PR-2026-0001', employeeName: 'Ada Lovelace' }
                        }]);
                    }
                    if (url === '/api/promotions/configuration') {
                        if (method === 'PUT') return jsonResponse({ name: 'Promotion policy 2027' });
                        return jsonResponse({
                            name: 'Default promotion policy',
                            minimumMonthsInCurrentRole: 6,
                            minimumAppraisalScore: 70,
                            maximumSalaryIncreasePercentWithoutExecutiveApproval: 30,
                            approvalReminderAfterHours: 24,
                            approvalEscalationAfterHours: 48,
                            escalationApproverGroup: 'humanresource',
                            allowProbationPromotion: false,
                            requireFinanceApproval: true,
                            requireExecutiveApproval: true,
                            requiredDocumentsBeforeSubmit: ['APPRAISAL_SUMMARY'],
                            requiredDocumentsBeforeImplementation: ['PROMOTION_LETTER']
                        });
                    }
                    if (url === '/api/promotions/1/approval-route-preview') {
                        return jsonResponse({
                            matchedRoutingRules: ['Engineering rule'],
                            steps: [{ sequenceNumber: 1, stage: 'LINE_MANAGER', approverName: 'Mary Manager' }]
                        });
                    }
                    if (url === '/api/promotions/1') {
                        return jsonResponse({
                            id: 1,
                            requestNumber: 'PR-2026-0001',
                            employeeName: 'Ada Lovelace',
                            status: 'DRAFT',
                            currentRoleName: 'Engineer',
                            proposedRoleName: 'Senior Engineer',
                            currentSalary: null,
                            proposedSalary: null,
                            salaryIncreaseAmount: null,
                            salaryIncreasePercent: null,
                            justification: 'Promotion readiness',
                            missingRequiredDocumentsBeforeSubmit: ['APPRAISAL_SUMMARY'],
                            missingRequiredDocumentsBeforeImplementation: [],
                            eligibilityChecks: [{ passed: false, checkName: 'APPRAISAL_SCORE', message: 'Below threshold' }],
                            approvalSteps: [{ stage: 'LINE_MANAGER', decision: 'PENDING', approverName: 'Mary Manager' }],
                            attachments: [{ id: 4, fileName: 'proposal.pdf', documentType: 'APPRAISAL_SUMMARY' }],
                            activities: [{ createdAt: '2026-10-11T10:00:00', description: 'Created promotion request' }]
                        });
                    }
                    if (url === '/api/promotions' || url.startsWith('/api/promotions?')) {
                        return jsonResponse(requestRows);
                    }
                    if (method === 'POST') {
                        return jsonResponse({ ok: true });
                    }
                    return jsonResponse('Not found', false);
                };
                document.addEventListener('DOMContentLoaded', () => setTimeout(() => { window.__promotionLoaded = true; }, 0));
                """;
    }
}
