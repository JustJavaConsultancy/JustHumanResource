# Grouped request approvals: implementation and operations

Implemented 30 October 2026. Design reference: [workflow-plan.md](workflow-plan.md).

## Behavior

Grouped Custom routes support sequential levels with parallel approvers inside each level:

- **ALL:** everyone must approve; one rejection rejects the request.
- **ANY_ONE:** the first committed decision resolves the level. Approval advances it, rejection rejects the request, and Return sends the request back for correction.
- Any active member can Return. Other pending assignments close as superseded, preserving actual votes.
- Requester self-approval is excluded at submission. An empty resulting level is rejected as invalid rather than automatically approved.
- Simultaneous decisions serialize on the request row. A stale losing decision receives a conflict. Repeating an identical already-recorded decision is idempotent.

Unanimous rejection and mixed-vote tie-break rules are not included. Existing Line Manager, sequential Custom, Free Route, Finance authority, copied-employee and request-type handler behavior remains in place. Existing running process instances are not migrated.

## Enablement

The new-submission/configuration flag defaults to false:

```properties
app.requests.grouped-approval.enabled=true
```

Supply this property through deployment configuration or the Spring Boot startup argument `--app.requests.grouped-approval.enabled=true`. The repository's existing `application.yml` was not edited for this feature.

Before enabling in a deployed environment:

1. Back up the database and validate the existing schema/table names and deployment migration procedure.
2. Apply [20261030_grouped_request_approvals.sql](../db/migrations/20261030_grouped_request_approvals.sql) through that procedure. It adds tables, nullable request-type configuration, foreign keys and a partial unique active-cycle index; it does not convert existing approval paths.
3. Deploy the application, including `groupedRequestApprovalProcess.bpmn`, while retaining the legacy process definition.
4. Verify existing request inboxes and requests continue to work.
5. Enable the property for a controlled pilot.
6. As HR/administrator, open `/requests/grouped-approval-paths`, also linked from desktop/mobile request administration.
7. Create and save a draft with named levels, eligible employees and ALL/ANY_ONE policies. Preview and publish the immutable version.
8. Explicitly bind the published version to a pilot request type. Binding replaces the route only for new requests. The legacy route editor can restore a sequential/Line Manager configuration.
9. Submit synthetic/pilot requests and exercise approval, rejection, Return/resubmission, cancellation and Finance intervention before wider use.

The flag blocks new grouped submissions, publication and binding, but does not block decisions on already-running grouped requests. A grouped-configured request never silently falls back to another route when disabled. Returned grouped requests remain pinned to their original published version, even if type configuration changes; disabled/ineligible pinned routes block resubmission with a useful error.

## User interface

The editor supports employee search, keyboard-accessible selection, Move up/down, draft validation, preview, publication, cloning, enable/disable and request-type binding. An effective-route preview can show what remains after excluding a selected requester. Published content cannot be edited; clone a new draft instead.

Desktop, employee, mobile and Finance request detail pages load a shared grouped panel showing cycle history, policy, progress and individual decisions. The authenticated current member receives decision controls. A dialog explains the effect, requires rejection/Return comments and prevents repeated submission while pending. Conflict handling preserves the entered comment and refreshes the outcome. Status refresh pauses for background tabs and terminal requests.

Desktop/mobile employee inboxes show grouped level, policy and current progress. Their Review grouped approval link opens the detailed evidence/decision panel. Legacy tasks keep their existing inline actions.

Future members do not gain viewing rights solely from unactivated assignments. Activated approvers retain historical access consistent with existing approver visibility. Copied employees and recommendation authors receive no voting authority. Grouped comments are restricted to the author and existing HR/admin detail views rather than exposed through every participant row.

The grouped panel is authoritative for grouped progress and current request status. Existing request-type views remain available, and final page-level actions may require a page refresh after another person changes the request.

## Architecture and transaction boundaries

New Java components live under `request/grouped/`. The core entry points are:

- `GroupedApprovalConfigurationService`: permissions, feature flag, versioned definitions, publication and effective routing.
- `GroupedApprovalService`: snapshots, runtime levels/assignments, decisions, lifecycle supersession, history and diagnostics.
- `GroupedApprovalController`: administrator configuration and operations page/API.
- `GroupedRequestApprovalController`: authenticated grouped decisions, history and CSRF bootstrap.
- `GroupedApprovalNoticeDispatcher`: durable grouped mail dispatch/retries.

Grouped versions use one family/revision record per version rather than a separate path-container entity. Definition members use an ordered collection table. Runtime cycles, levels and assignments are separate records; legacy approval steps are not repurposed as parallel votes.

The grouped BPMN uses a parallel multi-instance user task per level. The application resolves policy using persisted votes under a request lock. Only the level-resolution signal crosses the process boundary; actor/comment/decision inputs are not shared mutable process variables. A completion listener rejects direct generic task completion unless the domain service has already recorded a valid assignment decision.

JPA writes and Flowable commands must use the same datasource/transaction manager. Runtime tests demonstrate rollback of vote, task completion and finalization together using `JpaTransactionManager` and Spring-managed Flowable. If deployment changes those transaction boundaries, repeat the rollback/race tests before enabling.

Submission and cancellation acquire the same request lock used by Finance and grouped decisions. Finance supersession retires grouped assignments before the existing process cancellation/final decision behavior. The request-wide lock establishes ordering for member decisions racing Finance/cancellation.

## API compatibility and security

Legacy approval URLs remain available. They dispatch grouped tasks to the new domain service and require the grouped CSRF token when used for a grouped decision. New UI clients use the protected grouped decision endpoint directly.

New endpoints:

| Endpoint | Purpose |
| --- | --- |
| `GET/POST /api/grouped-approval-paths` | List versions/create draft |
| `PUT /api/grouped-approval-paths/{id}?version=...` | Update an expected draft revision |
| `POST .../{id}/publish?version=...` | Publish an expected draft revision |
| `POST .../{id}/clone` | Create another family revision |
| `POST .../{id}/enabled?enabled=...` | Change availability without changing approved content |
| `GET .../{id}/preview?requesterId=...` | Effective member preview |
| `PUT .../request-types/{code}` | Bind `{"pathVersionId":123}` |
| `GET .../diagnostics` | Read-only health sample |
| `GET /api/requests/grouped-approvals/csrf` | Session CSRF token/header information |
| `GET /api/requests/grouped-approvals/{requestId}` | Authorized cycle history/current status |
| `POST /api/requests/grouped-approvals/decisions` | `taskId`, `action` APPROVE/REJECT/RETURN, optional `comment` |

The new API security chain requires authentication and cookie/header CSRF for mutations. Administrator methods also check the existing HR/admin capabilities. Decision actors come from authenticated employee identity. New errors use problem details for validation, denied access, unavailable resources and stale/locked state. Existing unrelated API security behavior was not globally rewritten.

## Notifications and diagnostics

Grouped activation/final outcome events are recorded in `grouped_approval_notices` with unique deduplication keys. A lightweight timer submits work every 15 seconds by default; adjust with `app.requests.grouped-approval.notification-delay-ms` if required. A dedicated single worker runs mail delivery, preventing SMTP from blocking the shared scheduler or accumulating overlapping dispatch batches.

The grouped dispatcher calls the throwing `EmailService` transport directly, because the legacy request mail helper intentionally catches send errors. Failures retain their record, increment attempts and back off to an hourly retry cap. Obsolete pending invitations and superseded cycles are suppressed. Ordinary logs do not contain comments or mail contents.

SMTP cannot guarantee exactly-once delivery after an ambiguous timeout or crash between sending and recording delivery. Investigate delivery/provider logs before manually forcing retries. This implementation does not add automatic reassignment, escalation or decision-making for unavailable approvers.

The configuration page's operational check calls the HR/admin-only diagnostics endpoint. It samples up to 100 active cycles and retrying notifications, showing IDs/counts/attempts without sensitive content. Investigate:

- Active cycle with zero tasks, or live task count differing from pending current-level assignments.
- Notices with repeated attempts: verify mail settings, recipient eligibility/address and transport health.
- Blocked ALL level: identify unavailable members and use the existing authorized Finance intervention/correction procedure.

Diagnostics are read-only and intentionally do not auto-repair uncertain outcomes. Consult `grouped_approval_events`, assignments and Flowable history before intervention. Prior votes must never be converted into an unrecorded override.

## Validation and test commands

Tests use synthetic data. `GroupedApprovalRuntimeTest` starts isolated JPA/Flowable infrastructure; it does not read the application's database connection settings. The default database is H2. An optional PostgreSQL URL is restricted to a dedicated local database named `grouped_workflow_test`, using test user `grouped_test`.

```powershell
mvn '-Dtest=GroupedApproval*Test,WorkflowRequest*Test,GenericRequestProcessDefinitionTest,ApprovalRouteResolverFactoryTest,CustomApprovalRouteResolverTest,EmployeeExitProcessDefinitionTest,PromotionWorkflowRuntimeTest,PromotionBpmnDefinitionTest' '-Dgrouped.browser.tests=true' test
```

The optional browser tests use installed headless Chrome by default. Override `-Dgrouped.browser.path=...` for a different executable. Without `grouped.browser.tests=true`, those tests are skipped explicitly. They run real scripts and responsive interaction against synthetic API fixtures; they are not a claim of a signed-in production browser test.

For a disposable PostgreSQL instance created specifically for tests:

```powershell
mvn '-Dtest=GroupedApprovalRuntimeTest' '-Dgrouped.postgres.url=jdbc:postgresql://127.0.0.1:55439/grouped_workflow_test' test
```

The test schema is created/dropped, so never point this at application data. The local URL/database restriction is intentional. The SQL migration is validated separately on another disposable schema/database, including a repeat application.

Covered scenarios include ALL partial/final completion, ANY_ONE cancellation, reject/Return, correction cycles, stale actors/votes, duplicate decisions, generic endpoint bypass, feature-disabled continuing decisions, handler failure rollback, concurrent member decisions, Finance-vote races, authorization/CSRF, notification retry/suppression, and mobile browser configuration/decision/conflict interaction. Legacy request tests and selected shared workflow tests run alongside them.

## Rollback and acceptance

Disable new grouped submissions first. Keep a compatible binary and the grouped BPMN deployed until active cycles finish or are explicitly resolved through an authorized operational process. Do not deploy an older binary that cannot discover/operate grouped tasks, delete active runtime records or run a destructive schema downgrade.

Before enabling broadly, HR/platform should review policy wording and run a pilot covering both policies, desktop/mobile access, Finance override, Return/resubmission and unavailable employees. Verify actual deployment transaction management, schema, authentication and mail configuration. Automated tests do not replace that deployment-specific acceptance.

## Validation record — 30 October 2026

- Clean Java 21 compilation and selected regression suite: **70 tests passed, zero failures/errors/skips**. This includes three real headless Chrome interaction tests, authentication/CSRF checks, configuration/notification tests, and existing request/approval/promotion/exit coverage.
- After isolating notification delivery from the shared scheduler, the affected notification/scheduler, security and request integration suite was rerun: **25 tests passed, zero failures/errors/skips**. This includes the new slow-mail/non-overlapping-dispatch test.
- Headless Chrome syntax validation: **11 inline/script blocks passed**, including the modified desktop/mobile inbox renderers.
- The additive migration ran successfully twice on a dedicated PostgreSQL 17 migration-test database. Its partial active-cycle uniqueness and member uniqueness constraints rejected deliberately duplicated fixtures; fixture changes were rolled back.
- PostgreSQL 17 runtime suite: **12 tests passed, zero failures/errors/skips**, including request/process ownership guards, member-vote and Finance races, idempotency, Return/resubmission, and shared JPA/Flowable rollback.

No migration or feature enablement was applied to the application's configured database, and the existing locally modified `application.yml` was left untouched. Browser fixtures use synthetic API responses; a signed-in deployment pilot and actual mail delivery still require environment-specific acceptance.
