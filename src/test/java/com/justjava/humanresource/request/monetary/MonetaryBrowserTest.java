package com.justjava.humanresource.request.monetary;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
/** Actual templates/scripts with synthetic APIs and a local browser; no application or email access. */
@EnabledIfSystemProperty(named="monetary.browser.tests",matches="true")
class MonetaryBrowserTest {
 Playwright playwright;Browser browser;Page page;List<String> errors;
 @BeforeEach void open(){playwright=Playwright.create();browser=playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true).setExecutablePath(Path.of(System.getProperty("grouped.browser.path","C:/Program Files/Google/Chrome/Application/chrome.exe"))));page=browser.newPage(new Browser.NewPageOptions().setViewportSize(390,844));errors=new ArrayList<>();page.onPageError(errors::add);
  page.route("**/*",route->{String path=java.net.URI.create(route.request().url()).getPath();try{
   if(path.startsWith("/js/")||path.startsWith("/css/"))route.fulfill(new Route.FulfillOptions().setContentType(path.startsWith("/js/")?"application/javascript":"text/css").setBody(Files.readString(Path.of("src/main/resources/static"+path))));
   else if(path.endsWith("/csrf"))reply(route,"{\"token\":\"test-token\",\"headerName\":\"X-XSRF-TOKEN\"}");else route.fulfill(new Route.FulfillOptions().setStatus(404));
  }catch(Exception e){throw new RuntimeException(e);}});
 }
 @AfterEach void close(){if(browser!=null)browser.close();if(playwright!=null)playwright.close();}
 static void reply(Route route,String value){route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(value));}
 void fundingOptions(){page.route("**/api/request-monetary/options",r->reply(r,"{\"enabled\":true,\"currencies\":[\"NGN\",\"JPY\"],\"types\":[{\"code\":\"CASH_ADVANCE\",\"name\":\"Cash Advance\"},{\"code\":\"FUND_REQUEST\",\"name\":\"Fund Request\"}],\"beneficiaries\":[{\"value\":9,\"label\":\"Test Employee\"}]}"));}
 void template(String url,String name)throws Exception{String html=Files.readString(Path.of("src/main/resources/templates/request/"+name));page.route("**"+url,r->r.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody(html)));page.navigate("http://monetary.test"+url);}
 @Test void mobileFundingSavesPreciseAmountUploadsAndPreviews()throws Exception{
  fundingOptions();page.route("**/api/request-monetary/funding",r->{assertThat(r.request().postData()).contains("\"amount\":\"99999999999999999.99\"","FUND_REQUEST");assertThat(r.request().headers()).containsEntry("x-xsrf-token","test-token");reply(r,"{\"id\":42,\"requestNumber\":\"FND-42\"}");});
  page.route("**/api/requests/42/attachments",r->reply(r,"{}"));page.route("**/api/request-monetary/requests/42/preview",r->reply(r,"{\"monetary\":true,\"currency\":\"NGN\",\"amount\":\"99999999999999999.99\",\"policy\":\"Funding limits\",\"policyVersion\":1,\"lowerBound\":\"0\",\"upperBound\":\"No upper limit\",\"target\":\"GROUPED\"}"));
  template("/mobile/requests/funding/new","funding-form.html");page.getByText("Save a draft before previewing or submitting.").waitFor();
  page.locator("#fundingType").selectOption("FUND_REQUEST");page.locator("#fundingTitle").fill("Office funding");page.locator("#fundingAmount").fill("99999999999999999.99");page.locator("#fundingPurpose").fill("Office supplies");page.locator("#fundingCostCenter").fill("OPS");page.locator("#fundingRequired").fill("2030-01-01");page.locator("#fundingBeneficiary").selectOption("9");
  page.locator("button[type=submit]").click();page.getByText("Saved FND-42.",new Page.GetByTextOptions().setExact(false)).waitFor();
  page.locator("#fundingAttachment").setInputFiles(new FilePayload("proof.txt","text/plain","Support".getBytes()));page.locator("#fundingUpload").click();page.getByText("1 supporting document(s)",new Page.GetByTextOptions().setExact(false)).waitFor();
  page.locator("#fundingPreview").click();page.locator("#fundingPreviewText").getByText("Funding limits",new Locator.GetByTextOptions().setExact(false)).waitFor();
  assertThat(page.locator("#fundingError").innerText()).isEmpty();assertThat(errors).isEmpty();assertThat((Boolean)page.evaluate("document.documentElement.scrollWidth <= innerWidth")).isTrue();
 }
 @Test void correctionEditorLocksCurrencyAndPreservesExistingHeader()throws Exception{
  fundingOptions();String saved="{\"id\":42,\"requestType\":\"FUND_REQUEST\",\"title\":\"Existing funding\",\"priority\":\"HIGH\",\"description\":\"Existing description\",\"departmentId\":44,\"fundRequest\":{\"amount\":\"12345678901234567.89\",\"currency\":\"NGN\",\"purpose\":\"Office supplies\",\"costCenter\":\"OPS\",\"requiredDate\":\"2030-01-01\",\"beneficiaryEmployeeId\":9}}";
  page.route("**/api/request-monetary/funding/42",r->{if(r.request().method().equals("PUT")){assertThat(r.request().postData()).contains("HIGH","Existing description","\"departmentId\":44","12345678901234567.89");reply(r,"{\"id\":42,\"requestNumber\":\"FND-42\"}");}else reply(r,saved);});
  page.route("**/api/request-monetary/requests/42/history",r->reply(r,"{\"snapshots\":[{}]}"));
  template("/mobile/requests/funding/42/edit","funding-form.html");page.getByText("Save a draft before previewing or submitting.").waitFor();assertThat(page.locator("#fundingCurrency").isDisabled()).isTrue();assertThat(page.locator("#fundingAmount").inputValue()).isEqualTo("12345678901234567.89");page.locator("button[type=submit]").click();page.getByText("Saved FND-42.",new Page.GetByTextOptions().setExact(false)).waitFor();assertThat(errors).isEmpty();
 }
 @Test void monetaryHistoryDisplaysFundingAndCorrectionDetails()throws Exception{
  page.route("**/api/request-monetary/requests/42/history",r->reply(r,"{\"funding\":true,\"request\":{\"requesterEmployeeId\":1,\"status\":\"RETURNED_FOR_CORRECTION\"},\"typeDetails\":{\"currency\":\"NGN\",\"amount\":\"1000.00\",\"purpose\":\"Office funding\",\"costCenter\":\"OPS\",\"requiredDate\":\"2030-01-01\",\"beneficiaryName\":\"Test Employee\"},\"snapshots\":[{\"submissionNo\":1,\"currency\":\"NGN\",\"amount\":\"100.00\",\"lowerBound\":\"0\",\"upperBound\":\"1000\",\"target\":\"LINE_MANAGER\",\"policyName\":\"Funding limits (v1)\",\"resolvedRoute\":\"[]\"},{\"submissionNo\":2,\"currency\":\"NGN\",\"amount\":\"1000.00\",\"lowerBound\":\"1000\",\"upperBound\":null,\"target\":\"GROUPED\",\"policyName\":\"Funding limits (v1)\",\"resolvedRoute\":\"[]\"}]}"));
  page.route("**/api/requests/context",r->reply(r,"{\"employeeId\":1}"));page.route("**/mobile/requests/42",r->r.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody("<html><head></head><body><main></main><script src='/js/request-monetary-history.js' defer></script></body></html>")));
  page.navigate("http://monetary.test/mobile/requests/42");page.getByText("Monetary routing history").waitFor();assertThat(page.locator("main").innerText()).contains("Test Employee","Previous amount: NGN 100.00","fresh approvals");assertThat(page.getByRole(AriaRole.LINK,new Page.GetByRoleOptions().setName("Edit funding request")).getAttribute("href")).isEqualTo("/mobile/requests/funding/42/edit");assertThat(errors).isEmpty();
 }
 @Test void integratedLegacyTemplatesRetainValidInlineJavascript()throws Exception{
  for(String name:List.of("request/employee-main.html","mobile/request-main.html","request/main.html","mobile/request-admin.html")){
   String html=Files.readString(Path.of("src/main/resources/templates/"+name));var matcher=java.util.regex.Pattern.compile("<script(?![^>]*src=)[^>]*>([\\s\\S]*?)</script>").matcher(html);
   while(matcher.find())assertThat((Boolean)page.evaluate("code => { new Function(code); return true; }",matcher.group(1))).isTrue();
  }
 }
 @Test void administratorBuildsTwoBandsAndSavesDecimalStrings()throws Exception{
  fundingOptions();page.setViewportSize(1280,900);page.route("**/api/request-monetary/config",r->reply(r,"{\"enabled\":true,\"fundingEnabled\":true,\"policies\":[],\"bindings\":[],\"types\":[{\"code\":\"FUND_REQUEST\",\"name\":\"Fund Request\",\"enabled\":true}]}"));
  page.route("**/api/grouped-approval-paths",r->reply(r,"{\"paths\":[{\"id\":30,\"name\":\"Finance committee\",\"state\":\"PUBLISHED\",\"enabled\":true,\"revision\":1}]}"));
  page.route("**/api/request-monetary/policies",r->{assertThat(r.request().postData()).contains("\"lowerBound\":\"1000\"","\"upperBound\":\"1000\"","GROUPED","30");reply(r,"{\"id\":20,\"version\":0}");});
  template("/requests/monetary-policies","monetary-policies.html");page.locator("[data-lower]").waitFor();page.locator("#monetaryName").fill("Funding limits");page.locator("[data-upper]").fill("1000");page.locator("#monetaryAddBand").click();page.locator("[data-lower]").nth(1).fill("1000");page.locator("[data-target]").nth(1).selectOption("GROUPED");page.locator("[data-path]").nth(1).selectOption("30");page.locator("#monetaryPreview").click();assertThat(page.locator("#monetaryPreviewText").innerText()).contains("1000","Finance committee","no upper limit");page.locator("button[type=submit]").click();page.getByText("Draft saved.",new Page.GetByTextOptions().setExact(false)).waitFor();assertThat(errors).isEmpty();assertThat(page.locator("#monetaryError").innerText()).isEmpty();
 }

 void existingFunding() throws Exception {
  fundingOptions();page.route("**/api/request-monetary/funding/42",r->reply(r,"{\"id\":42,\"requestType\":\"FUND_REQUEST\",\"title\":\"Office funding\",\"priority\":\"HIGH\",\"departmentId\":44,\"fundRequest\":{\"amount\":\"100\",\"currency\":\"NGN\",\"purpose\":\"Supplies\",\"costCenter\":\"OPS\",\"requiredDate\":\"2030-01-01\",\"beneficiaryEmployeeId\":9}}"));
  page.route("**/api/request-monetary/requests/42/history",r->reply(r,"{\"snapshots\":[{}]}"));
  template("/mobile/requests/funding/42/edit","funding-form.html");page.getByText("Save a draft before previewing or submitting.").waitFor();
 }
 @Test void delayedFundingSaveKeepsLaterEditsAndPinnedCurrency()throws Exception {
  existingFunding();List<Route> pending=new ArrayList<>();page.route("**/api/request-monetary/funding/42",pending::add);
  page.locator("button[type=submit]").click();page.waitForCondition(()->!pending.isEmpty());assertThat(pending.getFirst().request().postData()).contains("\"amount\":\"100\"");
  page.locator("#fundingAmount").fill("1000");reply(pending.removeFirst(),"{\"id\":42,\"requestNumber\":\"FND-42\"}");page.getByText("Previous values saved.",new Page.GetByTextOptions().setExact(false)).waitFor();
  assertThat(page.locator("#fundingAmount").inputValue()).isEqualTo("1000");assertThat(page.locator("#fundingCurrency").isDisabled()).isTrue();
  page.locator("#fundingPreview").click();page.getByText("Save the draft and all changes before this action.").waitFor();
  page.locator("#fundingSubmit").click();assertThat(page.locator("#fundingError").innerText()).contains("Save the draft");
  page.locator("button[type=submit]").click();page.waitForCondition(()->!pending.isEmpty());assertThat(pending.getFirst().request().postData()).contains("\"amount\":\"1000\"");pending.removeFirst().fulfill(new Route.FulfillOptions().setStatus(400).setContentType("application/json").setBody("{\"message\":\"Validation failed\"}"));
  page.getByText("Validation failed").waitFor();assertThat(page.locator("#fundingAmount").inputValue()).isEqualTo("1000");assertThat(page.locator("#fundingCurrency").isDisabled()).isTrue();assertThat(errors).isEmpty();
 }
 void reimbursement(boolean capable)throws Exception {
  page.route("**/api/request-monetary/requests/42/history",r->reply(r,"{\"funding\":false,\"request\":{},\"typeDetails\":{},\"snapshots\":[],\"routing\":{\"monetaryApplicable\":true,\"monetaryRoutingAvailable\":true,\"canPreviewSavedRoute\":"+capable+"}}"));
  page.route("**/employee/requests/42",r->r.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody("<html><head></head><body><main></main><script src='/js/request-monetary-history.js' defer></script></body></html>")));
  page.navigate("http://monetary.test/employee/requests/42");page.getByText("No monetary submission yet.").waitFor();
 }
 @Test void initialReimbursementPreviewsThresholdAndRetriesSelectionErrors()throws Exception {
  reimbursement(true);page.route("**/api/request-monetary/requests/42/preview",r->r.fulfill(new Route.FulfillOptions().setStatus(409).setContentType("application/json").setBody("{\"message\":\"No active currency binding\"}")));
  page.getByText("Preview saved monetary route",new Page.GetByTextOptions().setExact(true)).click();page.getByRole(AriaRole.STATUS).getByText("No active currency binding",new Locator.GetByTextOptions().setExact(false)).waitFor();
  page.route("**/api/request-monetary/requests/42/preview",r->reply(r,"{\"monetary\":true,\"currency\":\"NGN\",\"amount\":\"1000\",\"policy\":\"Limits\",\"policyVersion\":2,\"lowerBound\":\"1000\",\"upperBound\":\"No upper limit\",\"target\":\"LINE_MANAGER\",\"resolvedRoute\":\"[]\"}"));
  page.getByText("Preview saved monetary route",new Page.GetByTextOptions().setExact(true)).click();page.getByRole(AriaRole.STATUS).getByText("NGN 1000: Limits revision 2",new Locator.GetByTextOptions().setExact(false)).waitFor();assertThat(errors).isEmpty();
 }
 @Test void nonOwnerDoesNotReceivePreviewControl()throws Exception {reimbursement(false);assertThat(page.getByRole(AriaRole.BUTTON).count()).isZero();}
 @Test void refreshDiscardsPendingPreviewAndDoesNotDuplicateActions()throws Exception {
  reimbursement(true);List<Route> pending=new ArrayList<>();page.route("**/api/request-monetary/requests/42/preview",pending::add);page.getByRole(AriaRole.BUTTON).click();page.waitForCondition(()->!pending.isEmpty());
  page.evaluate("window.dispatchEvent(new Event('request-monetary:refresh'))");page.waitForCondition(()->page.getByRole(AriaRole.STATUS).innerText().isEmpty());reply(pending.removeFirst(),"{\"monetary\":false,\"message\":\"OLD RESPONSE\"}");
  assertThat(page.getByRole(AriaRole.BUTTON).count()).isEqualTo(1);assertThat(page.locator("main").innerText()).doesNotContain("OLD RESPONSE");assertThat(errors).isEmpty();
 }
 @Test void bindingShowsExactRevisionAndRetainedUnavailableStates()throws Exception {
  fundingOptions();page.route("**/api/grouped-approval-paths",r->reply(r,"{\"paths\":[]}"));
  page.route("**/api/request-monetary/config",r->reply(r,"{\"enabled\":false,\"fundingEnabled\":true,\"types\":[{\"code\":\"FUND_REQUEST\",\"name\":\"Funds\",\"enabled\":true,\"monetaryRoutingEnabled\":false}],\"bindings\":[{\"requestType\":\"FUND_REQUEST\",\"currency\":\"NGN\",\"policyId\":2},{\"requestType\":\"FUND_REQUEST\",\"currency\":\"USD\",\"policyId\":99}],\"policies\":[{\"id\":1,\"name\":\"Limits\",\"revision\":1,\"state\":\"PUBLISHED\",\"enabled\":true,\"requestType\":\"FUND_REQUEST\",\"currency\":\"NGN\"},{\"id\":2,\"name\":\"Limits\",\"revision\":2,\"state\":\"PUBLISHED\",\"enabled\":false,\"requestType\":\"FUND_REQUEST\",\"currency\":\"NGN\"}]}"));
  template("/requests/monetary-policies","monetary-policies.html");page.getByText("NGN: Limits - revision 2",new Page.GetByTextOptions().setExact(false)).waitFor();assertThat(page.locator("#monetaryBindings").innerText()).contains("Retained binding; not used for new fixed-route requests","Disabled policy","Policy #99 - metadata unavailable");assertThat((Boolean)page.evaluate("document.documentElement.scrollWidth <= innerWidth")).isTrue();assertThat(errors).isEmpty();
 }
 @Test void policySaveAdoptsVersionWithoutClearingLaterBounds()throws Exception {
  fundingOptions();page.route("**/api/grouped-approval-paths",r->reply(r,"{\"paths\":[]}"));
  String config="{\"enabled\":true,\"fundingEnabled\":true,\"policies\":[],\"bindings\":[],\"types\":[{\"code\":\"FUND_REQUEST\",\"name\":\"Funds\",\"enabled\":true}]}";
  page.route("**/api/request-monetary/config",r->reply(r,config.replace("\"policies\":[]", "\"policies\":[{\"id\":20,\"name\":\"Limits\",\"revision\":1,\"version\":3,\"state\":\"DRAFT\",\"enabled\":true,\"requestType\":\"FUND_REQUEST\",\"currency\":\"NGN\",\"bands\":[]}]")));List<Route> pending=new ArrayList<>();page.route("**/api/request-monetary/policies",pending::add);
  template("/requests/monetary-policies","monetary-policies.html");page.locator("[data-lower]").waitFor();page.locator("#monetaryName").fill("Limits");page.locator("button[type=submit]").click();page.waitForCondition(()->!pending.isEmpty());assertThat(pending.getFirst().request().postData()).contains("\"lowerBound\":\"0\"");
  page.locator("[data-lower]").fill("1000");reply(pending.removeFirst(),"{\"id\":20,\"version\":3}");page.getByText("Previous values saved.",new Page.GetByTextOptions().setExact(false)).waitFor();assertThat(page.locator("[data-lower]").inputValue()).isEqualTo("1000");page.locator("[data-publish=\"20\"]").click();page.getByText("Save current changes before publishing this draft.").waitFor();
  page.route("**/api/request-monetary/policies/20?version=3",r->{assertThat(r.request().method()).isEqualTo("PUT");assertThat(r.request().postData()).contains("\"lowerBound\":\"1000\"");reply(r,"{\"id\":20,\"version\":4}");});page.locator("button[type=submit]").click();page.getByText("Draft saved.",new Page.GetByTextOptions().setExact(false)).waitFor();assertThat(errors).isEmpty();
 }

 @Test void initialSaveLocksTypeAdoptsIdAndKeepsLaterAmountUnsaved()throws Exception {
  fundingOptions();List<Route> pending=new ArrayList<>();page.route("**/api/request-monetary/funding",pending::add);
  template("/employee/requests/funding/new","funding-form.html");page.getByText("Save a draft before previewing or submitting.").waitFor();
  page.locator("#fundingType").selectOption("FUND_REQUEST");page.locator("#fundingTitle").fill("Funds");page.locator("#fundingAmount").fill("100");page.locator("#fundingPurpose").fill("Supplies");page.locator("#fundingCostCenter").fill("OPS");page.locator("#fundingRequired").fill("2030-01-01");page.locator("#fundingBeneficiary").selectOption("9");
  page.locator("button[type=submit]").click();page.waitForCondition(()->!pending.isEmpty());assertThat(page.locator("#fundingType").isDisabled()).isTrue();assertThat(pending.getFirst().request().postData()).contains("\"amount\":\"100\"");
  page.locator("#fundingAmount").fill("1000");reply(pending.removeFirst(),"{\"id\":42,\"requestNumber\":\"FND-42\"}");page.getByText("Previous values saved.",new Page.GetByTextOptions().setExact(false)).waitFor();assertThat(page.url()).endsWith("/funding/42/edit");
  page.route("**/api/request-monetary/funding/42",r->{assertThat(r.request().method()).isEqualTo("PUT");assertThat(r.request().postData()).contains("\"amount\":\"1000\"");reply(r,"{\"id\":42,\"requestNumber\":\"FND-42\"}");});
  page.locator("#fundingUpload").click();page.getByText("Save the draft and all changes before this action.").waitFor();page.locator("button[type=submit]").click();page.getByText("Saved FND-42.",new Page.GetByTextOptions().setExact(false)).waitFor();assertThat(errors).isEmpty();
 }
 @Test void fundingPreviewBecomesStaleIfEditedWhileLoading()throws Exception {
  existingFunding();List<Route> pending=new ArrayList<>();page.route("**/api/request-monetary/requests/42/preview",pending::add);page.locator("#fundingPreview").click();page.waitForCondition(()->!pending.isEmpty());page.locator("#fundingAmount").fill("1000");reply(pending.removeFirst(),"{\"monetary\":false,\"message\":\"Old fixed route\"}");page.getByText("Preview is out of date. Save changes and preview again.").waitFor();assertThat(errors).isEmpty();
 }
 @Test void reimbursementShowsDisabledPolicyAndFixedResponseOnDesktop()throws Exception {
  page.setViewportSize(1280,900);reimbursement(true);
  page.route("**/api/request-monetary/requests/42/preview",r->r.fulfill(new Route.FulfillOptions().setStatus(409).setContentType("application/json").setBody("{\"message\":\"Policy is disabled\"}")));
  page.getByRole(AriaRole.BUTTON).click();page.getByRole(AriaRole.STATUS).getByText("Policy is disabled",new Locator.GetByTextOptions().setExact(false)).waitFor();
  page.route("**/api/request-monetary/requests/42/preview",r->reply(r,"{\"monetary\":false,\"message\":\"The configured fixed approval route applies.\"}"));page.getByRole(AriaRole.BUTTON).click();page.getByText("The configured fixed approval route applies.").waitFor();assertThat(page.getByRole(AriaRole.STATUS).innerText()).doesNotContain("undefined");assertThat(errors).isEmpty();
 }
}
