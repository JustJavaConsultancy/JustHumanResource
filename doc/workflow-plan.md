# Request Workflow: Multiple Approvers per Level

Implementation plan — 30 October 2026

Status: proposed design. This document adds an implementation plan only; it does not change application behavior.

## 1. Objective

Add multiple approvers within one approval level, with configurable ALL or ANY_ONE decision rules. Preserve all existing request features and their behavior, including sequential Custom paths, Line Manager routing, Free Route, Finance overrides and forwarding, copied employees, recommendations, attachments, request handlers, notifications and request history.

Implement the feature as an opt-in grouped Custom approval configuration and a new versioned BPMN process. Existing configurations and running requests continue through their existing implementation. A deployment must not silently convert old paths or running instances.

The work includes administrator configuration, requester tracking, approver inbox/actions, Finance visibility, desktop/mobile views, durable audit and regression testing. A backend-only change is not complete.

## 2. Current implementation and extension points

The following observations come from static repository review. No application execution, database inspection or test run was performed when writing this plan.

Java paths below are relative to `src/main/java/com/justjava/humanresource/`; resource paths are relative to `src/main/resources/`.

| Area | Existing implementation | Consequence for this change |
| --- | --- | --- |
| Custom path configuration | `approval/entity/CustomApprovalPathStep.java` stores one `approverEmployeeId` per unique path/sequence | Keep this legacy model intact; do not reinterpret its sequence as a group. |
| Path editing | `approval/service/CustomApprovalPathService.java` assigns increasing sequence numbers and prevents duplicate employees in a path | Existing API/editor remain compatible; grouped editing needs its own contract. |
| Route resolution | `CustomApprovalRouteResolver` and `LineManagerApprovalRouteResolver` return a flat sequence | Do not flatten grouped levels into these resolvers or make other modules consume a new shape unexpectedly. |
| Request type | `request/entity/WorkflowRequestType.java` contains route type, custom path ID and process definition key | Add an explicit nullable grouped configuration reference; do not infer grouped execution from employee count. |
| Submission | `request/service/WorkflowRequestService.java`, especially `submit()` | Add a small explicit dispatch branch after existing submission checks; retain the legacy branch. |
| Current BPMN | `processes/genericRequestApprovalProcess.bpmn` has one task assigned to `currentApproverId` | Keep it unchanged; introduce a separately keyed grouped process. |
| Current decisions | `request/workflow/delegate/ProcessRequestApprovalDecisionDelegate.java` moves through flat `approverIds` | Do not reuse its shared decision variables for simultaneous grouped tasks. |
| Task APIs | `request/controller/WorkflowRequestController.java` exposes approval task and decision endpoints | Existing payloads must remain accepted; route grouped tasks to a new service. |
| Inbox | `pendingTasks()` queries assigned tasks through `requestProcessKeys()` | Include grouped instances even after a request type is reconfigured; otherwise active tasks could disappear. |
| Return | `ReturnRequestForCorrectionDelegate` sets returned status and clears workflow reference | Grouped return must additionally resolve all active group tasks and pending records. |
| Finance | `financeDecide()`, `financeForward()` and `recipientDecide()` lock requests, supersede pending steps and retire process instances | Extend this lifecycle handling to grouped records without weakening existing Finance authority. |
| UI | `templates/request/*`, `templates/mobile/request-*`, `templates/finance/request-detail.html`, related page controllers | Extend the correct views and shared DTOs; test all supported entry points. |

Before implementation, establish the exact baseline of cancellation, resubmission, visibility, notifications and Finance behavior in executable tests. Static review is not proof that every existing behavior works correctly.

## 3. Decision semantics

### 3.1 Policies in the first release

| Policy | Approval rule | Rejection rule | Return rule |
| --- | --- | --- | --- |
| ALL | Every active assignment in the current level must approve | One valid rejection immediately rejects the request | One valid return sends the request back for correction |
| ANY_ONE | First valid committed approval resolves the level and advances | First valid committed rejection immediately rejects the request | First valid committed return sends the request back for correction |
| Single-person grouped level | Same behavior as the selected policy; only one vote exists | That employee can reject | That employee can return |

Levels execute sequentially. Approvers within the active level receive tasks concurrently. Future-level employees cannot act before their level activates.

For ANY_ONE, "first" means the first decision successfully committed under the request lock, not the first browser click or HTTP arrival. Conflicting simultaneous attempts cannot both resolve a level. An ALL approval that does not complete the level is an individual vote, not a request-level approval.

### 3.2 Clarification about unanimous rejection

ALL means all must approve, with one rejection stopping the request. It does not require everyone to reject. This preserves the existing sequential route's rejection principle and avoids unresolved mixed votes.

If the business also needs unanimous rejection, treat it as a separate policy design before implementation. Independently choosing ALL approval and ALL rejection produces a deadlock for mixed votes without an explicit tie-break/escalation rule. Do not quietly add that interpretation to this release. Confirm the ALL/ANY_ONE wording with HR during the first phase and record acceptance.

### 3.3 Examples

- Level 1 contains A, B and C with ALL. A and B approve: level remains active, showing 2 of 3. C approves: activate level 2. C rejects instead: reject the request immediately.
- Level 1 contains A, B and C with ANY_ONE. B approves first: level resolves and A/C tasks close as superseded. A's stale rejection cannot reverse it.
- A returns a request from an ALL level after B has approved. Preserve B's vote in that cycle, close remaining tasks, and return the request. Resubmission creates a new cycle; it does not reuse B's old vote.
- Finance approves an eligible request while grouped tasks remain pending. Preserve existing decisions, supersede pending assignments, retire the process and apply the Finance outcome once.

## 4. Compatibility contract and scope

| Existing feature | Preservation requirement |
| --- | --- |
| Legacy Custom paths | Same APIs, editor and sequential behavior; no automatic conversion. |
| Line Manager route | Same manager-chain resolution and sequential decisions. |
| Free Route | Same single current recipient, send/forward rules and final decision behavior. |
| Finance decisions | Same permission, eligible statuses, final-decision protection and audit; covers grouped requests too. |
| Finance forwarding | Retires prior actionable workflow work, leaves one authorized current recipient, retains Finance's existing authority. |
| No-approval request types | Existing automatic approval and handler behavior. |
| Return/resubmission | Existing editing/submission eligibility; grouped cycles retain separate immutable histories. |
| Cancellation/close | Existing eligibility rules; grouped cancellation additionally closes active assignments/tasks. |
| Copied employees/recommendations | Existing visibility and commenting/recommendation capabilities; no new voting rights. |
| Attachments/items/handlers | Existing validation and request-type business effects. |
| Other approval modules | Leave, Loan, Exit, Promotion and payroll routes are out of scope and must not change through shared resolver edits. |
| Historical API responses | Existing fields remain; new grouped summary fields are additive and optional. |

Initial scope excludes majority/quorum voting, weighted votes, approval delegation within grouped levels, dynamic group membership, escalation/timeouts that decide automatically, live-instance migration and mandatory conversion of old paths.

## 5. Configuration and runtime data model

Use new tables for grouped definitions and runtime execution. Keep existing `CustomApprovalPathStep` and `WorkflowRequestApprovalStep` semantics intact. Proposed names are implementation guidance and may follow established naming conventions.

### 5.1 Configuration tables

| Entity | Essential fields and constraints |
| --- | --- |
| `GroupedApprovalPath` | ID, name, description, enabled flag, owning scope where applicable, audit/version fields |
| `GroupedApprovalPathVersion` | Path ID, revision, DRAFT/PUBLISHED/RETIRED state, published by/at, configuration checksum; unique path/revision |
| `GroupedApprovalLevelDefinition` | Path version ID, order, display name, policy ALL/ANY_ONE; unique version/order |
| `GroupedApprovalLevelMember` | Level definition ID, employee ID; unique level/employee |

Add nullable `groupedApprovalPathVersionId` to request-type configuration. It selects an explicitly published version, rather than following a mutable "latest" pointer. Keep `ApprovalRouteType.CUSTOM` as the broad route category to avoid changing other modules' route enum handling.

Configuration rules:

1. A grouped version is allowed only for a Custom route requiring approval.
2. Legacy `customApprovalPathId` and grouped version reference are mutually exclusive for new updates; neither may be missing for an approving Custom type.
3. Existing legacy records remain valid without new fields or migration edits.
4. Published versions are immutable. Editing clones a draft; publishing does not automatically rebind request types.
5. Each level has at least one member and an explicit policy. Employee selection must use existing eligibility/visibility rules and applicable resource scope.
6. Prohibit duplicate employees across levels initially, matching existing Custom-path validation.
7. At submission, exclude requester self-approval consistently with current routing, show the effective route preview, and fail clearly if any level becomes empty. Never auto-approve an empty ALL group.
8. Validate active/eligible members again at submission. After activation, absence or deactivation must not silently change the required vote count; surface a blocked assignment for operational handling.

### 5.2 Runtime tables

| Entity | Essential fields and constraints |
| --- | --- |
| `RequestApprovalCycle` | Request ID, cycle number, engine kind LEGACY/GROUPED where needed, pinned configuration version, process instance/definition reference, state, start/end, initiator; unique request/cycle |
| `RequestApprovalLevel` | Cycle ID, source level ID, order/name, snapshotted policy, status, required approver count, version, activation/resolution time and resolving assignment; unique cycle/order |
| `RequestApprovalAssignment` | Level ID, employee snapshot, state, decision/comments/time, Flowable task ID, request command key, supersession reason/event; unique level/employee and unique non-null task link |
| `RequestApprovalEvent` | Request/cycle/level/assignment references, actor, event type, previous/new state, reason, correlation/idempotency key, timestamp; append-only |
| `RequestApprovalOutbox` | Event reference, notification/business-effect type, deduplication key, dispatch attempts, next retry and delivery state |

Optional request fields: active cycle ID and engine discriminator. Existing `currentApprovalLevel` remains the level ordinal and `totalApprovalLevels` remains the number of levels, not number of employees. Do not populate legacy step rows with a misleading flat list of grouped members. Build a combined read model for history instead.

Use existing auditing/version conventions, foreign keys, lookup indexes and database constraints. Enforce at most one active approval cycle per request using a constraint appropriate to the database. Keep counters derived from persisted assignments or recompute them under lock; do not trust client counts or model vote totals solely in process variables.

## 6. Routing, snapshots and resubmission

Add a `RequestApprovalSubmissionService` or narrowly scoped dispatcher called by existing submission after current attachment/item/handler validation.

Routing precedence:

1. No approval required: existing automatic branch.
2. Free Route operation: existing Free Route implementation.
3. Explicit grouped Custom version: validate flag/configuration, snapshot and start grouped process.
4. Otherwise: existing Line Manager or legacy Custom route/process.

Do not overwrite the legacy `processDefinitionKey` on a request type to enable grouping: Free Route uses the configured process key too. Resolve the grouped process key explicitly from engine selection while retaining legacy behavior. If the new capability is disabled for a grouped-configured request type, block new submission with a clear message; do not silently fall back to another approver route.

Within one transaction: lock/validate request eligibility, allocate cycle, snapshot effective levels/members/policies, set request approval fields, start workflow and store instance reference. Verify that Flowable and JPA participate in the same transaction manager; if they do not, establish an idempotent start/reconciliation mechanism before release. Duplicate submit calls must return the existing cycle or a conflict, never create two processes.

For returned grouped requests, resubmission creates a fresh cycle from the original pinned published version by default, with eligibility revalidation. Do not silently take a newly configured version mid-request. A disabled/invalid pinned route blocks resubmission with an actionable administrative message; explicit route replacement requires a future authorized/audited action or a separately agreed policy. Legacy resubmission behavior stays unchanged.

## 7. BPMN and execution design

Create `processes/groupedRequestApprovalProcess.bpmn` with process key `groupedRequestApprovalProcess`. Do not alter the key or runtime behavior of `genericRequestApprovalProcess`.

Proposed flow:

```text
Start -> load snapshotted cycle -> activate next level
  -> parallel multi-instance approval scope, one task per assignment
  -> evaluate persisted level resolution
       APPROVED and more levels -> activate next level
       APPROVED and final level -> finalize approval
       REJECTED -> finalize rejection
       RETURNED -> finalize return
  -> end
```

Use assignment IDs as task identity inputs, not a shared mutable `currentApproverId`. Decision data belongs to the task/assignment; it must not be written into shared process-level `approvalDecision`, `approvalActorId` or `approvalComment` variables that concurrent tasks can overwrite.

The application decision service determines level resolution under lock. The BPMN completion condition observes that persisted resolution through a controlled variable/state accessor. An early resolving decision ends the multi-instance scope and cancels sibling executions. In ALL, a non-final approval completes only that approver's task. No majority calculation is implied by Flowable instance counters.

Prototype the exact ordering of assignment persistence, task completion, completion-condition evaluation and sibling cancellation against the repository's resolved Flowable version. Verify nested execution cancellation and history recording. Do not manually delete sibling user tasks in isolation; cancel their owning scope through supported process behavior. If the multi-instance prototype cannot meet these invariants, select an explicit parallel subprocess alternative and document it before production implementation.

Finalizers must guard against repeated execution and invoke existing request-type handlers only once. Refactor shared finalization behind a narrowly scoped helper only where necessary, backed by legacy regression tests. Do not run a final approval delegate when deleting a process for Finance override or requester cancellation.

## 8. Decision service, locking and idempotency

Add `GroupedRequestApprovalDecisionService`; existing approval endpoints dispatch by trusted process/assignment identity. The request body supplies task ID, action, comment, expected version and optional idempotency token. Actor identity is always derived from authentication.

Processing sequence:

1. Resolve the task-to-assignment/request relationship without trusting a caller-supplied request ID.
2. Lock the request first, then active level, then assignment in a consistent order used by Finance/cancel operations.
3. Re-read request/cycle/level/assignment/task status after locking.
4. Verify current employee is the assigned employee and has access to the request; the level must be active, the cycle current and the process valid.
5. Validate action and comment; retain required rejection/return comments and existing maximum lengths. Validate expected version.
6. Replay an already completed identical command safely. A conflicting later command receives 409 and current status.
7. Record the individual decision and audit event; calculate resolution from the immutable policy and stored assignments.
8. If resolved, mark remaining actionable assignments SUPERSEDED with a specific reason and resolving event. Do not overwrite prior human votes.
9. Complete the acting task and let BPMN cancel remaining executions/advance/finalize within the coordinated transaction.
10. Write deduplicated notification/business-effect events and commit. Return individual, level and request outcomes.

For ANY_ONE, use the committed winning outcome; losing concurrent decisions do not become recorded votes after resolution. For ALL, later conflicting attempts after an earlier approval cannot change that same assignment's decision. A failed transaction rolls back the vote and task transition together.

Establish lock ordering across existing Finance operations and new grouped decisions to avoid deadlocks. On lock timeout, return a retryable conflict without exposing an uncertain success message. Network retry uses the command key to discover the actual committed result.

## 9. Lifecycle integration and retained side effects

### Return and correction

Record who returned the request and why; resolve the level/cycle as returned; supersede other pending assignments, including future levels; cancel active workflow work; retain completed votes. Use existing returned request status/edit permissions. A resubmission is a new cycle with independent decisions and history.

### Finance approval/rejection/return

Extend the existing row-locked Finance operation using a grouped lifecycle adapter. It must supersede pending grouped assignments, terminate the grouped process, preserve earlier votes, record the Finance action, and maintain existing final-decision constraints and handler calls. A Finance operation racing a group decision rechecks eligibility under the same request lock; never permit two final outcomes.

### Finance forwarding and recipient decisions

Before forwarding, retire any active grouped cycle/tasks and mark pending members superseded by Finance forward. Continue through the existing single Finance-forward recipient flow. Do not create grouped voting rights for prior approvers or copied employees. Finance retains its current final decision rights. Existing recipient replacement and stale-tab checks remain effective.

### Cancellation and close

Retain existing cancellation permissions/status guards. Grouped cancellation additionally closes active process work, supersedes pending assignments and cancels unsent actionable reminders. Close remains available only under existing approved-request rules; grouped history stays readable.

### Visibility, recommendations and attachments

An assigned member needs access to the request, relevant attachments and their action while active. Preserve existing post-decision visibility policy; do not grant broad access merely because a person is a future member. Requester/copied-employee/internal-comment visibility rules remain unchanged. Recommendations/comments are not votes. Return/redact comments through existing role-sensitive DTOs.

## 10. API and read-model changes

Use additive fields and new grouped-configuration endpoints. Keep existing approval action endpoints operational by server-side dispatch; callers should not need to understand the process engine.

| Contract | Proposed behavior |
| --- | --- |
| Grouped path drafts | Create/edit levels, members and policy using explicit grouped DTOs; legacy path payload unchanged |
| Publish version | Validate complete configuration, freeze version and return effective preview |
| Bind request type | Accept grouped version or legacy path reference with mutual-exclusion validation; require current administrator permissions |
| Effective route preview | Given request/requester context, show eligible approvers and exclusions without starting a workflow |
| Existing tasks endpoint | Include grouped tasks with optional cycle/level/policy/progress fields |
| Existing approval endpoints | Dispatch to legacy or grouped decision service based on stored task/process mapping |
| Request details | Add optional `approvalMode`, cycle summaries, grouped levels/assignments and per-viewer action capability |
| Decision response | Return `assignmentOutcome`, `levelOutcome`, `requestStatus` and authoritative permitted next actions for grouped clients |

Use 403 for denied capability, scoped 404 for inaccessible resources, 409 for stale/resolved state, and field-level validation errors for invalid configuration. Preserve existing response contracts for legacy clients where necessary through content/version negotiation or optional new endpoints, rather than replacing void responses unexpectedly.

`requestProcessKeys()` must retain the grouped process key independent of current configuration bindings; disabling or reassigning a request type cannot hide already-running tasks. Query trusted supported process keys and current employee assignment. Update employee inbox aggregation so one request appears coherently without duplicate rows, while preserving its task identity.

Apply existing authentication, resource authorization and CSRF/session protections to new endpoints. Review filter-level exposure before adding APIs; never rely on a hidden UI button as access control. Avoid broad unrelated security changes in the feature patch; add explicit controls for the touched routes and regression coverage.

## 11. UI/UX specification

### 11.1 Administrator: configure and publish

Keep the existing sequential path editor. Add a clearly labeled "Grouped approval path" option and a separate editor using the existing visual system.

Each level card contains:

- Editable descriptive name and visible level number.
- Searchable employee multiselect showing name plus employee number/department to disambiguate people.
- Policy radio buttons, with complete explanations:
  - **Everyone must approve:** all selected approvers must approve; one rejection rejects the request.
  - **First decision resolves this level:** the first approval advances; the first rejection rejects; other tasks close.
- Plain-language Return explanation: any active approver can send the request back for correction.
- Add/remove member, add/remove level and Move up/Move down controls. Drag-and-drop may supplement but never replace accessible controls.

Show inline validation next to the field and a linked error summary. Identify duplicate/ineligible employees, missing policy, empty level and invalid requester exclusions. Do not silently remove selections. Preserve unsaved edits after validation failure and warn before destructive navigation/removal.

Use draft/save/publish states. Publishing presents a route preview, policy explanations and version number. Binding a published version to request types is an explicit action showing affected types and stating that already-submitted requests keep their snapshot. A single-person level remains valid; explain that both policies behave the same with one member.

### 11.2 Approver inbox

Show request number/type/requester, submission time, current level name, policy, progress, pending task and last update. ALL progress reads "2 of 3 approvals received"; ANY_ONE reads "Waiting for the first decision" rather than a misleading completion percentage.

Keep resolved/superseded tasks out of actionable lists. On refresh, provide a neutral status message if a task disappeared because another person acted. Preserve existing search/filter behavior and offer policy/current-status filters where useful. Do not automatically duplicate a request card for every group member.

### 11.3 Approver detail and decision interaction

Show request evidence, policy explanation, member status and the authenticated viewer's action state. Avoid exposing restricted internal comments to other participants merely to populate the member table.

Use explicit buttons: Approve, Reject, Return for correction. Reject and Return require comments under existing rules. Before submission, show an accessible confirmation explaining the effect:

- ALL partial approval: "Your approval will be recorded. Other approvals are still required."
- ALL final approval: "This completes the level and advances the request" or "approves the request" at the final level.
- ANY_ONE approval: "This resolves the level and closes the other pending tasks."
- Rejection/Return: state that the request will stop or return and other pending approval tasks will close.

The server decides the actual outcome; a preview cannot promise that the viewer is still the final voter when someone else is acting concurrently. Disable repeated submission while the call is pending, but preserve typed comments on recoverable failure. An unknown network outcome should offer status refresh/retry with the same command key.

Success feedback distinguishes "Your approval was recorded; waiting for 1 approver" from "Level approved" and "Request approved." Never show full request approval after only a partial vote.

### 11.4 Stale and concurrent actions

If another approver/Finance resolves the level while a page is open, refresh available actions. Return 409 with an understandable message such as "This level was already resolved by another approver. Your decision was not applied." Display the committed outcome, refresh progress and disable stale buttons. Preserve an unsent comment for copying if appropriate; do not automatically post it as a vote/comment.

Use existing refresh mechanisms or bounded lightweight polling only while actionable. Stop polling on resolved pages, handle background tabs and avoid notification floods. Do not require WebSockets for the first release.

### 11.5 Requester tracking and history

Show a sequential level timeline, parallel member decisions within the active level, policy, timestamps and cycle number. Future levels are "Not started". Separate outcomes:

- Human approved/rejected/returned.
- Task superseded because another ANY_ONE member resolved it.
- Task superseded by Return, cancellation or Finance action.

Never label a superseded person as having approved or rejected. Preserve prior cycles after correction and expose which cycle is current. Keep old requests in their existing legacy presentation when grouped fields are absent. Show names from an appropriate historical display snapshot even if employees later leave, subject to privacy policy.

### 11.6 Finance and administrative views

Show current level, policy, partial votes and remaining tasks before intervention. Confirmation explains that the Finance action will retire pending approval work. History must identify the override/forward reason and actor while retaining prior votes. Do not add new Finance actions or change existing eligibility.

### 11.7 Responsive design and accessibility

Cover desktop request/admin/employee/Finance pages and mobile request views. Use a shared grouped-approval fragment/read model where possible to prevent divergent rules. Mobile cards may replace wide member tables while preserving the same information/actions.

Provide keyboard operation, visible focus, correctly associated labels, accessible confirmation dialogs, focus restoration, live status announcements, readable contrast and text/icons in addition to colors. Test at 200% zoom and narrow phone widths. Loading, empty, permission-denied, validation, service-error, retry and terminal states are required designs, not deferred polish.

Update the desktop/mobile request user guides with ALL/ANY_ONE examples, Return behavior and Finance override explanations. Do not expose database/BPMN terminology in ordinary user flows.

### 11.8 UX acceptance scenarios

1. Administrator configures two levels using only a keyboard, finds same-name employees correctly, previews and publishes.
2. Invalid configuration retains entered data and focuses a useful error summary.
3. ALL approver sees partial progress and accurate post-decision feedback on desktop/mobile.
4. ANY_ONE approver with a stale page cannot overturn a completed decision and understands why.
5. Requester distinguishes actual votes from superseded tasks and sees separate correction cycles.
6. Finance sees the effect of intervention before confirming; prior decisions remain visible afterward.
7. Legacy request screens/actions remain unchanged; copied employees cannot vote.

## 12. Notifications and operational reliability

For grouped routes, notify each current-level member once at activation. Do not notify future levels early. Non-final ALL approvals normally update progress without sending requester/fellow-member email for every vote. Activate/notify the next level once; send one final request outcome notification.

Write notification events transactionally with decisions, using keys such as cycle/level/activation/member and cycle/final-outcome. Dispatch after commit with retries and a durable outbox for grouped effects. Inspect existing mail utilities before integrating; an in-memory after-commit callback alone does not guarantee restart recovery. Preserve legacy notification behavior rather than rewriting it unnecessarily.

Before delivering actionable reminders, recheck assignment state; suppress pending reminders for resolved tasks. Already-sent emails should link to authoritative status and tolerate a task that has since resolved. External mail delivery may be at-least-once; document provider timeout reconciliation rather than claiming perfect exactly-once delivery.

Add reconciliation checks for active cycles without live processes, pending assignments without tasks, completed levels with active tasks and duplicate finalization events. Start with an admin-visible diagnostic/report and safe retries; do not silently repair uncertain business outcomes. Do not introduce automatic reassignment or approval when employees become unavailable. Until a separate reassignment feature exists, use existing authorized Finance intervention/correction paths and a clear support procedure.

## 13. Implementation structure and file map

| Component | Proposed location/change |
| --- | --- |
| Grouped configuration | New `approval/grouped/` entities, repositories, DTOs, service/controller; existing path implementation retained |
| Submission dispatch | Small extension in `request/service/WorkflowRequestService.java` calling a dedicated routing/submission service |
| Runtime grouped approvals | New `request/approval/` cycle/level/assignment services and repositories |
| Group decisions | `GroupedRequestApprovalDecisionService`, policy evaluator and authorization checks |
| Workflow delegates | New grouped initialize/activate/finalize delegates; new BPMN file; legacy delegates untouched unless safe shared extraction is proven |
| Lifecycle integration | Adapter called by Finance, forwarding, Return/cancel and finalization paths; preserve current guards |
| API/read model | Additive DTOs and dispatch in `WorkflowRequestController`; update page/Finance controllers as needed |
| Inbox | Extend supported process keys, assigned-task enrichment, visibility and deduplication |
| UI | Shared grouped fragment plus desktop/mobile/admin/Finance rendering and grouped configuration screens |
| Notifications | Grouped outbox/event dispatcher integrated with existing mail templates/services |
| Documentation/tests | User guides, migrations, process/security/concurrency/integration/browser suites |

Avoid expanding the already large `WorkflowRequestService` with all grouped policy logic. Keep dispatch/integration changes small and move new behavior into independently testable services.

## 14. Database migration and deployment

1. Confirm the repository's production migration mechanism and baseline schema; do not infer production behavior from local Hibernate settings.
2. Add new definition/runtime/outbox tables and nullable request/request-type references with indexes. Existing rows remain valid without backfill into grouped configuration.
3. Add application code that reads both models and deploys the separately keyed BPMN, with grouped configuration/submission disabled.
4. Verify startup/deployment does not replace or migrate legacy running instances. Test each process definition version against a disposable PostgreSQL/Flowable database.
5. Enable draft configuration in a controlled environment; publish pilot paths and explicitly bind pilot request types.
6. Enable grouped submissions for the pilot; verify legacy and grouped requests coexist.

Feature controls must distinguish new configuration/submission from processing existing grouped cycles. A rollback disables new grouped submissions but leaves existing cycles actionable. Do not roll back to a binary unable to read/run active grouped instances. Maintain a compatible build until cycles finish, or use an explicit audited operational resolution plan.

Migrations should be additive; do not drop legacy columns/tables or destructively downgrade audit data. Versioned APIs/feature flags should hide unavailable controls cleanly and show clear submission errors rather than silently selecting another route.

## 15. Verification plan

### Baseline and regression

Record results for existing approval resolver, Custom path, generic request BPMN, request service/controller, copied-employee and Finance tests. Inventory missing behavior tests and add meaningful coverage before modifying shared integration paths.

Regression matrix must cover legacy multi-level approve/reject/return, single approver, Line Manager chain, Free Route send/forward/final decisions, automatic approval, Finance final decision/return/forward/recipient, cancellation/close, request-type handlers, attachments/items, copied employees, recommendations and desktop/mobile navigation. Include smoke coverage for other modules using shared approval resolvers.

### New tests

| Layer | Required scenarios |
| --- | --- |
| Configuration | Empty levels, invalid members, duplicate employees, missing policy, immutable publication, version binding, requester exclusion and inactive employee |
| Policy unit tests | ALL partial/final approval, immediate reject/return, ANY_ONE outcomes, one-member group, no empty-group auto-approval |
| Database integration | One active cycle, unique task/member/command keys, concurrent submission, lock ordering, optimistic conflicts and migration compatibility |
| Executable Flowable | Parallel task creation, partial ALL completion, early scope cancellation, next-level creation, correct history and no orphan sibling tasks |
| Concurrency | Approve vs reject; approve vs return; final ALL votes; Finance vs member; forward/cancel vs member; stale retry after next-level activation |
| Authorization | Unassigned actor, copied/future member, foreign request/task, supplied actor spoof, stale/deleted task, restricted attachment/comments and disabled configuration |
| Lifecycle | Returned resubmission cycles; Finance supersession/recipient replacement; cancellation; configuration changes during execution |
| Reliability | Transaction rollback after vote/task work, restart, outbox retry, lost HTTP response, duplicate command and handler finalization retry |
| UI/browser | All UX acceptance scenarios, mobile/keyboard, error/empty/loading states, correct confirmation and no legacy screen regression |

Use real PostgreSQL and an embedded/integration Flowable engine for locking and runtime behavior; H2/XML parsing alone is insufficient. Avoid tests relying on thread timing: coordinate concurrency with barriers/latches and assert one authoritative outcome. Test rollback/failure injection around both JPA and Flowable boundaries.

Acceptance invariants:

- Only current-level assigned members can vote.
- A resolved assignment cannot vote again or change its vote.
- An active level has exactly its snapshotted required membership.
- A cycle has at most one final outcome; resolution cannot be reversed by stale tasks.
- Superseded assignments are not counted as votes.
- Final handler effects and outcome notifications are idempotent.
- Every vote, override, return, supersession and cycle transition has attributable history.
- Previously configured and already-running legacy routes retain their behavior.

## 16. Phased implementation backlog

Estimates assume two engineers, part-time QA and access to an HR decision owner. Roughly 5–7 calendar weeks is a preliminary range, not a commitment; revise after the runtime/transaction prototype.

| Phase | Indicative duration | Deliverables | Exit criterion |
| --- | --- | --- | --- |
| 0: Baseline and semantics | 2–3 days | Regression inventory, HR policy wording, UX wireframes, schema/transaction findings | Existing behavior and policy acceptance documented |
| 1: Runtime prototype | 3–4 days | Parallel task prototype, early cancellation, conflicting-decision and Finance race tests | No orphan tasks; one outcome; JPA/Flowable rollback proven |
| 2: Schema/configuration | 4–5 days | Additive migrations, versioned grouped editor/API, validation and route preview | Publish/bind without changing legacy paths |
| 3: Runtime/services | 5–7 days | Snapshot submission, policies, locking/idempotency, BPMN, finalization | ALL/ANY_ONE executable integration tests pass |
| 4: Lifecycle and UI integration | 5–7 days | Finance/Return/cancel adapters, inbox/read models, responsive progress/actions/history, outbox | Full end-to-end flows and UX acceptance scenarios pass |
| 5: Regression and pilot | 4–6 days | Concurrency/security/browser regression, load/recovery checks, guides/runbook, pilot flags | Compatibility and operational gates pass; HR signs off |

Detailed tickets:

1. `WF-01`: baseline existing request flows and agree decision semantics.
2. `WF-02`: prototype multi-instance task cancellation and common transaction/locking boundary.
3. `WF-03`: create grouped version/level/member and cycle/assignment/audit migrations.
4. `WF-04`: implement configuration API, immutable publication and explicit request-type binding.
5. `WF-05`: implement accessible editor and effective route preview.
6. `WF-06`: implement idempotent submission dispatch, snapshots and cycle resubmission.
7. `WF-07`: implement grouped decision evaluator, authorization, concurrency and new BPMN.
8. `WF-08`: integrate finalizers/handlers, Return, Finance override/forward/recipient and cancellation.
9. `WF-09`: extend task discovery, inbox, visibility and additive read models.
10. `WF-10`: implement desktop/mobile/Finance views, confirmations and stale-state UX.
11. `WF-11`: implement grouped notification outbox, reminder suppression and diagnostics.
12. `WF-12`: complete migration/runtime/security/concurrency/regression/browser tests and documentation.
13. `WF-13`: run pilot, validate recovery/disable procedure and approve wider rollout.

Do not start production group voting before WF-02 establishes cancellation and transaction safety. Configuration/UI scaffolding can proceed independently once DTOs and semantics are agreed. Every ticket includes migration impact, compatibility tests, permissions, failure behavior and a demonstrable acceptance case.

## 17. Operations, risks and rollout gates

Track active cycles/levels, pending assignment age, decision conflicts, finalization failures, orphan tasks, notification retries and outcome rates by route/policy. Avoid sensitive comments in logs and high-cardinality employee labels. Log correlation IDs so support can connect a command, task, assignment, event and cycle.

| Risk | Mitigation |
| --- | --- |
| Concurrent requests resolve twice | Request-first lock ordering, command idempotency, state guards and database constraints |
| Shared Flowable variables overwrite votes | Assignment/task-local inputs; persisted policy resolution |
| Finance leaves sibling tasks active | Unified grouped lifecycle adapter and executable race/cancellation tests |
| Old tasks disappear after configuration changes | Stable supported-process discovery independent of current bindings |
| Group changes affect active requests | Immutable published version and runtime snapshots |
| Duplicate handlers/emails | Finalization guard, idempotent effect keys and durable dispatch |
| Required approver unavailable | Clear blocked-state/support procedure; existing authorized intervention, no silent quorum reduction |
| Legacy behavior changes through shared services | Minimal integration dispatch, preserved branches and regression gates |
| Users misunderstand policies | Full policy wording, preview, action-specific confirmations and UX testing |

Pilot with a small set of new requests and a mixed path (ALL followed by ANY_ONE). Include requester, assigned approvers, administrator, Finance and mobile users. Demonstrate completion, rejection, correction/resubmission, override and a deliberate concurrent conflict. Verify previous legacy running requests complete normally in the same deployment.

Release gates: no unresolved critical authorization/data-consistency defect; no orphan tasks after level resolution; all lifecycle and legacy regression tests pass; migrations tested against representative restored schema; notification and handler deduplication demonstrated; user guide/runbook complete; HR accepts policy wording and pilot behavior.

## 18. Definition of done

The feature is complete only when administrators can explicitly configure versioned grouped levels; assigned employees can decide concurrently under ALL/ANY_ONE semantics; requesters can understand progress and history; Finance/Return/cancel remain consistent; retries and races cannot create conflicting outcomes; and all existing features continue operating as before. Backend, desktop/mobile UI, accessibility, migrations, operational recovery and regression evidence are required deliverables.
