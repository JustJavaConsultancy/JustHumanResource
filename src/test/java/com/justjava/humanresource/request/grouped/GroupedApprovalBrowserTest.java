package com.justjava.humanresource.request.grouped;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Browser interaction tests with synthetic API fixtures; no server, production data or email access. */
@EnabledIfSystemProperty(named="grouped.browser.tests", matches="true")
class GroupedApprovalBrowserTest {
    Playwright playwright;
    Browser browser;
    BrowserContext context;
    Page page;
    List<String> errors;
    @BeforeEach void open() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true)
                .setExecutablePath(Path.of(System.getProperty("grouped.browser.path","C:/Program Files/Google/Chrome/Application/chrome.exe"))));
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(390,844));
        page = context.newPage(); errors = new ArrayList<>(); page.onPageError(errors::add);
        page.route("**/*", route -> {
            String path = java.net.URI.create(route.request().url()).getPath();
            try {
                if (path.startsWith("/js/")) {
                    route.fulfill(new Route.FulfillOptions().setContentType("application/javascript").setBody(Files.readString(Path.of("src/main/resources/static"+path))));
                } else if (path.endsWith("/csrf")) reply(route,"{\"token\":\"test-token\",\"headerName\":\"X-XSRF-TOKEN\"}");
                else route.fulfill(new Route.FulfillOptions().setStatus(404));
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    @AfterEach void close() { if(context!=null) context.close(); if(browser!=null) browser.close(); if(playwright!=null)playwright.close(); }
    static void reply(Route route,String body) { route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(body)); }

    @Test void administratorCanBuildPreviewAndSaveMobileConfiguration() throws Exception {
        page.route("**/api/grouped-approval-paths", r -> {
            if (r.request().method().equals("POST")) {
                assertThat(r.request().postData()).contains("ANY_ONE","11","Review");
                assertThat(r.request().headers()).containsEntry("x-xsrf-token","test-token");
                reply(r,"{\"id\":1,\"version\":0}");
            } else reply(r,"{\"enabled\":true,\"paths\":[{\"id\":1,\"version\":0,\"revision\":1,\"name\":\"Draft\",\"state\":\"DRAFT\",\"enabled\":true,\"levels\":[]}]}");
        });
        page.route("**/api/approval-paths/approvers",r -> reply(r,"[{\"value\":11,\"label\":\"E011 - Ada Example\"},{\"value\":12,\"label\":\"E012 - Ben Example\"}]"));
        page.route("**/api/requests/types",r -> reply(r,"[{\"code\":\"GENERAL_REQUEST\",\"name\":\"General\",\"requiresApproval\":true}]"));
        String html = Files.readString(Path.of("src/main/resources/templates/request/grouped-approval-paths.html"));
        page.route("**/requests/grouped-approval-paths",r -> r.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody(html)));
        page.navigate("http://grouped.test/requests/grouped-approval-paths");
        page.getByText("Grouped approval is available for new submissions.").waitFor();
        page.getByLabel("Path name",new Page.GetByLabelOptions().setExact(true)).fill("Review path");
        page.getByLabel("Level name",new Page.GetByLabelOptions().setExact(true)).fill("Review");
        page.getByLabel("Decision policy",new Page.GetByLabelOptions().setExact(true)).selectOption("ANY_ONE");
        page.getByLabel("E011 - Ada Example").check();
        page.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Preview").setExact(true)).click();
        assertThat(page.locator("#gcPreviewText").innerText()).contains("first decision","Ada Example");
        page.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Save draft").setExact(true)).click();
        page.getByText("Draft saved. Preview and publish it when ready.").waitFor();
        assertThat(errors).isEmpty();
        assertThat((Boolean)page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isTrue();
    }

    @Test void approverSeesPartialProgressAndAccurateDecisionFeedback() {
        String initial = "{\"requestStatus\":\"IN_APPROVAL\",\"cycles\":[{\"cycleNo\":1,\"pathName\":\"Review\",\"state\":\"ACTIVE\",\"levels\":[{\"ordinal\":1,\"name\":\"Review\",\"policy\":\"ALL\",\"state\":\"ACTIVE\",\"approvedCount\":1,\"memberCount\":3,\"members\":[{\"name\":\"Ada Example\",\"state\":\"PENDING\",\"taskId\":\"task-11\"}]}]}]}";
        page.route("**/api/requests/grouped-approvals/42",r -> reply(r,initial));
        page.route("**/api/requests/grouped-approvals/decisions",r -> {
            assertThat(r.request().postData()).contains("APPROVE","task-11");
            reply(r,"{\"message\":\"Your approval was recorded; other approvals are still required.\"}");
        });
        page.route("**/employee/requests/42",r -> r.fulfill(new Route.FulfillOptions().setContentType("text/html")
                .setBody("<html><head></head><body><main><h1>Request</h1></main><script src='/js/grouped-approvals.js' defer></script></body></html>")));
        page.navigate("http://grouped.test/employee/requests/42");
        page.getByText("1 of 3 approvals received").waitFor();
        page.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Approve").setExact(true)).click();
        page.getByRole(AriaRole.DIALOG).waitFor();
        assertThat(page.getByRole(AriaRole.DIALOG).innerText()).contains("only when everyone has approved");
        page.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Confirm decision")).click();
        page.getByText("Your approval was recorded; other approvals are still required.").waitFor();
        assertThat(errors).isEmpty();
        assertThat((Boolean)page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isTrue();
    }

    @Test void staleDecisionExplainsConflictAndPreservesComment() {
        String initial = "{\"requestStatus\":\"IN_APPROVAL\",\"cycles\":[{\"cycleNo\":1,\"pathName\":\"Review\",\"state\":\"ACTIVE\",\"levels\":[{\"ordinal\":1,\"name\":\"Review\",\"policy\":\"ANY_ONE\",\"state\":\"ACTIVE\",\"approvedCount\":0,\"memberCount\":2,\"members\":[{\"name\":\"Ada Example\",\"state\":\"PENDING\",\"taskId\":\"task-11\"}]}]}]}";
        page.route("**/api/requests/grouped-approvals/42",r -> reply(r,initial));
        page.route("**/api/requests/grouped-approvals/decisions",r -> r.fulfill(new Route.FulfillOptions().setStatus(409).setContentType("application/json").setBody("{\"detail\":\"Already resolved\"}")));
        page.route("**/employee/requests/42",r -> r.fulfill(new Route.FulfillOptions().setContentType("text/html")
                .setBody("<html><head></head><body><main></main><script src='/js/grouped-approvals.js' defer></script></body></html>")));
        page.navigate("http://grouped.test/employee/requests/42");
        page.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Reject").setExact(true)).click();
        page.getByLabel("Comment (required)").fill("Preserve this explanation");
        page.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Confirm decision")).click();
        page.getByText("This task has changed. Your decision was not applied. Close this dialog to see the current outcome.").waitFor();
        assertThat(page.locator("#groupedComment").inputValue()).isEqualTo("Preserve this explanation");
        assertThat(errors).isEmpty();
    }
}
