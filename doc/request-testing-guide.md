# Request Monetary Enhancements: End-to-End Testing Guide

**Status:** testing procedure; execution results must be recorded separately.  
**Source:** [Request enhancement implementation and rollout plan](request-enhancement-plan.md), including sections 11–13.  
**Audience:** developers, QA engineers, deployment engineers and business acceptance reviewers.  
**Last updated:** 3 November 2026.

## 1. Purpose and acceptance boundaries

This guide validates monetary routing for Expense Reimbursement and the new Cash Advance and Fund Request types. It covers configuration, capture, saved-value previews, submission, approvals, Return/correction, security, history and compatibility. It explicitly includes the three remediated UI gaps: save-state correctness, initial reimbursement preview and exact binding revision display.

Use an isolated development environment or approved staging deployment. End-to-end acceptance must use the actual deployed pages and endpoints. Automated browser fixtures exercise real scripts/templates with synthetic APIs; they do not prove that authentication, Thymeleaf layouts, notifications, attachment storage or organization-specific role assignments work in the deployed application.

Approval means approval for processing. This enhancement does not execute payment, reserve a budget, perform exchange conversion, reimburse an employee or settle an advance. Do not expect payment or settlement transactions as successful test outcomes.

A case passes only when its expected behavior and persisted evidence agree. A hidden button does not establish server authorization; a successful HTTP response does not establish correct routing or transaction boundaries.

## 2. Execution sequence

Run the sections in order; later cases depend on the policies and requests created earlier.

| Phase | Sections | Required outcome |
| --- | --- | --- |
| Prepare | 3–5 | Environment, accounts, baseline and test data recorded |
| Configure | 6 | Published grouped targets and explicitly activated monetary policies |
| Exercise primary journeys | 7–9 | Capture, saved-value previews and approvals for all three types |
| Verify history and compatibility | 10–11 | Pinned corrections, fresh approvals and exact binding identity |
| Test failure and security | 12–14 | Save races, availability, concurrency and access controls |
| Verify deployed experience | 15–16 | Finance/Free Route compatibility, mobile/desktop and accessibility |
| Gather technical evidence | 17–18 | Database/workflow invariants and automated regression results |
| Close the run | 19–20 | Defects triaged, acceptance recorded and configuration restored |

For an initial smoke test, execute ENV-01, CFG-01, CFG-02, FUND-01, PRE-01, APR-01 and COR-01. A smoke pass does not replace full acceptance.

## 3. Environment prerequisites

### 3.1 Record the test environment

Complete this record before execution:

| Field | Record |
| --- | --- |
| Build | Commit SHA, build identifier and deployment timestamp |
| Application | Base URL, active profile and application instance count |
| Database | Test database/schema, PostgreSQL version and migration evidence |
| Runtime | Java/Maven versions; application timezone and current date |
| Browser | Browser/version, OS and viewport or physical device |
| Integrations | Test identity provider, mail sink and attachment storage |
| Configuration | Initial flags, type availability, approval/attachment requirements and fixed routes |
| Execution | Tester, date, run identifier and defect tracker |

Use a unique prefix such as `REQ-E2E-20261103-A` in titles, policy names and filenames. Use identifiable test employees. Sanitize captured requests/responses: exclude session cookies, CSRF tokens and unrelated employee data.

### 3.2 Database and feature setup

1. Confirm `db/migrations/20261030_grouped_request_approvals.sql` has been applied to the test deployment.
2. Apply `db/migrations/20261103_request_monetary_enhancements.sql` through the deployment migration procedure before enabling monetary features. Record the outcome; Hibernate schema updates alone do not replace its constraints.
3. Start with monetary routing and funding disabled. Record existing fixed configurations and representative in-flight requests.
4. Run ENV-01, then enable the relevant flags and restart/redeploy as required by the environment.
5. Verify effective values through the admin configuration response. Editing a local properties file does not establish a running instance's configuration.
6. With funding globally enabled, explicitly enable Cash Advance and Fund Request through monetary administration if they were seeded disabled. Startup preserves administrator settings.
7. Ensure each monetary test type requires approval. Record its attachment requirement; both new types default to requiring a supporting document.

```properties
app.requests.monetary-routing.enabled=true
app.requests.funding.enabled=true
app.requests.grouped-approval.enabled=true
```

The grouped flag is required for new grouped selections. Monetary mode is selected per request type through activation. Activating one currency enables monetary mode for the type; other currencies need their own bindings.

### 3.3 Accounts and roles

Use separate browser profiles or isolated authenticated contexts. Each acting account must resolve to the intended employee record. These aliases describe test responsibilities, not new application roles.

| Alias | Setup | Purpose |
| --- | --- | --- |
| HR-A | Existing HR/administrator permissions | Policies, grouped paths, availability and audit |
| REQ-A | Active visible employee with an eligible Line Manager chain | Owner/requester |
| REQ-B | Unrelated employee without request visibility | Denied ownership/read cases |
| LM-1 / LM-2 | Eligible employees in REQ-A's configured chain | Sequential approvals |
| ALL-1 / ALL-2 | Eligible members of an `ALL` level | Unanimous voting |
| ANY-1 / ANY-2 | Eligible members of a later `ANY_ONE` level | First approval and superseded assignments |
| FIN-A | Existing Finance officer permissions | Finance reads/intervention |
| BEN-A | Active, visible, non-suspended employee | Fund beneficiary |
| COPY-A | Employee copied through the existing feature | Authorized reader without owner rights |
| BAD-A | Separate inactive, restricted-visibility or suspended employee | Eligibility failures |

Do not include REQ-A in baseline approval paths. Reserve self-approval exclusion for a separate negative case. Record the deployment's actual role mappings rather than assuming account names confer permissions.

## 4. Repeatable test data

Let **T** be the application's current calendar date. Enter funding dates relative to T, and recheck them if testing crosses midnight.

### 4.1 Grouped targets

Create these enabled, published versions at `/requests/grouped-approval-paths`:

| Alias | Definition |
| --- | --- |
| GP-A | Level 1: `ALL`, ALL-1 and ALL-2; level 2: `ANY_ONE`, ANY-1 and ANY-2 |
| GP-B | Another eligible path with visibly different levels/member names |

Record actual version IDs, revisions and effective members. Monetary bands select a published version, not a name or an assumed latest revision.

### 4.2 Monetary policies and thresholds

Create one NGN baseline policy for each supported type. Name it `<run>-<type> limits`; retain that name when cloning revisions.

| Band | Bounds | Target |
| --- | --- | --- |
| Low | `0` inclusive to `1000` exclusive | Line Manager chain |
| High | `1000` inclusive, no upper limit | GP-A published version |

| Test amount | Expected NGN selection |
| --- | --- |
| `0.01` | Low |
| `999.99` | Low |
| `1000.00` | High |
| `1000.01` | High |
| `99999999999999999.99` | High; exact supported maximum |

Create USD policies during multi-currency testing. For successful JPY monetary submissions, create a JPY policy with whole-number boundaries. Never assume an NGN binding covers another currency.

### 4.3 Request payloads and inventory

Use title `<run>-<type>-<case>`, priority `HIGH`, description `Testing saved header preservation`, purpose `Approved test expenditure`, cost centre `E2E-OPS`, and a harmless supporting text/PDF file with known contents.

- Cash Advance: required T+1, expense start T+2, expense end T+3, settlement due T+4.
- Fund Request: required T+1; beneficiary BEN-A.
- Reimbursement: valid expense/payment options from the application; line dates within its expense period; lines `400.00` and `600.00` total exactly `1000.00`.

Maintain an inventory containing request IDs/numbers, owner, amount/currency, policy/revision/band, workflow IDs, snapshot counts and final state. Use separate requests for approval, rejection, Return, cancellation, Finance and Free Route branches.

## 5. Baseline and navigation

### ENV-01 — Disabled-feature compatibility

**Precondition:** monetary/funding flags false; existing fixed configuration and an in-flight control request available.

1. Open request lists/details as requester, approver, HR and Finance.
2. Submit an ordinary fixed-route control request through its established flow.
3. Inspect funding availability messages; attempt a new funding draft/submission through the API.
4. Read existing monetary history if this is a repeat run.

**Expected:** ordinary fixed routing and existing approval decisions remain usable. New funding operations are rejected when globally disabled. A monetary-controlled or pinned request must not silently fall back to fixed routing when monetary routing is unavailable. Authorized historical reads remain available.

**Evidence:** effective flags, control request/route, denied funding response and disabled-state screenshots.

### ENV-02 — Navigation and rendered layouts

| Experience | Route |
| --- | --- |
| Monetary administration | `/requests/monetary-policies` |
| Desktop requester list/detail | `/employee/requests`, `/employee/requests/{id}` |
| Desktop funding create/edit | `/employee/requests/funding/new`, `/employee/requests/funding/{id}/edit` |
| Desktop admin detail | `/requests/{id}` |
| Mobile requester list/detail | `/mobile/requests`, `/mobile/requests/{id}` |
| Mobile funding create/edit | `/mobile/requests/funding/new`, `/mobile/requests/funding/{id}/edit` |
| Mobile admin detail | Open from the mobile administration list; record the rendered URL |
| Finance list/detail | `/finance/requests`, `/finance/requests/{id}` |

**Steps:** open each entry point as its intended role; use actual navigation, filters and links.

**Expected:** correct layouts, no console errors, both new type filters, and legacy funding creation links leading to the dedicated form. Monetary content must preserve existing approval, attachment, activity and Finance controls.

## 6. Policy configuration

### CFG-01 — Publish grouped targets

1. As HR-A, configure/publish GP-A and GP-B.
2. Record enabled state, exact published version IDs and revisions.
3. Preview effective members for REQ-A where the administration tool allows it.

**Expected:** eligible non-requester members and no empty effective level. Invalid targets must block monetary selection rather than produce an empty workflow.

### CFG-02 — Save, publish and activate separately

1. Create the baseline Fund Request NGN policy and preview local threshold examples.
2. Save; record `id`, business `revision` and optimistic entity `version` from `/api/request-monetary/config`.
3. Publish through the separate confirmation. Confirm publication alone has not activated a binding.
4. Activate; acknowledge the currency coverage warning and reload configuration.
5. Repeat for Cash Advance and Expense Reimbursement.

**Expected:** immutable published content; exactly one binding per type/currency; monetary mode enabled for that type. The summary identifies the bound revision. Fixed configuration remains retained. Audit records identify the actor and actions.

### CFG-03 — Policy validation matrix

Test each variation independently. Use the authenticated API where native browser validation prevents transmission; restore valid input between cases.

| Variation | Expected |
| --- | --- |
| Blank/>100-character name; unsupported type/currency | Reject |
| No bands or >30 bands | Reject |
| First lower bound >0 | Reject incomplete coverage |
| Negative bound; NGN `0.001` | Reject value/precision |
| Upper bound equal to/below lower bound | Reject |
| Gap: upper `1000`, next lower `1000.01` | Reject |
| Overlap: upper `1000`, next lower `999.99` | Reject |
| Finite highest upper bound; open-ended band followed by another | Reject |
| Grouped target without version; draft/disabled grouped version | Reject |
| Line Manager target with grouped reference | Reject |
| Valid bands supplied out of order | Accept, order by lower bound |
| JPY fractional boundary `1000.5` | Reject without rounding |

**Evidence:** HTTP results, retained form values and unchanged persisted definition/binding for rejected operations. Publication must repeat validation; no partially saved bands or activation changes are acceptable.

### CFG-04 — Optimistic locking

1. Open one policy draft in two tabs and record its version.
2. Save a valid change in A; save/publish from B using the stale version.

**Expected:** conflict; A's saved definition remains intact. Refresh/compare before retrying. Business revision and entity version are different values and must not be interchanged.

## 7. Funding capture

### FUND-01 — Fund Request complete journey

1. As REQ-A, open desktop funding creation and select Fund Request.
2. Enter the baseline header, BEN-A, NGN `999.99`, required T+1; save.
3. Record ID and `FND` number prefix; reload the editor.
4. Verify header/department behavior, purpose, amount/currency, cost centre, date and beneficiary.
5. Choose a supporting file; confirm selection alone does not dirty funding details.
6. Upload; open normal detail and view/download the file; compare contents.
7. Preview the saved request, submit with confirmation and complete APR-01.

**Expected:** one owned draft; immutable type after creation; hidden advance fields do not block Fund validation. Saved amount selects Low. Submission enforces the configured attachment requirement and creates the expected process/snapshot. No payment occurs.

### FUND-02 — Cash Advance complete journey

Repeat FUND-01 through mobile funding creation with `1000.00` and the advance dates in section 4.3; complete APR-02.

**Expected:** `CAD` prefix, irrelevant/disabled beneficiary input, persisted advance dates, High/GP-A selection, required supporting documents and no settlement transaction.

### FUND-03 — Input validation

| Variation | Expected |
| --- | --- |
| Missing title, purpose, cost centre or required date | Reject; preserve entered values |
| Title >200, description >10000, purpose >2000, cost centre >100 | Reject through UI/API validation |
| Missing/zero/negative/above-maximum amount | Reject |
| NGN `1.001`; JPY `1.5` | Reject meaningful excess precision, no rounding |
| NGN `1.00`; JPY `1` | Valid amounts; successful routing still needs the currency binding |
| Unsupported currency | Reject |
| Required T-1 | Reject on save/submission |
| Required > expense start; start > end; end > settlement due | Reject each ordering independently |
| All advance dates equal to a valid required date | Accept ordering |
| Missing/inactive/restricted/suspended beneficiary | Reject, including an ID supplied directly |
| Nonempty request `items` | Reject: funding uses a single header amount |
| Change existing request type | Reject server-side; original details/type retained |

Also save a valid draft, make BEN-A ineligible, then submit: eligibility must be revalidated. Restore BEN-A afterward. For date-expiry revalidation, use a controlled date-test environment; do not alter a shared server clock.

### FUND-04 — Attachment enforcement and recovery

1. Confirm supporting documents are required for the selected funding type.
2. Save a valid draft without attachments; preview, then attempt submission.
3. Confirm submission is rejected without a process/snapshot. Preview success must not bypass attachment validation.
4. Upload a harmless file, reload the normal detail page and verify view/download contents.
5. Submit successfully. Separately test the established attachment removal permissions in editable and noneditable states.
6. On a separate test type/configuration variant, explicitly make attachments optional and verify the effective requirement is respected; restore the baseline afterward.

**Expected:** existing per-type requirements remain authoritative. A failed upload does not count as a stored attachment or successful submission. The dedicated editor reports uploads performed in that session; the normal detail page is the source for the complete attachment list, including earlier uploads.

## 8. Reimbursement and saved-value previews

### EXP-01 — Authoritative reimbursement amount

1. Create reimbursement through the existing UI using valid options and the `400.00`/`600.00` lines.
2. Save/reload and confirm the persisted total is `1000.00` NGN.
3. Run PRE-01 before first submission.
4. Edit/save lines to total `999.99`; preview again, then restore the desired test amount and submit.

**Expected:** the saved line sum selects High at `1000.00`, Low at `999.99`. Browser-supplied totals, policy/band/approver IDs or workflow identifiers cannot override authoritative persisted routing data.

For developer integration tests, use a disposable inconsistent fixture with mismatched line currencies or total versus line sum. Preview/submission must fail without starting work. Do not corrupt shared staging data to create this fixture.

### ROUTE-01 — Boundary and currency matrix

For each of Expense Reimbursement, Cash Advance and Fund Request, create separate requests for every amount in section 4.2. For reimbursement, use valid lines summing to the tested amount. Save/reload, preview and submit each request; compare the selected persisted band and exact amount against the expected table. Finish or retain each workflow according to the inventory; do not reuse a submitted request as an editable draft.

**Expected:** `999.99` selects Low; exactly `1000.00` and `1000.01` select High. The maximum persists without rounding/truncation. Zero and values above the maximum fail validation. Lower bounds are inclusive and upper bounds exclusive across all three handlers, not only the funding form.

Verify `/options` advertises exactly the supported currency set: NGN, USD, EUR, GBP, GHS, KES, ZAR and JPY. With matching test policies, exercise a valid amount for each currency and verify its own binding is used. Repeat JPY with a whole amount and an invalid fractional amount. Removing a currency binding must cause selection failure; there is no conversion or fallback to another currency's policy.

### PRE-01 — Initial reimbursement preview (GAP-02)

**Precondition:** owned editable monetary reimbursement draft; no snapshot/workflow.

1. Open desktop and mobile detail; confirm “No monetary submission yet” and “Preview saved monetary route”.
2. Capture request state, snapshot count and workflow/task baseline using section 17.
3. Preview at `1000.00`; inspect amount/currency, policy revision, bounds, route label and resolved members.
4. Repeat preview/history refresh; compare before/after evidence.

**Expected:** preview works before the first snapshot; it creates no process, snapshot, decision, binding change or notice. Display explains selection is repeated on submission. Refreshes do not duplicate panels/actions.

History preserves `request`, `typeDetails`, `snapshots` and `funding`, adding:

```json
{
  "routing": {
    "monetaryApplicable": true,
    "monetaryRoutingAvailable": true,
    "canPreviewSavedRoute": true
  }
}
```

The capability reflects trusted normal submission ownership/status rules. It does not revoke an otherwise authorized reader's existing read-only preview access.

### PRE-02 — Failure feedback and recovery

1. With only NGN bound for a monetary type, save a USD request and preview.
2. Configure the missing binding or restore a bound currency; retry.
3. Independently disable the selected monetary policy, grouped target or monetary flag; preview again.
4. Restore each configuration and retry after refresh.
5. Test an unpinned fixed-route reimbursement; use the preview API if no monetary UI action is appropriate.
6. Test a returned pinned request after its type switches to fixed mode.

**Expected:** explicit retryable errors, no silent fixed fallback. History/applicability remains readable even when selection is unavailable. Global disablement is separate from applicability. Genuine fixed responses use `monetary:false` and readable text without missing monetary fields. Pinned corrections remain monetary. Loading disables the action; retries replace earlier output.

### PRE-03 — Stale previews

Delay a funding preview, edit the amount and release the response. Separately delay a detail preview, trigger normal detail refresh and release the older response. Use section 18's deterministic browser fixtures for automation.

**Expected:** funding marks the result out of date and requires save/preview again. Detail refreshes discard older results and retain one action/panel. No old route appears as the route for newer visible values.

## 9. Approval processing

### APR-01 — Sequential Line Manager path

1. Submit a Low request with all prerequisites satisfied.
2. Compare snapshot/workflow/resolved levels/initial assignee with configuration and preview.
3. As each assigned Line Manager, approve with identifiable comments.
4. Check requester/approver views and the test mail sink after each transition.

**Expected:** normal sequential progression, only authorized active assignees deciding, final `APPROVED`, correct activity/type/number/recipients and no payment/settlement action.

### APR-02 — Grouped ALL and ANY_ONE

1. Submit High to GP-A.
2. ALL-1 approves: first level still awaits ALL-2.
3. ALL-2 approves: next level activates.
4. ANY-1 approves: level completes; ANY-2's competing assignment becomes non-actionable.
5. Attempt a decision using ANY-2's captured old task ID.

**Expected:** established voting behavior, readable progress/comments/superseded assignments and no stale decision altering the outcome.

### APR-03 — Rejection and terminal states

On separate sequential/grouped requests, reject an active task. Verify `REJECTED`, activity/notices, then attempt an old approval, owner edit and ordinary resubmission.

**Expected:** no normal restart from a terminal state. Exercise cancellation/closure only in their permitted existing states; compare with established rules rather than assuming the enhancement grants new authority.

## 10. Return, correction and historical pinning

### COR-01 — Increase across the threshold

1. Submit `999.99` NGN under policy revision 1; capture snapshot 1 and workflow W1.
2. Assigned approver Returns with a correction comment; verify `RETURNED_FOR_CORRECTION`.
3. REQ-A changes amount to `1000.00`, preserves currency, saves and previews.
4. Resubmit; record snapshot 2 and workflow W2.
5. Complete the new grouped approvals; attempt a captured old W1 task.

**Expected:** same policy ID/revision, High reselected, W2 different from W1, incremented submission number and fresh approvals. Snapshot 1 retains its amount, band, members and workflow reference. Old work cannot decide the new submission.

### COR-02 — Decrease and grouped-target reselection

Repeat with High returned/reduced to `999.99`: the pinned policy selects Line Manager. Separately use a test-only two-band policy targeting GP-A and GP-B; Return across its boundary and confirm a different grouped version can be selected from that same monetary policy.

Keep the ordinary nonmonetary grouped correction regression: it retains its established original grouped version.

### COR-03 — Currency lock and unavailable pinned policy

1. Open a returned funding request with monetary history; inspect disabled currency and explanation.
2. Attempt another currency through the update API; inspect persisted details/history afterward.
3. Disable its pinned policy; attempt resubmission.
4. Restore that exact policy and retry.

**Expected:** currency change rejected before detail mutation. Disabled pinned policy blocks a new submission; newer activation/fixed-mode switching does not bypass pinning. Existing active decisions remain available where applicable.

## 11. Revision activation and fixed-mode compatibility

### REV-01 — Exact binding revision (GAP-03)

1. Clone a published baseline policy, retaining its name; record new ID/revision.
2. Change revision 2's boundary to `2000`; publish without activating.
3. Confirm binding still shows revision 1.
4. Activate revision 2; compare summary with the exact `/config` binding `policyId`.
5. Create a new `1500` request: revision 2 selects Low. Correct a revision-1-pinned request at `1500`: it still selects High.
6. Explicitly reactivate revision 1; verify display, then restore the intended binding.
7. Add USD with another policy/revision; verify both currency rows.

**Expected:** neither shared names nor latest-revision ordering substitute for exact ID resolution. Existing snapshots are unchanged. Summary reloads preserve another draft's dirty fields/state.

### REV-02 — Binding display matrix

| State | Required display/behavior |
| --- | --- |
| Monetary; published/enabled; global on | Exact currency/name/revision; configured and available |
| Monetary; disabled policy | Exact identity; disabled-policy text; new selection blocked |
| Monetary; global off | Configured identity plus global-unavailability message |
| Fixed; retained bindings | Retained/not-used-for-new-fixed-requests text; availability separate |
| No bindings | Explicit no-binding state |
| Unpublished or unresolved bound metadata | Explicit invalid/unavailable state; identifiable unresolved policy without invented revision |

Use ordinary administration for valid states. The last row is a defensive browser-fixture case: normal activation/reference constraints prevent it. Do not bypass staging constraints. Check long names, escaping, multiple currencies and mobile wrapping.

### REV-03 — Switch explicitly to fixed mode

1. Record original fixed Line Manager/Custom/grouped settings.
2. While monetary mode is active, attempt a fixed editor save.
3. Select the explicit fixed-routing action; inspect retained-binding summary.
4. Submit a new request: verify original fixed behavior and no monetary snapshot.
5. Correct a previously monetary-submitted request: verify pinned monetary selection.

**Expected:** fixed editor updates require the explicit mode switch. Existing configuration, bindings, snapshots and unrelated in-flight processes are not rewritten/restarted.

## 12. Save-state correctness and concurrency

### SAVE-01 — Delayed funding creation/update (GAP-01)

Run on a new desktop Fund Request and a returned mobile request with pinned currency.

1. Enter `100`; hold save-response delivery deterministically.
2. Click Save; inspect outbound decimal-string amount `100`.
3. While waiting, change visible amount to `1000`, priority and description.
4. Release the response.
5. Attempt upload/preview/submit without saving again. Attempt departure; cancel the warning.
6. Save again; inspect method, ID and payload, then reload.

**Expected:** the first response acknowledges only its captured values. Later edits remain visible/unsaved and saved-value actions stay guarded. Creation adopts the returned ID; the next save updates rather than creates. Type is temporarily locked during initial save and permanently after creation; a failed initial save restores its previous type state. Pinned currency and irrelevant-field locks remain intact. Header/department metadata survives updates.

For deterministic manual testing in an isolated tab, this DevTools helper holds delivery of actual funding/policy save responses to the UI. The server may already have committed. Install immediately before Save; wait until `window.releaseRequestSave` exists, edit, then release. Reload afterward. This is test instrumentation, not application code.

```javascript
const originalRequestFetch = window.fetch.bind(window);
window.fetch = async (input, options = {}) => {
  const path = new URL(typeof input === 'string' ? input : input.url,
                       location.href).pathname;
  const method = (options.method || 'GET').toUpperCase();
  const hold = ['POST', 'PUT'].includes(method) &&
    /^\/api\/request-monetary\/(funding|policies)(\/\d+)?$/.test(path);
  const response = await originalRequestFetch(input, options);
  if (hold) {
    await new Promise(resolve => {
      window.releaseRequestSave = () => {
        delete window.releaseRequestSave;
        resolve();
      };
    });
  }
  return response;
};
// After editing while the response is held:
// window.releaseRequestSave();
// Reload the tab after the scenario.
```

Release the current response before another save. Throttling alone does not guarantee the race was exercised. The helper delays response delivery, not server execution, so use section 17 to distinguish persisted and visible values.

### SAVE-02 — Delayed policy save/publication (GAP-01)

1. Save a valid policy draft with response held; change bounds/targets before release.
2. Verify returned ID/version adoption and newer edits still unsaved.
3. Attempt publication of that draft without another save.
4. Save the newer valid definition; inspect bounds and optimistic version.
5. With another draft dirty, publish/activate a different saved policy.

**Expected:** save-first publication guard; unsaved editor survives other policy operations. Add/remove-band and select changes count as edits. Next update uses acknowledged entity version. Failed save preserves entered definition.

### SAVE-03 — Failure and uncertain outcome

Produce a controlled server validation failure and verify inputs/dirty state remain. Separately simulate connection loss after sending a save in a dedicated session.

**Expected:** lost response is not proof of rollback. UI advises refresh/check before retry. Inspect request inventory/list/detail before another create; no automatic create retry duplicates drafts. Restore connectivity and edit the verified draft normally.

### CON-01 — Duplicate submit and update/submit race

Use a dedicated saved draft and two authenticated clients. Send concurrent submit attempts; separately race update against submit. Capture request, workflow, snapshot and task evidence before/after.

**Expected:** parent locking serializes operations. At most one successful submission/process/snapshot for that draft state. Update either commits first and selection uses its saved values, or loses edit eligibility and is rejected. Do not require a specific winner; require consistent committed details/snapshot/workflow and no duplicate work.

## 13. Availability and fail-closed selection

Use separate requests and restore each setting before the next case. For global flags, restart/redeploy relevant instances and confirm effective configuration.

| Case | Change/action | Expected |
| --- | --- | --- |
| AVL-01 | Monetary type, unbound currency | Preview/submit fails; no fixed fallback/process/snapshot/initial submission notice |
| AVL-02 | Bound monetary policy disabled | New selection/correction blocked; history remains readable |
| AVL-03 | Selected grouped target disabled/unpublished | Selection fails without partial process |
| AVL-04 | Grouped global flag off | New grouped selection blocked; existing decisions remain available |
| AVL-05 | Empty Line Manager chain/ineligible selected approver | Submission fails without starting approval work |
| AVL-06 | Monetary global off | Applicability retained; new monetary submission/correction blocked; existing decisions continue |
| AVL-07 | Funding global off | New funding creation/submission blocked; history retained |
| AVL-08 | Both funding types disabled | Empty enabled-type capture list with explicit explanation |
| AVL-09 | Approval requirement off for monetary type | Activation/monetary submit rejected; no automatic-approval fallback |
| AVL-10 | Requester exclusion empties grouped level | Actionable selection failure |

Compare section 17 evidence and mail sink before/after failed submission. Earlier draft activity may exist; distinguish it from a new submission notice/event. Unsupported/ambiguous persisted bands are disposable service/database-fixture cases, not configurations to inject into shared staging.

## 14. Authorization and API contracts

### SEC-01 — Roles, ownership and visibility

Repeat direct API calls; do not rely solely on hidden controls.

| Actor/state | Expected |
| --- | --- |
| Unauthenticated | Authentication required; no sensitive redirect/error body |
| Ordinary employee | No policy/config/audit administration or policy/type mutations |
| Owner in DRAFT | Funding edit/save and normal submission capability, subject to prerequisites |
| Owner in RETURNED_FOR_CORRECTION | Normal flow except returned Free Route follows alternate-flow rules |
| Owner submitted/terminal | Normal funding edit/submit rejected |
| REQ-B without visibility | Detail/history/preview/attachments denied; no monetary leakage |
| COPY-A/authorized reader | Existing allowed reads; no owner mutation/capability |
| Assigned approver | Active assigned task only; no other person's decision |
| FIN-A | Existing Finance authority; no acquired owner editing rights |

`canPreviewSavedRoute:false` governs owner UI. Do not incorrectly demand the read-only preview endpoint reject all non-owners: existing request visibility remains authoritative for that endpoint.

### SEC-02 — CSRF and tampering

1. Omit CSRF on an authenticated mutation, then send an invalid token.
2. Get a valid token from `/api/requests/grouped-approvals/csrf` in that session; retry with its returned `headerName`/`token`.
3. Attempt another owner's ID, changed request type and invented policy/band/approver/workflow values.
4. Attempt stale tasks after Return, ANY_ONE completion and Finance intervention.

**Expected:** shared CSRF protection rejects invalid requests; valid authorized operations proceed. Client values cannot expand authority, select a trusted route or revive stale work. Rejections leave no partial mutation/process.

### API reference

Prefix: `/api/request-monetary`. Mutations require the existing authenticated-session CSRF mechanism.

| Method/path | Purpose |
| --- | --- |
| `GET /config`, `GET /audit` | Admin configuration / most recent 100 events |
| `POST /policies` | Create validated draft |
| `PUT /policies/{id}?version={entityVersion}` | Draft update |
| `POST /policies/{id}/publish?version={entityVersion}` | Publish immutable content |
| `POST /policies/{id}/clone`, `POST /policies/{id}/activate` | New revision / explicit binding |
| `POST /policies/{id}/availability?enabled={boolean}` | Policy availability |
| `POST /types/{type}/fixed-routing` | Explicit fixed mode |
| `POST /types/{type}/availability?enabled={boolean}` | Cash Advance/Fund Request availability |
| `GET /options` | Currencies, beneficiaries, enabled funding types |
| `POST /funding`, `GET /funding/{id}`, `PUT /funding/{id}` | Owned funding draft lifecycle |
| `POST /requests/{id}/submit` | Shared submission orchestration |
| `GET /requests/{id}/preview`, `GET /requests/{id}/history` | Authorized saved routing/history reads |

Shared routes include `/api/requests/{id}/attachments`, `/api/requests/approvals/{approve|reject|return}` and `/api/finance/requests/{id}/{approve|reject|return|forward}`. Approval actions use active task IDs, not request IDs. Obtain other payloads from the UI/controller DTOs.

Sample Fund Request creation body: replace date/employee ID with actual run values. Omit department for existing default behavior or supply a valid department under that flow.

```json
{
  "requestType": "FUND_REQUEST",
  "title": "REQ-E2E-FUND-01",
  "priority": "HIGH",
  "description": "Testing saved header preservation",
  "fundRequest": {
    "amount": "1000.00",
    "currency": "NGN",
    "purpose": "Approved test expenditure",
    "costCenter": "E2E-OPS",
    "requiredDate": "<T+1 as YYYY-MM-DD>",
    "beneficiaryEmployeeId": 123
  }
}
```

For Cash Advance creation, retain the header fields, change `requestType` to `CASH_ADVANCE`, and replace `fundRequest` with the following `cashAdvance` object. Replace all date placeholders; do not send a beneficiary or request items for this case.

```json
{
  "amount": "1000.00",
  "currency": "NGN",
  "purpose": "Approved test expenditure",
  "costCenter": "E2E-OPS",
  "requiredDate": "<T+1 as YYYY-MM-DD>",
  "expenseStartDate": "<T+2 as YYYY-MM-DD>",
  "expenseEndDate": "<T+3 as YYYY-MM-DD>",
  "settlementDueDate": "<T+4 as YYYY-MM-DD>"
}
```

Sample policy creation body: substitute the real published grouped version ID.

```json
{
  "name": "REQ-E2E-Fund limits",
  "requestType": "FUND_REQUEST",
  "currency": "NGN",
  "bands": [
    {"lowerBound": "0", "upperBound": "1000", "target": "LINE_MANAGER", "groupedVersionId": null},
    {"lowerBound": "1000", "upperBound": null, "target": "GROUPED", "groupedVersionId": 456}
  ]
}
```

Funding amounts, policy bounds and history amounts serialize as decimal strings. Verify the maximum without JavaScript `Number`/`parseFloat`. Reimbursement retains its existing response contract; do not assume every legacy amount became a string. Meaningful excess fractional precision is rejected without rounding; trailing zeros alone do not imply invalidity.

For new API errors, expect bad requests for validation, conflicts for stale/unavailable state, forbidden administration and not-found missing resources under shared error handling. Record exact status/body and investigate discrepancies. Authentication redirects and legacy ownership/status failures retain existing contracts; not every shared rejection necessarily has the same status.

## 15. Finance, Free Route and shared regression

### FIN-01 — Finance authority

On separate submitted requests, exercise Finance approve/reject/Return/forward, then decide a forwarded request as its eligible recipient.

**Expected:** established permissions/status rules; Finance action/activity evidence; competing assignments superseded where appropriate; old tasks cannot overturn intervention. Monetary history stays intact. Thresholds do not remove Finance authority. A normal-flow returned monetary request remains pinned for normal resubmission.

### FREE-01 — Free Route authority

On an eligible draft use existing Free Route controls; forward and exercise approval/Return on independent branches. Verify returned Free Route alternate resubmission and absence of ordinary owner monetary submission capability.

**Expected:** preserved alternate authority and activity. Do not require a monetary snapshot for an alternate path bypassing normal monetary submission; distinguish its history from normal monetary selection.

### REG-01 — Shared request features

Verify on new types and pre-enhancement controls:

- Copied employees, recommendations and public/internal comments under existing visibility rules.
- Attachment upload/view/download/removal in allowed states; Finance reads and unrelated-user denials.
- Request number uniqueness, type filters, email labels and list visibility.
- Fixed Line Manager, Custom and ordinary grouped paths, including original-path correction pinning.
- Existing in-flight requests and unrelated modules continue without reinterpretation/restart.
- Established edit/cancel/close/task-security rules remain intact.

Check actual approved mail/storage integrations. A queued callback/log alone does not prove inbox delivery. Record unavailable integration checks as blocked acceptance.

## 16. Desktop, mobile and accessibility acceptance

Run funding/reimbursement journeys at 1280×900 and 390×844, check 320-pixel width and a physical mobile browser where available. Use rendered application layouts, not raw fixtures.

| Area | Check |
| --- | --- |
| Layout/navigation | Correct layout, no horizontal overflow, long names/currency rows wrap |
| Keyboard | Logical tab order, visible focus, usable buttons/selects/native dialogs |
| Focus | Save does not steal focus from edited field; error focus appropriate; locked controls remain locked |
| Labels | Accessible names and understandable dates, beneficiary, currency and inclusive/exclusive bounds |
| Announcements | Loading/success/unsaved/stale/error states announced with a screen reader |
| State clarity | Empty history differs from fixed mode; availability communicated in text, not color alone |
| Confirmation | Cancel publish/activate/submit makes no mutation; acceptance targets correct saved record |
| Recovery | Denied/empty/disabled/network/server errors retain context and useful next action |
| Shared controls | Panel preserves approval, Finance, attachment and activity controls without duplication |
| Meaning | Approval/payment distinction and alternate authority remain understandable |

Record reviewer, browser/device, viewport, assistive technology, scenario IDs and exceptions. Automation alone does not close this section.

## 17. Database and workflow evidence

Use read-only queries against the recorded test schema or equivalent authorized APIs/diagnostics. Substitute the actual request ID for `123`; confirm deployed naming/schema. Take before/after readings close together; unrelated concurrent work can invalidate global counts.

```sql
SELECT id, request_number, request_type, status, requester_employee_id,
       workflow_instance_id, approval_route_type
FROM workflow_requests WHERE id = 123;

SELECT submission_no, policy_id, band_id, policy_name, amount, currency,
       lower_bound, upper_bound, target, grouped_version_id,
       workflow_instance_id, submitted_by, resolved_route
FROM request_monetary_snapshots
WHERE request_id = 123 ORDER BY submission_no;

SELECT b.request_type, b.currency, b.policy_id, p.name, p.revision,
       p.state, p.enabled
FROM request_monetary_bindings b
JOIN request_monetary_policies p ON p.id = b.policy_id
ORDER BY b.request_type, b.currency;

SELECT request_id, amount, currency, purpose, cost_center, required_date,
       beneficiary_employee_id, beneficiary_name
FROM request_fund_request_details WHERE request_id = 123;

SELECT request_id, amount, currency, required_date,
       expense_start_date, expense_end_date, settlement_due_date
FROM request_cash_advance_details WHERE request_id = 123;
```

| Operation | Persisted invariant |
| --- | --- |
| Draft save/update | Correct details; no new snapshot/process |
| History/preview | State, process references, snapshots, tasks/votes, bindings and notices unchanged |
| Successful monetary submission | One new submission snapshot paired with its started process; amount/route agree |
| Failed submission | No partial process, snapshot, active task or initial submission notice |
| Correction/resubmit | New workflow/incremented snapshot; earlier snapshot unchanged |
| Policy activation | Binding changes for intended dimensions; request history unchanged |
| Fixed switch | Bindings/history retained; new unpinned fixed requests have no monetary snapshot |

An unchanged request workflow identifier alone does not prove absence of orphan engine processes. Correlate request-scoped Flowable runtime/history/tasks and grouped evidence through supported diagnostics or deployment-verified read-only queries. Capture process/task IDs. Historical processes may remain after Return; distinguish retention from active competing work.

## 18. Automated verification

Run from repository root using Java 21 and project Maven configuration (`mvn` or `mvnw.cmd`). Use an accessible local cache; append `-Dmaven.repo.local=<cache>` when required. Use offline `-o` only when dependencies/plugins are cached.

### 18.1 Focused checks

```powershell
mvn '-Dtest=MonetaryRoutingServiceTest,MonetaryValidationTest,MonetaryRequestControllerTest,MonetaryPersistenceTest' test
mvn '-Dtest=MonetaryBrowserTest' '-Dmonetary.browser.tests=true' test
```

Browser tests default to installed Chrome at `C:/Program Files/Google/Chrome/Application/chrome.exe`; override with `-Dgrouped.browser.path=<chrome.exe>`. Playwright driver setup must work. `-Dplaywright.cli.dir=<prepared-driver-directory>` is an optional environment workaround, not an application dependency.

Relevant deterministic methods in `MonetaryBrowserTest`:

- `delayedFundingSaveKeepsLaterEditsAndPinnedCurrency`
- `initialSaveLocksTypeAdoptsIdAndKeepsLaterAmountUnsaved`
- `policySaveAdoptsVersionWithoutClearingLaterBounds`
- `fundingPreviewBecomesStaleIfEditedWhileLoading`
- `initialReimbursementPreviewsThresholdAndRetriesSelectionErrors`
- `nonOwnerDoesNotReceivePreviewControl`
- `refreshDiscardsPendingPreviewAndDoesNotDuplicateActions`
- `bindingShowsExactRevisionAndRetainedUnavailableStates`

### 18.2 Combined compatibility run

```powershell
mvn '-Dtest=Monetary*Test,WorkflowRequest*Test,GroupedApproval*Test,GenericRequestProcessDefinitionTest,WorkflowRequestHandlerRegistryTest' '-Dmonetary.browser.tests=true' '-Dgrouped.browser.tests=true' test
```

Inspect `target/surefire-reports`; archive log and pass/fail/error/skip counts. Confirm browser tests actually ran rather than being skipped due to missing enabling properties.

The plan records an earlier combined result of 99 tests, zero failures/errors and one intentional PostgreSQL skip, including 14 monetary and 3 grouped browser tests. This is historical evidence, not execution of this guide on the current deployment. Counts may change as tests evolve.

### 18.3 Optional disposable PostgreSQL

These tests create/drop schemas and use Hibernate create/drop for persistence fixtures. Use only a dedicated local disposable `request_enhancement_test` database and role. Current source supplies an empty password; use its dedicated test connection arrangement, not application credentials or weakened shared-server authentication.

```powershell
mvn '-Dtest=MonetaryPostgresMigrationTest,MonetaryPersistenceTest' '-Dmonetary.postgres.url=jdbc:postgresql://127.0.0.1:55432/request_enhancement_test' test
```

Replace `55432` with the dedicated instance's port. The guard permits only localhost/127.0.0.1 with explicit port and that database name. Tests use `monetary_migration_test` and `monetary_runtime_test`; do not run parallel copies against those schemas.

Verify repeat migration, legacy enum/data retention, default fixed mode and rejected invalid references. If unavailable, record the skip/reason and retain the separate deployment migration acceptance requirement.

## 19. Evidence, defects and release decision

### 19.1 Per-case record

```text
Run ID / build / environment:
Case ID / variant / viewport:
Tester / date / role aliases:
Preconditions / effective flags:
Request ID / number:
Policy ID / revision / band / currency:
Workflow IDs / task IDs / before-and-after snapshot counts:
Steps and actual result:
Expected-result comparison:
Evidence links (screenshots, sanitized HTTP, SQL, mail/storage):
Result: PASS | FAIL | BLOCKED | NOT RUN
Defect ID / severity / retest result:
```

Use BLOCKED when required access/configuration/integration is unavailable. Do not convert unexecuted cases into passes because related unit tests exist. Lost edits, unauthorized disclosure/mutation, wrong routes/amounts, duplicate processes, historical overwrite or silent fallback require release-blocking triage.

### 19.2 Traceability and sign-off

| Requirement | Cases/evidence |
| --- | --- |
| GAP-01: later edits remain unsaved | SAVE-01–03, PRE-03, browser methods |
| GAP-02: initial authorized reimbursement preview | EXP-01, PRE-01–03, SEC-01, persistence/controller tests |
| GAP-03: exact revision/availability | REV-01–02, fixture and actual admin screenshots |
| Funding capture/validation | FUND-01–04, validation/persistence tests |
| Routing/corrections | CFG-02–03, ROUTE-01, APR-01–03, COR-01–03, CON-01 |
| Opt-in/fixed/alternate authority | ENV-01, REV-03, FIN-01, FREE-01, REG-01 |
| Read-only preview/atomic submission | PRE-01, AVL-01–10, CON-01, database/workflow evidence |
| Deployed usability/integration | ENV-02, section 16 and actual mail/attachment evidence |

Acceptance requires applicable scenarios executed, critical defects resolved/retested, regression passing with explained skips, and recorded deployed role/UI/accessibility acceptance. Sign-off must identify build/environment, reviewers, business/deployment owners and approved exceptions. Production migration, activation and enablement remain separate rollout decisions under the enhancement plan.

## 20. Cleanup and troubleshooting

Restore flags, availability, routing modes and active bindings to the recorded baseline or agreed staging end state. Restore test employee eligibility. Release held responses/reload instrumented tabs. Cancel/close test requests only through permitted controls; do not delete published policies, snapshots or live workflows as cleanup. Dispose only of the dedicated automated database through its local test procedure. Retain evidence and defects.

| Symptom | First checks |
| --- | --- |
| Funding types absent | Effective flag, per-type availability, preserved disabled seed settings, options response |
| Policy save/activation rejected | Admin employee mapping, global flag, approval requirement, entity version, valid targets |
| Reimbursement preview absent | Saved draft, trusted owner/status capability, monetary mode/history, script/API/console errors |
| No monetary policy | Exact type/currency binding; no currency fallback/conversion |
| Returned request blocked after activation | Pinned policy/target availability and currency; new activation does not replace pinning |
| Amount rounded | Raw decimal strings, database numeric value, client number conversion |
| Actions guarded after successful save | Later edits may correctly remain unsaved; compare payload/current fields |
| Browser setup failure/skips | Enabling properties, Chrome path, Playwright driver/cache access |
| Missing files/notices | Actual integrations, effective requirements, recipients and transaction result |
| Fixtures pass, deployment fails | Rendered layouts, roles, migrations, flags, persisted data and integration configuration |

Preserve failing requests/evidence before changing configuration. Retest affected scenarios after fixes, then relevant compatibility checks. Repeated unchanged runs do not replace missing acceptance coverage.
