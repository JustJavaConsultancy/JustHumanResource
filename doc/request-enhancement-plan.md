# Request Enhancements: implementation and rollout plan

Updated: 3 November 2026.

Status: core first-release implementation and remediation of the three identified UI/UX gaps are present in the working tree. Automated remediation evidence is recorded in section 13; running-application UI/UX sign-off remains outstanding. Production deployment and business acceptance remain rollout activities. The migration has been exercised against an isolated local PostgreSQL database; no application database was changed during implementation.

## 1. Delivered scope and compatibility

The application now supports approval routing by monetary bands for Expense Reimbursement, Cash Advance and Fund Request. Two new request types, `CASH_ADVANCE` and `FUND_REQUEST`, use the existing request lifecycle, permissions, attachments, notifications and approval engines.

Monetary routing is opt-in. Existing request types retain their fixed Line Manager, Custom or grouped path settings until an administrator explicitly activates a monetary policy. Fixed configuration is retained when monetary routing is enabled, so an explicit switch back can reuse it. Existing running workflows are not migrated or restarted.

Existing grouped `ALL` and `ANY_ONE` voting rules, Finance approval/rejection/Return/forwarding, Free Route, copied employees, recommendations, comments and attachment operations remain shared features. Finance and Free Route retain their existing authority: monetary thresholds are not an unconditional organizational spending authorization limit.

This release captures requests and approvals. Approval does not transfer money, reserve a budget, reimburse an employee or settle an advance.

## 2. Business decisions implemented

| Decision | Implemented behavior |
| --- | --- |
| Initial monetary types | Expense Reimbursement, Cash Advance and Fund Request only |
| Policy dimensions | One activated published policy per request type and currency; routing mode is selected per type |
| Currency handling | NGN, USD, EUR, GBP, GHS, KES, ZAR and JPY; no exchange conversion |
| Storage | `BigDecimal`, PostgreSQL `numeric(19,2)`; maximum 99999999999999999.99 |
| Currency precision | Reject unsupported fractional precision; JPY accepts whole amounts; no silent rounding |
| Request amounts | Strictly positive; zero is allowed only as a policy's first lower bound |
| Band interpretation | Lower inclusive, upper exclusive; adjacent bands must meet exactly |
| Coverage | Complete coverage from zero through an open-ended highest band; no gaps, overlap or fallback |
| Targets | Existing Line Manager chain or an enabled published grouped path version |
| Fund Request shape | A single header amount; no request items, external beneficiaries or bank details |
| Beneficiary | One active, visible, non-suspended employee, selected from existing employee data |
| Cost centre | Required business reference, up to 100 characters; not a budget-control or cost-centre entitlement lookup |
| Department | Existing request department behavior is reused; a new funding-specific entitlement system is not introduced |
| Date handling | Required date must be today or later when saving/submitting; advance dates must satisfy required <= expense start <= expense end <= settlement due |
| Supporting documents | Both new types default to requiring an attachment; existing per-type attachment configuration remains authoritative |
| Corrections | Pin the original monetary policy version and currency, recalculate amount, reselect its band and start fresh approvals |
| Policy changes | Published content is immutable; clone a new draft revision and explicitly publish/activate it |
| Alternate authority | Existing Finance and Free Route permissions are preserved and disclosed in the UI |

These choices replace the open implementation decisions in the original proposal. Further business rules can be added independently without changing the meaning of existing request records.

## 3. Backend implementation

Paths in this section are relative to `src/main/java/com/justjava/humanresource/`.

### Monetary policy administration

`request/monetary/MonetaryPolicyService` implements draft creation, optimistic draft updates, publication, cloning, activation, availability and explicit fixed-mode selection. It reuses `GroupedApprovalConfigurationService.requireAdministrator()` for the existing HR/administrator authorization rules and trusted employee actor resolution.

Policies have a family identifier and increasing revision. Draft updates and publication require the expected entity version and lock the policy row. Cloning locks the family's first policy before allocating a revision. Activation locks the request-type configuration and upserts the unique type/currency binding. Published content cannot be edited; the enabled flag is an operational availability control.

Every policy must contain 1 to 30 bands. Validation rejects unsupported types/currencies, negative or imprecise bounds, missing grouped targets, inappropriate grouped references on Line Manager bands, gaps, overlap and an upper limit on the last band. Grouped targets must be enabled and published. The server repeats validation at publication.

The admin event stream records draft saves, publication, activation, policy availability, request-type availability and changes back to fixed routing. The UI exposes the most recent 100 events; this is not a reporting export or audit retention policy.

### Authoritative amount extraction

`WorkflowRequestTypeHandler` has an optional `monetaryContext` extension with a default empty implementation, preserving existing handlers.

`ExpenseReimbursementRequestHandler` derives monetary context from saved reimbursement details and saved line items. It checks line currencies, precision and the persisted total against the sum. Legacy reimbursement validation remains in place; supported monetary currencies apply when monetary routing is selected.

`FundingRequestService` validates and persists dedicated funding details. `CashAdvanceRequestHandler` and `FundRequestHandler` register the new types in the existing handler registry. Both handlers provide monetary context from stored detail records and revalidate before submission. Their approval hooks do not initiate payments or settlement.

The browser cannot supply a trusted policy, band, approver list or workflow identifier. Selection uses stored request data.

### Submission orchestration

`WorkflowRequestService.submit` locks the owning request, verifies ownership and status, applies existing attachment/item requirements, invokes the type handler and asks `MonetaryRoutingService` to select a route before starting approval work.

1. If no monetary snapshot exists and the type has monetary routing disabled, continue the existing fixed/grouped/automatic flow.
2. Otherwise require monetary routing to be globally available and approval to be required for the type.
3. Resolve the persisted amount and normalized currency.
4. For an initial monetary submission, load the activated type/currency binding. For correction, use the last snapshot's policy identifier and reject a currency change.
5. Require an enabled published policy with matching dimensions and exactly one matching band.
6. Resolve and validate the selected route. Reject an empty Line Manager chain or an ineligible approver; grouped routing retains its requester exclusion and eligibility checks.
7. Start the existing generic sequential process for a Line Manager target, or the existing grouped process for a grouped target.
8. Persist a monetary snapshot in the same transaction as workflow creation.

There is no silent fallback when a binding, policy, target or eligible route is unavailable. Errors leave the submission unsuccessful. Existing notification behavior is retained.

`GroupedApprovalService.startSelectedRoute` permits a monetary correction to select a different grouped version from the pinned monetary policy. Ordinary grouped resubmissions still retain their original grouped version through the existing `start` method.

Draft/returned request updates now acquire the same parent lock used by submission. This serializes changes to monetary details against approval startup. Currency changes after a monetary submission are rejected before detail mutation.

### Snapshot and history semantics

Each monetary submission records its number, policy and band identifiers, policy display name/revision, authoritative amount/currency, bounds, target, grouped version when relevant, resolved levels/member names, submitting employee and workflow instance identifier.

Historical monetary snapshots are not overwritten by correction or policy activation. Changing an amount can move a request both upward and downward between bands of the pinned policy. Each submission needs fresh approvals; previous approvals are not carried over.

Disabling a policy or target prevents a new submission that needs it. Existing approval decisions remain available. Switching a type back to fixed routing does not release already-pinned monetary requests from their policy; those corrections still require monetary routing to be available.

### Shared integration changes

- `RequestType`, request numbering and email labels include the two new types (`CAD` and `FND` prefixes).
- `RequestTypeConfiguration` seeds new types without overwriting administrator configuration on subsequent starts.
- Creation/edit/detail DTOs and service switches support funding details and monetary history.
- Existing fixed Custom/grouped route editors reject updates while monetary mode is active, requiring an explicit mode switch first.
- Other approval modules, BPMN decision rules and existing database records are not reinterpreted.

## 4. Database migration

Apply `db/migrations/20261103_request_monetary_enhancements.sql` after `20261030_grouped_request_approvals.sql`, using the deployment migration procedure before enabling the features.

| Schema addition | Purpose |
| --- | --- |
| `workflow_request_types.monetary_routing_enabled` | Defaults to false for existing type configurations |
| `request_monetary_policies` | Versioned draft/published policy metadata |
| `request_monetary_bands` | Ordered bounds and route targets |
| `request_monetary_bindings` | Unique activated policy per type/currency |
| `request_monetary_snapshots` | Unique request/submission routing history |
| `request_monetary_policy_events` | Administrator action history |
| `request_cash_advance_details` | Advance amount, purpose, references and dates |
| `request_fund_request_details` | Fund amount, purpose, references and employee beneficiary snapshot |

The migration includes reference constraints, unique keys, amount/target/date checks and the request-type enum check expansion needed by older PostgreSQL schemas. It replaces only a single-column request-type check containing the known legacy enum values; unrelated checks remain intact. It is transactional and was tested for repeat execution, data retention, default fixed routing, new enum values and rejected invalid references.

Hibernate schema updates alone do not substitute for this migration's constraints. Existing production data must be assessed through normal deployment review; there is no backfill, exchange conversion, threshold binding or automatic mode activation.

## 5. API contracts and permissions

The new API prefix is `/api/request-monetary`. All new API routes require authentication. Mutations use the existing cookie/header CSRF mechanism; clients can obtain a token from `/api/requests/grouped-approvals/csrf`.

| Method and suffix | Purpose / access |
| --- | --- |
| GET `/config` | Flags, policy versions, bindings and supported type configuration; HR/admin |
| POST `/policies` | Create a validated draft; HR/admin |
| PUT `/policies/{id}?version=...` | Update an unpublished draft with expected version; HR/admin |
| POST `/policies/{id}/publish?version=...` | Publish immutable content; HR/admin |
| POST `/policies/{id}/clone` | Create the next editable revision; HR/admin |
| POST `/policies/{id}/activate` | Explicitly bind the published version; HR/admin |
| POST `/policies/{id}/availability?enabled=...` | Enable/disable policy availability; HR/admin |
| POST `/types/{type}/fixed-routing` | Explicitly select fixed routing; HR/admin |
| POST `/types/{type}/availability?enabled=...` | Enable/disable a new funding type; HR/admin |
| GET `/audit` | Most recent 100 configuration events; HR/admin |
| GET `/options` | Supported currencies, eligible beneficiaries and enabled funding types; authenticated employee |
| POST `/funding` | Create an owned funding draft through the shared service |
| GET `/funding/{id}` | Retrieve an editable funding DTO; editable owner only |
| PUT `/funding/{id}` | Update funding through shared edit/status/ownership checks |
| POST `/requests/{id}/submit` | Submit through the shared owner/status checks |
| GET `/requests/{id}/preview` | Select from authorized saved request values; no workflow mutation |
| GET `/requests/{id}/history` | Funding detail and monetary history through existing request/Finance visibility checks |

Funding amounts, policy bounds and monetary history amounts serialize as decimal strings to avoid JavaScript precision loss. Existing reimbursement response contracts are retained. Preview returns a readable route label and resolved route data; it is informational and selection is repeated at submission.

Validation errors are reported as bad requests, unavailable/stale state as conflicts, unauthorized administration as forbidden, and missing resources as not found. The existing API and legacy route clients retain their established contracts.

## 6. Delivered UI/UX

### Administration

`/requests/monetary-policies` provides a responsive policy editor, type routing modes, active currency bindings, draft/published versions and recent configuration audit. Links are available from the desktop and mobile request administration screens.

Administrators can add/remove bands, choose a Line Manager or published grouped target, preview threshold examples, save, publish, clone and explicitly activate a version. Publication and activation have separate confirmation steps. Bounds are labeled inclusive/exclusive and an empty upper field represents no upper limit. Server validation reports gaps, overlap, unsupported precision and unavailable configuration without clearing the entered draft.

Binding summaries resolve the exact policy identifier and show its revision, monetary/fixed mode and availability, including retained bindings, disabled policies and unresolved metadata. The existing fixed-route editors explain when monetary routing controls a type and require an explicit switch before a fixed-route save. Enabling one currency policy enables monetary mode for the type: other currencies need their own bindings or submissions fail closed. This consequence is included in activation confirmation.

### Request capture

Desktop routes are `/employee/requests/funding/new` and `/employee/requests/funding/{id}/edit`; mobile routes use `/mobile/requests/funding/...`. Both use the application's respective layout and shared responsive form.

The form captures title, priority, optional description, decimal amount, currency, purpose, cost-centre reference and required date. Cash Advance shows expense and settlement dates. Fund Request shows the employee beneficiary. Hidden type-specific inputs are disabled to avoid accidental validation of irrelevant fields.

The workflow is save draft, upload supporting documents, preview the saved approval route and submit. Changed drafts must be saved before upload/preview/submit; choosing an attachment does not incorrectly mark monetary fields dirty. Existing documents and shared actions remain available on the normal request detail page. The dedicated form reports uploads performed in that session.

Amounts are sent as strings without floating-point conversion. JPY uses whole-amount input rules. Editing preserves the stored department and exposes priority/description. After the first monetary submission, the funding editor locks currency and explains how to request another currency.

Controls are labeled, keyboard focus is visible, errors use an alert summary and status messages are announced. Pending operations disable buttons, validation errors retain entered values, and unsaved changes trigger a departure warning. Editable fields remain active during saving. Edit revisions associate each response with its captured payload, so later edits remain unsaved and guarded from upload, preview, submission and publication. Initial funding creation temporarily locks the request type; pinned currency and irrelevant-field locks remain intact. A preview overtaken by edits is discarded, and focus is restored only when this will not take focus from an actively edited field. Disabled features and an empty enabled-type list have explicit messages.

### Details, approvals and Finance

`request-monetary-history.js` adds authorized funding detail and monetary history to desktop employee/admin, mobile employee/admin and Finance detail pages. It displays purpose, amount, currency, dates/beneficiary, policy and band, resolved level/member names and old/new amounts across corrections.

Initial monetary reimbursement drafts can preview saved amounts before their first submission, using server-derived owner/status capabilities independently of snapshot history. Loading, retryable errors, disabled routing and fixed-route responses are explicit. Detail refreshes invalidate older previews across the five shared layouts.

The existing grouped approval panel continues to display `ALL`/`ANY_ONE` progress, tasks and superseded assignments. Existing sequential approval, Finance and Free Route controls continue through their established endpoints and audit streams. Monetary history explains that Finance and Free Route retain their authority, and existing activity/Finance history records interventions.

A monetary request is described as approved for processing, with explicit text that approval does not execute payment or settlement. Type filtering includes both new types. Legacy creation menus link users to the dedicated funding form rather than collecting incomplete funding payloads in the older modal.

### Intentional UI limits

The first release uses browser-native confirmation dialogs and a server-validation error summary, rather than a bespoke dialog framework and per-cell error mapping. It does not add an aggregate monetary reporting dashboard, cross-currency totals, offline drafts or payment/settlement status controls. A full production usability/accessibility acceptance review remains part of rollout; browser interaction tests are not a substitute for that review.

## 7. Verification and evidence

The targeted regression selection covers monetary routing/validation, request creation/edit/visibility and Finance compatibility, handler registration, grouped configuration/security/notifications/runtime and the generic BPMN definition.

Automated checks include:

- Exact threshold selection; adjacent unsorted bands; gaps, overlap and incomplete coverage.
- Positive amounts, storage limits, supported currencies, precision rejection and decimal JSON fidelity.
- Missing bindings/ambiguous bands failing closed and untouched fixed routes when monetary mode is off.
- Pinned policy/currency across correction with band reselection and a fresh submission number.
- Funding date ordering, required beneficiary, exact persisted monetary values and transactional rollback.
- Real JPA policy publication, immutable published edits, stale draft rejection, cloning, activation and retained fixed configuration.
- Grouped monetary reselection after Return, alongside the existing original-path pinning regression.
- New API authentication and CSRF requirements, plus administrator and feature-flag guards.
- Shared submission dispatch and absence of workflow/initial Finance notice when selection fails.
- PostgreSQL migration repeatability, legacy data/default retention, enum expansion and foreign-key enforcement.
- Chrome interaction with the actual form scripts/templates: mobile funding save/upload/preview with a large exact amount, correction editing with locked currency and preserved header fields, monetary history, desktop policy band construction/save, and JavaScript parsing of the integrated legacy templates. Existing grouped browser scenarios also run.

The following automated verification runs passed on 3 November 2026. They establish coverage for the scenarios exercised, not conclusive implementation or UI/UX acceptance; subsequent review reproduced the gaps in section 11:

| Run | Result |
| --- | --- |
| Targeted regression, including Chrome and isolated PostgreSQL | 78 tests; zero failures, errors or skips |
| Follow-up browser/history/legacy-template checks and default H2 persistence | 8 tests; zero failures, errors or skips |
| Working-tree whitespace check | `git diff --check` passed |

The follow-up includes repeated persistence/browser scenarios plus two additional browser checks; these counts should not be added as distinct tests. Monetary JSON string serialization was verified with both Jackson 2 and Jackson 3. The disposable PostgreSQL server was stopped after verification.

Reproduce the normal tests with the project's configured Maven cache:

```powershell
mvn '-Dtest=Monetary*Test,WorkflowRequest*Test,GroupedApproval*Test,GenericRequestProcessDefinitionTest,WorkflowRequestHandlerRegistryTest' test
```

Enable optional local browser checks with `-Dmonetary.browser.tests=true -Dgrouped.browser.tests=true`; the default Chrome path is `C:/Program Files/Google/Chrome/Application/chrome.exe`, overrideable through `grouped.browser.path`.

PostgreSQL tests require an explicitly supplied `monetary.postgres.url`, restricted to `jdbc:postgresql://127.0.0.1:<port>/request_enhancement_test` or localhost, and the disposable `request_enhancement_test` role. Do not supply application database credentials. Migration checks use a separate temporary schema; persistence checks use `monetary_runtime_test`. The default persistence run uses H2.

These are isolated service/runtime/browser fixture tests. They do not certify a deployed application's external mail, file storage, reverse proxy, production data or organization-specific permissions. Those integrations require staging acceptance.

## 8. Controlled rollout

Feature defaults in code are false:

```properties
app.requests.monetary-routing.enabled=false
app.requests.funding.enabled=false
```

Before rollout sign-off, close GAP-01 through GAP-03 and complete the staging acceptance checks in section 11. Automated test results alone do not satisfy that gate.

1. Back up and review the target schema. Apply the grouped migration if outstanding, then the monetary migration through the normal deployment process.
2. Deploy with both new flags false. Let the additive seed create the two new request types as disabled. Verify existing requests, fixed routes, Finance and grouped tasks behave normally.
3. Enable monetary routing in staging. Existing types remain fixed until an explicit policy activation.
4. Create/validate/publish the required grouped targets, then create/publish monetary policies for each intended type/currency. Review exact-threshold examples with business owners.
5. Activate the selected policies. Verify missing currency bindings block submission and that ordinary in-flight requests continue without restart.
6. Enable the funding flag. Types seeded on the earlier disabled deployment retain their disabled state; explicitly enable each type from the monetary admin screen when its routes and attachment policy are ready. If the first deployment starts with the flag true, newly seeded funding types are enabled immediately, so the staged sequence is preferred.
7. Exercise representative employee, approver, HR and Finance accounts across desktop and mobile. Verify attachment storage, real notifications, Return/correction, rejected stale decisions and alternate Finance/Free Route authority.
8. Pilot with a limited operational cohort, review policy events and submission errors, then expand using normal change controls.

Grouped targets also require `app.requests.grouped-approval.enabled` to permit new grouped submissions. Existing approval decisions continue if this flag is later disabled.

### Rollback and operational behavior

Disable funding capture or monetary submissions with the corresponding flag. This stops new work needing that capability; existing approval tasks remain actionable. To permit new fixed-route requests for a type, use the explicit fixed-routing action while keeping historical policies/bindings/snapshots. Returned monetary requests retain their pinned policy and may need the feature restored before resubmission.

Do not delete published policies, bindings, snapshots, new types or detail tables during rollback. Once records contain the new enum values, a binary rollback to code that cannot read them is not compatible; use a forward-compatible recovery build. Disabling features is the supported operational first response.

Monitor missing bindings, unavailable targets, stale draft conflicts and attachment/notification failures. Resolve configuration explicitly rather than manually modifying request amounts, votes or snapshots.

## 9. User operation guide

For administrators: build and publish grouped targets first where needed; draft a complete policy for one type/currency; check threshold examples; publish; activate separately. Publish/activate a new revision for future monetary submissions. Existing monetary corrections retain their earlier revision. Use availability controls carefully because disabling a pinned policy blocks correction submissions.

For requesters: choose New Cash Advance / Fund Request, complete the relevant fields, save, attach supporting evidence, preview and submit. Approval does not mean money was paid. Correct returned requests through the dedicated editor and save before previewing; a changed amount can require a different approval path and fresh approvals.

For approvers and Finance: inspect the amount, policy/band and submission history alongside the existing approval panel. Treat superseded assignments as retired work, not votes. Use the existing authorized decision/Finance controls and record reasons as required by those flows. Finance and Free Route are intentional exceptions to automatic monetary route selection.

## 10. Later work and acceptance boundaries

The following remain explicitly outside this delivery: payment initiation/reconciliation, advance settlement/refunds/overdue recovery, payroll deductions, budget reservation, exchange conversion, external beneficiaries, fund line-item accounting, majority voting, mandatory new Finance spending limits, restrictions removing existing Free Route authority, live-workflow migration and aggregate monetary reporting/export.

Before production enablement, business owners should accept the implemented date rules, employee-only beneficiaries, textual cost-centre reference and preserved alternate authority. These are deployment acceptance decisions, not unimplemented backend placeholders.


## 11. Follow-up review gaps and completion criteria

Review date: 3 November 2026. Status: all three gaps below remain open. Recording these findings does not implement their fixes or supersede the existing compatibility contract. Core monetary routing and funding capabilities remain implemented; the UI/UX cannot yet be described as fully aligned with the requirements.

### GAP-01: edits during saving can be incorrectly marked as saved

Priority: high. Status: implemented; automated checks recorded in section 13; staging acceptance pending. Affected files: `src/main/resources/static/js/request-funding-form.js` and the analogous save-state handling in `request-monetary-policies.js`.

The funding form disables action buttons while a save is pending, but leaves input fields editable. A successful response unconditionally sets `dirty=false`, even when the user changed fields after the submitted payload was captured. The policy editor follows the same pattern and needs equivalent protection.

The Chrome review reproduced saving an amount of 100, changing the visible amount to 1,000 before the response arrived, and then opening a preview for the stored amount of 100 without an unsaved-change warning. The displayed values and saved state can therefore disagree, allowing submission of an older amount while the form shows a newer one.

Required implementation:

- Track a form edit revision and capture the revision with each save payload. Mark the form clean only if no later edits occurred. Alternatively, prevent field edits throughout the save, preserving each control's original disabled state when restoring it.
- Keep preview, upload and submission guarded when visible changes have not been saved. A delayed response must not remove the departure warning for newer edits.
- Apply the same rule to policy draft saves so unsaved band or target edits cannot appear to have been persisted or published.
- Preserve entered values on failures and maintain the existing currency/type locks. A generic re-enable operation must not unlock a currency pinned by an earlier submission.

Acceptance and regression checks:

1. Delay a save response, attempt to change the amount from 100 to 1,000, and verify that either editing is prevented or the newer value remains explicitly unsaved.
2. Confirm that preview and submission cannot proceed as though the visible 1,000 was saved when the server holds 100.
3. Repeat with policy bounds and target selections, including a failed save and a returned funding request with locked currency.
4. Verify the behavior on desktop and mobile and check keyboard focus after controls are restored.

### GAP-02: initial reimbursement drafts lack a monetary route preview

Priority: medium. Status: implemented; automated checks recorded in section 13; staging acceptance pending. Affected file: `src/main/resources/static/js/request-monetary-history.js`, with any necessary additive response metadata in `MonetaryRequestController`.

The shared detail script exits when a request is not a funding type and has no monetary snapshots. Its preview button also requires an existing snapshot. A new Expense Reimbursement draft therefore has no monetary preview before its first submission, even when monetary routing controls its type. The Chrome review reproduced zero monetary panels and zero monetary preview buttons for this initial-draft case.

Required implementation:

- Determine monetary-preview eligibility independently of historical snapshot existence, using authorized server metadata for the request and its current routing configuration.
- Show the saved-value monetary preview to the editable owner of an eligible Expense Reimbursement draft before its first submission, across desktop and mobile.
- Retain the existing ownership/status checks and saved-value semantics. Do not create a workflow or snapshot during preview.
- Distinguish an empty history from an unavailable route. Missing currency bindings, disabled policies/targets and unsupported precision must produce clear feedback rather than silently hiding the preview capability.
- Preserve existing fixed-route behavior and correction requests pinned to an earlier monetary policy.

Acceptance and regression checks:

1. Create a reimbursement draft under an activated monetary policy with no prior snapshots; confirm that its owner can preview the matched band and resolved route before submission.
2. Exercise an amount exactly at a threshold and a changed, saved amount selecting another band.
3. Verify missing-binding and disabled-policy feedback, and confirm that unauthorized users cannot obtain the preview.
4. Cover desktop/mobile views, fixed-route reimbursements and returned monetary requests. Assert that preview creates neither a workflow nor a routing snapshot.

### GAP-03: the active policy revision is ambiguous in the binding display

Priority: medium. Status: implemented; automated checks recorded in section 13; staging acceptance pending. Affected file: `src/main/resources/static/js/request-monetary-policies.js`.

The active-binding summary displays currency and policy name but omits the bound policy revision. Cloned revisions can share a name, so administrators cannot reliably distinguish which published version is active from that summary. This finding was established by source inspection; it was not a separately executed browser reproduction.

Required implementation:

- Resolve each binding by its exact `policyId` and display the bound policy name and revision alongside the currency.
- Distinguish an active monetary binding from a retained historical binding when the type is in fixed mode.
- Display disabled or unavailable bound policies explicitly. If policy metadata cannot be resolved, show an identifiable unavailable-policy state rather than a generic label that appears valid.
- Keep publication and activation separate and preserve existing request snapshots when a newer revision is activated.

Acceptance and regression checks:

1. Publish two revisions with the same name, activate each in turn and verify that the binding summary displays the exact activated revision.
2. Confirm correct summaries for multiple currencies and the retained-binding state after switching to fixed routing.
3. Check disabled-policy and unresolved-metadata states, including a narrow mobile viewport and text-based status labels.

### Verification and acceptance still required

Existing passing reports support the tested routing, funding, persistence and UI scenarios. The browser suite serves actual scripts/templates with mocked APIs and does not render the complete server-side application layouts or establish full application integration and visual/accessibility acceptance. These coverage limitations remain separate from the three confirmed gaps.

Before conclusive sign-off:

- Add automated regression coverage for each gap and rerun the existing monetary, grouped approval and request compatibility checks.
- Test the running application in staging with actual rendered desktop/mobile layouts and representative requester, approver, HR/administrator and Finance roles.
- Exercise initial submission, Return/correction, threshold reselection, attachment storage and notifications through real application endpoints. Verify that existing Finance and Free Route authority remains intact.
- Perform visual and keyboard/accessibility acceptance, including focus, labels, status/error announcements, loading/empty/denied states and responsive layout.
- Record results and any remaining exceptions in this document. Close each gap only after its acceptance criteria pass.
- Complete the documented database migration, configuration, policy activation and feature-enablement steps before claiming deployment completion.

Final completion gate: GAP-01, GAP-02 and GAP-03 are closed with verification evidence; compatibility checks pass; and staging UI/UX and role-based acceptance are recorded. Until then, the correct status is core implementation present, with UI/UX completion and deployment acceptance outstanding.


## 12. Implementation plan to close the review gaps

Implementation status: work packages A through D are implemented, with automated evidence in section 13. Work package E remains open for running-application and business acceptance. The steps below retain the acceptance criteria and scope; earlier release tests are not substituted for remediation evidence.

### 12.1 Scope, delivery order and compatibility

Implement in this order: save-state correctness, server preview-capability metadata, reimbursement preview UI, binding revision display, then regression and staging acceptance. Each change must preserve existing fixed routing, policy/currency pinning, threshold selection, grouped voting, Finance authority and Free Route behavior.

No schema migration, new feature flag, threshold algorithm change, BPMN change or published-policy data rewrite is expected for these fixes. Add fields to the existing history response rather than replacing existing fields or endpoints. Retain decimal-string amounts, existing authentication/CSRF controls, disabled-by-default feature settings and all current authorization rules.

| Work package | Primary files | Deliverable | Completion evidence |
| --- | --- | --- | --- |
| A: save-state correctness | `static/js/request-funding-form.js`, `static/js/request-monetary-policies.js` | Pending responses cannot mark newer edits as saved | Delayed-response and failure browser tests |
| B: preview capabilities | `request/monetary/MonetaryRequestController.java`, request service/capability DTO as needed | Authorized, additive preview eligibility independent of snapshots | Controller/service authorization and read-only tests |
| C: initial reimbursement preview | `static/js/request-monetary-history.js`, detail templates/CSS only where necessary | Preview before the first monetary submission on desktop/mobile | Browser tests plus running-application checks |
| D: binding identity and state | `static/js/request-monetary-policies.js`, policy template/CSS if needed | Exact revision, currency and binding availability shown | Same-name revision and state browser tests |
| E: closure | Monetary/request/grouped tests and this document | Recorded regression and staging acceptance results | Evidence linked to each closed gap |

Java file paths above are relative to `src/main/java/com/justjava/humanresource/`; frontend paths are relative to `src/main/resources/`.

### 12.2 Work package A: preserve save-state correctness (GAP-01)

Use an edit-revision strategy so users can continue editing ordinary fields while a save is pending. Keep the implementation small and local to the two scripts; introduce a shared utility only if the resulting state transitions are actually identical. Do not introduce a new frontend framework.

#### State and event handling

1. Replace the standalone mutable dirty flag with explicit `editRevision` and `savedRevision` state, or keep `dirty` derived from their inequality. Initialization and deliberate draft loading/reset establish a clean baseline.
2. Increment `editRevision` for user changes to persisted fields. Cover text/date/amount input and currency/type/beneficiary/target selections, as well as policy band addition/removal. Selecting a file alone must not dirty funding details. Programmatic initial population must not count as an edit.
3. At save start, capture the current revision and serialize the exact payload immediately, before CSRF retrieval or any other asynchronous step. Associate the response with that operation and draft identifier.
4. Continue preventing duplicate mutation actions while busy. Temporarily lock the request-type selector during an initial funding save because a successful creation makes its type immutable. Preserve the originally selected type on failure; keep the type permanently locked after successful creation.
5. Preserve existing disabled states for pinned currency, irrelevant type fields and unavailable controls. Do not restore controls using an unconditional enable-all operation. Restore focus sensibly if the focused control was temporarily disabled, without taking focus away from a field the user is editing.

#### Successful response handling

- Adopt the returned request/policy identifier and optimistic entity version even if later edits exist. This ensures the next save updates the created draft rather than issuing another create, and policy updates use the latest acknowledged server version.
- Record the captured revision as the saved revision. Mark the form clean only when the current edit revision still matches that captured revision.
- Do not replace newer editable field values with the older response payload. Retain department/header metadata needed for the next funding update without overwriting current visible edits.
- If newer edits exist, announce: the previous values were saved, but current changes still need saving. Keep the departure warning and saved-value action guards active.
- If no newer edits exist, announce successful saving and allow the normal upload/preview/submit workflow.

#### Failure, preview and publication behavior

- A rejected or failed save must retain the current values and unsaved state. A network error with an uncertain outcome must not trigger an automatic create retry; retain the existing instruction to refresh/check the outcome before retrying.
- Require an acknowledged draft with no unsaved changes before funding upload, route preview or submission. Saving a draft must not turn a pending request into an approval or payment action.
- Capture the current revision for an asynchronous route preview. If the user edits while it is pending, discard or explicitly mark the result as out of date and require saving and previewing again. Never display a stale result as the route for the visible unsaved values.
- Distinguish a policy form's local band example from previewing/publishing the saved server draft. For the draft currently being edited, block publication of its saved version while newer edits are unsaved, with a clear save-first message.
- Publishing or activating a different saved policy must not silently clear or overwrite another draft's unsaved form state. Existing explicit discard confirmations remain necessary when changing the draft being edited.

Acceptance tests must intercept/delay requests deterministically rather than depend on timing sleeps. Exercise initial creation, subsequent updates, returned requests, policy updates, validation errors and uncertain network failures. Assert the actual outbound amount/bounds and the dirty-state guard, rather than checking success text alone.

### 12.3 Work package B: expose authorized preview capabilities (GAP-02)

Extend `GET /api/request-monetary/requests/{id}/history` with additive routing metadata. Keep its existing `request`, `typeDetails`, `snapshots` and `funding` fields unchanged. A small response record for the new metadata is preferable to an expanding collection of unrelated map entries.

Proposed nested contract:

```json
{
  "routing": {
    "monetaryApplicable": true,
    "monetaryRoutingAvailable": true,
    "canPreviewSavedRoute": true
  }
}
```

Definitions:

- `monetaryApplicable`: the type supports monetary context and either the type currently uses monetary routing or this request already has a monetary snapshot. Historical pinning therefore remains applicable after a type switches back to fixed routing.
- `monetaryRoutingAvailable`: the current global monetary-routing flag, reported separately from applicability. Disabling the feature must not make an applicable monetary request look like an ordinary fixed-route request.
- `canPreviewSavedRoute`: applicability combined with the existing trusted owner/status rules for normal submission. Reuse those rules through a service capability method rather than independently reproducing them in JavaScript. Returned Free Route requests must retain their existing alternate-flow semantics.

Implementation sequence:

1. Authorize access through the existing request/Finance detail service before constructing or returning metadata.
2. Reuse the already-loaded request and snapshot collection where practical. Fetch the relevant type configuration without requiring administrator access or leaking the full admin configuration response.
3. Derive the capability using the current authenticated employee and existing request service rules. Do not add mutable capability fields to database entities or trust a client-supplied owner/status.
4. Do not call the route resolver when loading history merely to decide whether a preview control should exist. A missing binding or disabled policy must not prevent the detail/history page from loading or hide the action that explains the problem.
5. Preserve the existing preview endpoint's authorized read access. The new owner-facing capability controls UI actions; it must not silently revoke existing authorized preview readers or expand access to otherwise invisible requests.
6. Keep the existing preview endpoint authoritative for policy selection, amount calculation and availability errors. No workflow, snapshot, approval vote, configuration binding or notice should be created by capability lookup or preview.

Add controller/service tests covering initial monetary reimbursement drafts, fixed-route drafts, historical monetary corrections after a mode switch, disabled global routing, returned Free Route requests, non-owner readers and users with no request visibility. Assert unchanged legacy history fields and the absence of side effects.

### 12.4 Work package C: render the reimbursement preview correctly (GAP-02)

Refactor `request-monetary-history.js` to treat three decisions independently: displaying funding detail, displaying historical snapshots, and offering a current saved-value preview.

1. Replace the early return based only on funding/snapshot count with a condition that also considers monetary applicability from the authorized response.
2. Render the monetary panel for an applicable reimbursement draft even when history is empty. Show a neutral message such as "No monetary submission yet"; do not claim an unsubmitted monetary draft uses fixed routing merely because no snapshot exists.
3. Offer "Preview saved monetary route" when the server capability allows it, without requiring a previous snapshot. Existing funding forms continue using their dedicated preview action.
4. Display an explicit globally-disabled state when relevant. Keep missing-binding, disabled-policy/target and precision errors visible as actionable preview feedback. Do not convert a failed monetary selection into fixed-route success.
5. On successful monetary preview, display saved amount/currency, policy name/revision, inclusive/exclusive bounds, route label and resolved level/member names. Reuse the existing escaped-text route rendering. Use a separate fixed-route message if the server now reports fixed mode rather than interpolating missing monetary fields.
6. Mark the result as informational and checked again at submission. Clear or mark stale any prior preview when a refreshed request state or subsequent result supersedes it.
7. Keep the action disabled during its own request, announce loading/success/error with accessible status text, and provide an explicit retry for failures. Avoid silently suppressing errors for an already-visible monetary panel.
8. Ensure panel refresh does not append duplicate actions or leave an old response visible after a later refresh. Keep current ownership checks for the funding edit link and preserve all existing detail/approval/Finance controls.

Use the same script for the existing desktop employee/admin, mobile employee/admin and Finance detail templates. Add markup/CSS only where the running layouts require it; do not copy the route logic into separate page implementations.

Browser tests must cover initial reimbursement drafts with no snapshots, exact-threshold previews, subsequent saved amounts, missing bindings, disabled policies, fixed-route responses and pinned corrections. Verify both positive capability and denied/non-owner states. A successful preview must leave workflow/snapshot counts unchanged in backend integration tests.

### 12.5 Work package D: identify the exact bound revision (GAP-03)

The existing admin config already returns bindings with `policyId` and policy views with `id`, `revision`, `state` and `enabled`. Use these fields rather than adding a new endpoint or selecting a policy by name/latest revision.

1. Build an identifier-indexed lookup from the returned policy list after each config reload.
2. For each type/currency binding, resolve exactly its `policyId`. Render the currency, policy name and revision together, for example: `NGN - Funding limits - revision 2`.
3. Render binding mode and policy availability as separate text states. Use the following matrix so fixed-mode historical bindings and unavailable monetary bindings cannot look operationally active:

| Type mode | Bound metadata | Required display |
| --- | --- | --- |
| Monetary | Published and enabled | Configured binding, exact revision; availability also reflects the global flag |
| Monetary | Published but disabled | Exact revision with disabled-policy status; new submissions are blocked |
| Monetary | Not published | Exact identifier/revision with invalid/unpublished status |
| Monetary | Missing from response | Policy identifier with metadata-unavailable status; no invented revision |
| Fixed | Any retained binding | Retained binding, not used for new fixed-route requests; show its availability separately |
| Either | No bindings | Explicit no-binding state |

4. Show the global routing-disabled message alongside configured bindings without implying they are currently available for submission.
5. Refresh the summary after successful activation, availability changes and mode switches. Maintain unsaved editor state under work package A; rendering the summary must not reset its draft fields or dirty revision.
6. Continue escaping policy names and rendering status in text, not color alone. Allow long names, revision labels and currency rows to wrap at mobile widths.
7. Preserve separate publish/activate actions and all persisted policy/request snapshot semantics. This package changes presentation, not binding selection.

Add browser fixtures with two published revisions sharing a name, activate each in turn, and assert the exact policy identifier/revision shown. Cover multiple currencies, fixed-mode retained bindings, global disablement, disabled/unpublished policies and unresolved identifiers. Check a narrow viewport and keyboard access.

### 12.6 Verification sequence and staging acceptance

Run focused tests while implementing each package, then perform one combined regression run after the code stabilizes. A failure or subsequent relevant code change justifies repeating the affected checks; repeated unchanged runs do not substitute for missing coverage.

| Layer | Required coverage | Evidence to record |
| --- | --- | --- |
| Service/controller | Preview metadata, authorization, fixed/pinned modes, read-only behavior and existing API fields | Test class/methods and run result |
| Browser fixtures | Delayed saves, preserved edits/locks, initial reimbursement preview, binding revisions and error states | Test names, viewport and assertions |
| Database/workflow integration | Threshold boundaries, correction pinning and no workflow/snapshot writes during preview | Counts before/after and test result |
| Compatibility regression | Existing monetary, grouped, request, handler and generic process tests | Command, environment and pass/fail/skip counts |
| Running application | Actual Thymeleaf layouts, real endpoints, representative roles and configured test data | Staging build/configuration and scenario outcome |
| UI/UX acceptance | Desktop/mobile, keyboard/focus, status/error announcements, visual consistency and recovery states | Reviewer/date, viewport and any remaining exceptions |

Suggested combined Maven selection, using the existing project environment/cache configuration:

```powershell
mvn '-Dtest=Monetary*Test,WorkflowRequest*Test,GroupedApproval*Test,GenericRequestProcessDefinitionTest' '-Dmonetary.browser.tests=true' '-Dgrouped.browser.tests=true' test
```

Use the documented optional PostgreSQL configuration only with its dedicated disposable local database. Keep application data, production credentials and live workflows out of automated test setup. Existing PostgreSQL migration evidence remains valid unless the fixes unexpectedly require a schema change; no migration alteration is planned.

In staging, provision test-only policies with two revisions sharing a name and two bands around a known threshold. Use owned reimbursement and funding drafts, a returned monetary request and a fixed-route control request. Exercise requester, approver, HR/admin and Finance accounts. Verify actual notifications/attachments through approved test integrations and preserve existing alternate authority.

Complete the end-to-end sequence: configure/publish/activate, save while attempting a later edit, save the remaining changes, preview the exact saved amount, submit, Return, correct across the threshold, preview and resubmit. Confirm fresh approvals and retained historical snapshots. Separately switch a type back to fixed mode and verify both new fixed-route behavior and existing pinned corrections.

### 12.7 Completion tracking and release gate

| Item | Initial status | Required closure evidence |
| --- | --- | --- |
| GAP-01 | Open; implementation planned | Delayed responses cannot lose dirty state or submit older values as current; funding and policy tests pass |
| GAP-02 | Open; implementation planned | Authorized initial reimbursement preview works on desktop/mobile without creating workflow/snapshot data |
| GAP-03 | Open; implementation planned | Exact revision and binding availability/mode states are unambiguous across tested conditions |
| Compatibility regression | Required after fixes | New and existing relevant checks pass; any skips are explained |
| Running-application UI/UX acceptance | Outstanding | Rendered layouts, roles and accessibility/recovery scenarios are recorded |
| Production rollout | Not performed by this plan update | Existing migration/configuration/enablement procedure completed separately |

When implementing, record the changed files and specific tests under each gap, then update its status only after its acceptance criteria pass. Update section 6's behavioral descriptions and section 7's evidence to match the final code. Preserve the distinction between automated implementation verification, staging acceptance and production deployment.

Conclusive implementation/UI/UX sign-off requires all three gaps closed, successful compatibility regression and recorded running-application acceptance. Automated remediation evidence is recorded below; running-application acceptance and production rollout are not claimed.


## 13. Remediation implementation evidence ? 3 November 2026

The three source-level gaps are implemented. They are not marked conclusively closed for release: the section 11 staging, role-based and UI/UX acceptance gate remains outstanding. No application database, production configuration or live workflow was changed in this remediation run.

### GAP-01: save-state correctness

Files: `static/js/request-funding-form.js`, `static/js/request-monetary-policies.js`, and `MonetaryBrowserTest.java` (resource/test paths as above).

Both editors capture the payload and edit revision before asynchronous CSRF retrieval, acknowledge the returned identifier/version, and retain later edits as unsaved. Input and change events cover ordinary controls; funding file selection is excluded. Saved-value actions and publication of an edited draft require a clean acknowledged revision. Initial creation locks request type, returned funding currency stays locked, failures retain visible edits, and previews overtaken by edits are discarded. Button focus is restored only when focus has fallen to the document body.

Deterministically held browser responses verify outbound old/new amounts, update rather than duplicate creation, adopted optimistic versions, retained bounds, save-first publication, upload/preview/submit guards, server-validation failures and pinned currency. Test methods: `delayedFundingSaveKeepsLaterEditsAndPinnedCurrency`, `initialSaveLocksTypeAdoptsIdAndKeepsLaterAmountUnsaved`, `fundingPreviewBecomesStaleIfEditedWhileLoading`, `policySaveAdoptsVersionWithoutClearingLaterBounds`, plus the existing correction-editor regression. Fixtures use 390 x 844 mobile and 1280 x 900 desktop viewports across the suite.

### GAP-02: authorized initial reimbursement preview

Files: `MonetaryRequestController.java`, `WorkflowRequestService.java`, `static/js/request-monetary-history.js`, and the five employee/admin/mobile/Finance detail templates.

History preserves its existing fields and adds a `RoutingCapabilities` record. Applicability includes historical pinning; global availability is separate; preview capability reuses normal owner/status/Free Route submission rules. Capability lookup does not resolve a route. The existing preview endpoint retains its authorized read contract.

The shared panel displays an empty monetary history and offers initial saved-route preview without requiring snapshots. Errors are retryable and announced; fixed mode has a separate message. A generation counter discards stale history/preview responses. The five detail refresh functions dispatch `request-monetary:refresh`, clearing prior results and preventing duplicate actions.

Evidence: seven `MonetaryRequestControllerTest` cases cover initial/fixed/pinned/unavailable/non-owner/Finance/denied states. `previewCapabilityUsesNormalSubmissionOwnershipStatusAndFreeRouteRules` exercises actual service capability logic. Browser cases cover initial threshold preview, missing binding, disabled policy, fixed responses, denied controls and stale refreshes. `MonetaryPersistenceTest.controllerPreviewReadsPersistedPolicyWithoutCreatingWorkflowOrSnapshot` invokes the controller with real JPA policy/binding/band/request/snapshot repositories at the exact 1,000 threshold; request workflow identifiers and snapshot counts are unchanged before/after. Its employee, amount handler and route resolver are test doubles; it does not claim a deployed reimbursement/storage/workflow integration test.

### GAP-03: exact binding identity and availability

File: `static/js/request-monetary-policies.js`. Bindings use an identifier-indexed policy lookup and display the resolved revision. Fixed routing explicitly labels retained bindings; disabled, unpublished, globally disabled and missing-metadata states are textual. Config reloads retain draft field/revision state. Existing publish/activate and persisted snapshot semantics are unchanged.

`bindingShowsExactRevisionAndRetainedUnavailableStates` uses two same-name published revisions and checks that binding identifier 2 displays revision 2, alongside disabled-policy, unresolved identifier and retained-binding states at mobile width. Activation of each revision in a running application, keyboard/accessibility review, and the complete availability-state matrix remain staging acceptance scenarios.

### Regression result and remaining acceptance

Combined regression: **99 tests, zero failures, zero errors, one intentional skip** (`MonetaryPostgresMigrationTest`, disposable PostgreSQL not configured). This includes **14 monetary browser tests and 3 grouped browser tests**, all passing. H2 persistence, controller/service authorization, monetary routing/validation, request/handler compatibility, grouped runtime/security/notifications and generic BPMN checks passed. Run completed 3 November 2026 at 20:21 WAT. `git diff --check` passed. Full run log: `target/request-gap-evidence/request-gap-final-tests.log`.

The optional PostgreSQL migration test is intentionally skipped unless its explicitly disposable database is configured. This remediation has no schema change; earlier migration evidence remains separate. The browser suite serves real scripts/templates through synthetic APIs and does not render full Thymeleaf layouts or exercise real role accounts, mail or attachment storage. Network-uncertain saves, the full availability matrix, complete keyboard/focus review and full role-based correction flows must still be exercised in staging before conclusive closure.

Reproduction uses Java 21, Maven 3.9.11, the existing local dependency cache and installed Chrome. The sandbox denied Java archive cleanup in the external Maven cache; the combined run used approved cache access. Playwright's cached Windows driver was extracted locally and supplied through `-Dplaywright.cli.dir` (no browser/dependency download). Test reports are under `target/surefire-reports`.

```powershell
mvn -o '-Dmaven.repo.local=C:/Users/Kazeem Akinrinde/.m2/repository' '-Dplaywright.cli.dir=C:/workspace/JustHumanResource/target/request-gap-evidence/playwright-driver' '-Dtest=Monetary*Test,WorkflowRequest*Test,GroupedApproval*Test,GenericRequestProcessDefinitionTest,WorkflowRequestHandlerRegistryTest' '-Dmonetary.browser.tests=true' '-Dgrouped.browser.tests=true' test
```

Release tracking: GAP-01/02/03 implementation delivered; automated checks recorded above; running-application acceptance outstanding; production rollout not performed. Continue with the existing section 12.6 staging sequence before signing off release readiness.
