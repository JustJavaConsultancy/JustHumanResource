# Recruitment Agent Implementation Plan

Review date: 29 October 2026. Status: proposed implementation, grounded in a fresh static review of the repository. This document does not implement application changes.

## 1. Objective and architecture decision

Extend JustHR recruitment with an agent that gathers job-related evidence, identifies information gaps, prepares explainable screening recommendations, and drafts recruiter-approved next actions. People remain responsible for publication, screening decisions, rejection, offers and hiring.

Build inside the existing Java/Spring application. Keep Flowable as the durable authority for recruitment stages and human tasks. Use Embabel for bounded reasoning actions behind an application-owned interface, and Spring AI for model integration. Keep durable execution state in PostgreSQL rather than assuming agent runtime memory survives restarts.

The MVP should deliver secure document ingestion, approved job criteria, evidence-backed application analysis and a human review workbench. Add interview preparation and approved communication drafts after the screening pilot. Defer autonomous calendar operations, offer assistance and talent-pool search until the foundations are proven.

## 2. Review scope and current implementation

Reviewed Maven dependencies; recruitment controllers, services, entities and repositories; document processing; shortlisting and reviews; candidate BPMN and its decision delegate; OAuth security configuration; recruitment template inventory; and test inventory. Findings are static observations. This documentation pass did not run the application or tests, inspect production data, resolve dependencies or verify provider connectivity.

Java paths below are relative to `src/main/java/com/justjava/humanresource/`. Resource paths are relative to `src/main/resources/`.

| Area | Observed implementation | Implication |
| --- | --- | --- |
| Runtime | `pom.xml` declares Java 21, Boot 4.0.8, Spring AI 2.0.1, Embabel 1.5.2, Flowable 7.1.0 and PostgreSQL/JPA | Dependencies are present; compatibility still needs a build spike. |
| Recruitment lifecycle | `recruitment/service/RecruitmentService.java`, `processes/candidateApplicationProcess.bpmn` | Reuse screening, assessment, interview, checks, offer and pre-employment tasks. |
| Candidate intake | `recruitment/CareersController.java` | Submission stores documents and conditionally invokes shortlisting. |
| Document pipeline | `CandidateDocumentService`, `CandidateDocumentExtractionService` | PDF/DOCX/TXT extraction exists; extend its security, quality and provenance. |
| Screening history | `AiShortlistingRun`, `AiShortlistingCriterionScore`, `AiShortlistingReviewDecision` | Preserve existing history while adding execution metadata and citations. |
| Human review | `AiShortlistingReviewService.record(...)` | Already binds run to application and requires a reason for overriding recommendations. |
| Domain actions | `InterviewService`, `OfferService`, `CandidateHireService`, `RecruitmentEmailService` | Future approved actions should reuse hardened domain services. |
| UI | `templates/recruitment/application-detail.html` and related pages | Extend the current recruiter experience instead of creating a separate chat-only workflow. |
| Tests | Six recruitment test classes covering services, BPMN and UI alignment | No dedicated shortlisting/document-extraction tests appeared in the test inventory. |

### Review findings

Priorities: P0 = prerequisite before exposing agent actions; P1 = required for dependable screening MVP; P2 = later enhancement. These findings do not establish a complete application-wide security audit.

| ID | Priority | Finding and evidence | Required implementation |
| --- | --- | --- | --- |
| R1 | P0 | `AiShortlistingService.evaluate()` scores normalized substring matches. No Embabel agent implementation was found in the Java source search. | Describe existing results as a local heuristic. Introduce a separate agent evaluator; retain deterministic scoring in Java. |
| R2 | P0 | `triggerShortlisting()` does not enforce the company feature flag. Public intake checks it, but manual run/retry paths do not. | Enforce global/company/capability policy in the application service and again before worker execution. |
| R3 | P0 | Retry authorizes path application `id`, then passes only `runId`; the service resolves another application from that run. | Require application/run binding and company/actor checks inside the retry service. |
| R4 | P0 | Reviewed controller access helpers grant broad recruitment-role access without resource-specific company/assignment checks. Queries include unscoped lists/counts and `findById`. `BaseEntity` contains no company scope. | Add explicit resource authorization and scoped queries for lists, counts, details, documents, dropdowns, mutations and tools. Verify complete ownership relationships. |
| R5 | P0 | `Oauth2SecurityConfig` permits `/api/**` at the filter level and globally disables CSRF. The task controller performs role checks itself, but that is not a suitable default for new APIs. | Add authenticated recruitment matchers before the broad rule, service authorization and CSRF protection for cookie-authenticated mutations. Test narrowly justified exemptions. |
| R6 | P0 | `RecruitmentTaskController.complete()` copies an arbitrary request map into Flowable variables; the decision delegate reads application identity from workflow variables. | Use task-specific typed commands and allowlists. Derive application/company/actor from trusted state; prohibit identifier and approval-variable overrides. |
| R7 | P0 | Candidate tracking can display status and respond to offers using application number plus email. These identifiers do not prove control of the candidate account. | Require verified short-lived access for sensitive status and offer responses, rate-limit lookup and avoid redirect query strings containing personal identifiers. Extend the existing hashed-token foundation. |
| R8 | P1 | Extraction and screening run synchronously. Screening holds a transaction around evaluation and catches all exceptions inside it. | Use durable background jobs, short transactions and independently persisted failures; caught database exceptions may leave a transaction unusable. |
| R9 | P1 | Uploads trust declared MIME type. Extraction silently truncates at 80,000 characters; DOCX reads paragraphs, not tables; image-only PDFs have no OCR path; legacy DOC is stored but unsupported for extraction. | Add content inspection, scanning/quarantine, bounded parsing, table support, explicit quality/truncation state and optional OCR. Route unreadable inputs to review. |
| R10 | P1 | Screening concatenates all extracted application documents, stores document IDs and a criteria snapshot, and lacks exact evidence spans/content hashes. | Restrict eligible document types; use immutable manifests, approved rubric versions, provenance and validated citations. Exclude unrelated identity/banking/check documents. |
| R11 | P1 | Review recording checks run/application ownership but not terminal usability, input freshness or stage eligibility. | Reject stale/failed/running recommendations as decision inputs; validate actor and review semantics. Keep historical reviews visibly historical. |
| R12 | P1 | Interview scheduling directly changes application stage. Offer services also update state independently of candidate task completion. | Centralize allowed transitions and coordinate domain changes with Flowable; prevent stage/task disagreement and skipped prerequisites. |
| R13 | P1 | Scorecard reviewer and offer approver identities are request parameters. Panel membership alone does not prove the current actor is that reviewer. | Derive actor from trusted authentication; separately authorize delegation. Bind nested interview/offer IDs to the expected application. |
| R14 | P1 | Notifications use `AfterCommitExecutor` callbacks. Reviewed call sites do not demonstrate durable recovery after process failure. | Inspect the helper/delivery implementation; add a transactional outbox and idempotent dispatch wherever durability is absent. |
| R15 | P2 | Application creation, document writes and screening are separate intake steps without a durable readiness manifest. | Add a recoverable intake completion barrier; never analyze an accidentally partial submission as complete. |

## 3. Capability and autonomy boundaries

| Capability | Agent output | Human control | Release |
| --- | --- | --- | --- |
| Job preparation | Draft criteria/rubric from approved requisition; flag vague criteria | HR/hiring manager approves rubric and publication | MVP |
| Candidate evidence | Job-related facts, citations, missing/conflicting evidence | Recruiter corrects facts with attribution | MVP |
| Screening | Criterion assessments, advisory recommendation and clarification questions | Recruiter records final screening decision | MVP |
| Interview preparation | Consistent job-related questions and evidence gaps | Recruiter approves pack; panel writes its own scorecards | Increment 2 |
| Communication | Clarification/invitation/decision drafts from approved facts | Authorized recruiter approves exact content and recipient | Increment 2 |
| Scheduling | Proposed slots using authorized availability access | Coordinator approves participants, timezone and slot | Increment 3 |
| Offer/onboarding support | Completeness checks and summaries of approved terms | Existing HR/finance approvals and accepted-offer prerequisites | Increment 3 |
| Talent-pool search | Scoped evidence retrieval | Separate access, retention and evaluation design | Deferred |

Do not implement autonomous rejection, hiring, compensation approval, external sourcing, reference contact, personality inference, protected-trait inference or face/voice analysis. A recommendation never completes a human task automatically.

## 4. Target components and boundaries

```text
Recruitment UI / candidate intake
  -> authorized application services
  -> database: manifests, runs, proposals, jobs and outbox
  -> durable worker
       -> readiness / policy / version checks
       -> recruitment agent facade
            -> bounded Embabel actions
            -> Spring AI model adapter
            -> scoped evidence tools
       -> schema / evidence / policy validation
       -> deterministic scoring and result persistence
  -> recruiter review workbench
  -> approved command executor
       -> transition coordinator -> domain services + Flowable
       -> notification outbox -> delivery adapter
```

Proposed packages under `recruitment/`:

- `authorization/`: `RecruitmentAuthorizationService`, trusted company/actor context and resource permissions.
- `agent/`: `RecruitmentAgentFacade`, screening agent, typed action inputs/outputs and execution policy.
- `agent/tool/`: narrowly scoped evidence/policy tools returning DTOs rather than entities or paths.
- `agent/validation/`: schema, rubric coverage, citation and content-policy validation.
- `execution/`: run service, durable jobs, leases, cancellation, retries and recovery.
- `criteria/`: immutable approved rubrics and server-side scoring.
- `evidence/`: manifests, extraction versions, chunks, redaction and provenance.
- `approval/`: action proposals, exact-payload approvals and idempotent command execution.
- `notification/`: durable outbox and delivery reconciliation.

Adapt `AiShortlistingService` as a compatibility facade or migrate callers incrementally. Preserve historical local results with their original algorithm metadata. Never silently substitute keyword results under an LLM model label when the provider fails.

Use one bounded agent with typed actions initially. Logical evidence/review roles do not require separate agents or infrastructure. Keep model APIs behind an adapter so library/provider changes do not leak into recruitment rules.

## 5. Screening execution contract

1. Authorize the initiating actor and resolve application, opening and company from trusted records. Automatic intake uses a constrained system identity with an originating event ID.
2. Require enabled policy, eligible application status, completed intake and an approved rubric. Persist an immutable input manifest and deduplicated queued run in a short transaction.
3. Worker claims a lease and rechecks policy, actor permission and source versions. Revoked access or disabled capability stops processing without changing recruitment state.
4. Load only eligible document versions and approved criteria. Minimize personal information before model access; preserve a restricted mapping for recruiter citations.
5. Agent analyzes the supplied evidence and retrieves more passages through scoped tools when needed. It may propose clarification but cannot contact the candidate.
6. Return typed criterion assessments: `SUPPORTED`, `PARTIAL`, `NOT_EVIDENCED`, `CONTRADICTED` or `UNKNOWN`. Each requires citations or an explicit missing-evidence reason. Missing evidence is not proof of inability.
7. Validate schema, criterion identity/coverage, evidence spans and policy. Recompute numeric scores in Java from approved rubric anchors. Model self-reported confidence is not a calibrated probability.
8. Persist result and diagnostics atomically in a new transaction. Recheck freshness: changed documents, rubric or relevant application state make the output superseded.
9. Human review records the exact run/version and final decision. A separately authorized workflow command performs any stage transition.

### Tools

| Proposed tool | Scope and output | Side effects |
| --- | --- | --- |
| `readApprovedJobCriteria` | Run-bound opening/rubric; stable criterion IDs, weights and evidence rules | None |
| `findApplicationEvidence` | Application fixed by run context; bounded chunks with document version/hash, page/section and offsets | None |
| `readPermittedRecruitmentPolicy` | Company-bound approved policy passages/version | None |
| `readInterviewEvidence` | Later release; permitted interviews and attributed human scorecards | None |
| `proposeClarification` | Validated gaps become a draft proposal | Internal draft only |
| `proposeInterviewPack` | Approved rubric and gaps become a question/scorecard draft | Internal draft only |

Enforce authorization in Java on every call. The model cannot choose a different company, actor or application. Do not expose generic SQL, repositories, filesystem/network tools, `completeTask`, `moveApplication`, `sendEmail` or `approveOffer`.

Version system instructions, prompt, output schema, rubric and agent implementation. Treat document/retrieved text as untrusted data even when it contains instructions. Require cited facts and explicit uncertainty; forbid invented dates, credentials or panel opinions. Store concise action summaries and tool metadata, not hidden reasoning traces.

Configure hard limits for calls, tokens, elapsed time and spend. Starting bounds for evaluation: six model calls, ten evidence calls, 90 seconds total, two transient execution retries and one output-repair attempt. Validate these defaults under load. Authorization failures and unsupported documents are not retryable provider errors; exhausted/invalid output routes to human review.

## 6. Data model and migration strategy

Use additive, versioned migrations. No migration framework was visible in the reviewed POM/resource inventory; select Flyway or Liquibase, baseline existing schema and test against a disposable restored database. Do not use automatic schema updates as the production migration plan.

| Record | Required additions |
| --- | --- |
| Approved rubric/version | Company/opening, stable criterion IDs, weights, required flags, scoring anchors, approval identity/time and source version; immutable after approval |
| Document processing version | Existing document link, company/application, content hash, scan result, parser/OCR version, quality, truncation, language, failure code, retention/deletion state |
| Evidence chunk | Processing version, page/section, offsets, text hash and minimized text; indexed by company/application |
| Agent execution | Existing shortlisting run link where applicable, company, use case, parent retry, request/input hashes, status, policy/rubric/prompt/schema/agent/model versions, initiator, lease/heartbeat, attempts, timings, tokens/cost |
| Input manifest | Immutable run/document-version links and opening/application versions; retain old comma-separated IDs for historical compatibility |
| Criterion evidence | Run/criterion, assessment state, server-calculated score, source chunk/span, excerpt and validation result; multiple citations supported |
| Action proposal | Company/application/run, action type, canonical payload hash, expected resource version, expiry, approval/execution state and idempotency key |
| Human approval/review | Authenticated actor, exact run/proposal and payload hash, decision/reason, expected version and timestamp; append-only audit |
| Job/outbox | Aggregate/event identity, deduplication key, payload reference, attempts, next retry, lease/delivery/error state |

Use existing `BaseEntity.version` for optimistic concurrency. Add database constraints for request-key uniqueness, one active execution per application/use case/company, unique command execution, review submission deduplication and required ownership relationships. A forced fresh analysis of unchanged inputs requires a new request key and explicit rationale.

Suggested execution states: `QUEUED`, `WAITING_FOR_DOCUMENTS`, `RUNNING`, `COMPLETED`, `NEEDS_HUMAN_REVIEW`, `FAILED`, `CANCELLED`, `SUPERSEDED`. Define a compatibility mapping to existing `AiShortlistingRunStatus`; preserve old semantics and unknown historical provenance. Never fabricate historical document hashes or model metadata.

## 7. Durability and workflow consistency

Start with a PostgreSQL-backed durable job worker. AMQP is declared, but an operating recruitment broker pipeline was not established by review. Add RabbitMQ transport only if deployment ownership is confirmed; database run state remains authoritative.

Use atomic job claims, expiring leases, heartbeats and recovery scheduling. Duplicate delivery must be harmless through unique keys and compare-and-set state changes. Commit job/run creation before model work. Never hold JPA transactions across model, OCR, scan, calendar or email calls. Persist failures separately. Cancellation must prevent late results from becoming current or triggering proposals/actions.

Introduce a proposed `RecruitmentDecisionService` to validate application stage/status, exact active task key, process identity, allowed decision and expected version; record trusted actor identity; and coordinate Flowable completion with domain updates. Reuse it from UI decisions and candidate offer responses. Harden interview/offer services so scheduling or offer creation cannot skip prerequisites or leave an unrelated task active.

Review offer-response sequencing carefully: existing offer response changes application stage before candidate task completion. Also decide HOLD semantics before implementing follow-up automation: current candidate BPMN sends HOLD to an end event, so resumability is not established.

Write notification events transactionally with business changes. Dispatch with an execution ledger and provider idempotency where available. Delivery is generally at-least-once; ambiguous timeouts need reconciliation instead of blind resend. Define ownership between existing automatic emails and agent-approved drafts so one event does not produce duplicate candidate messages.

## 8. Security, privacy and recruitment safeguards

Define separate capabilities for company HR, assigned recruiter, assigned hiring manager, panel member and offer approver. Finance access to an offer should not automatically expose all CVs or model inputs. Apply resource scope in services, including list/count queries and downloads; use minimal DTOs for dropdowns.

Scan before parsing. Inspect file signatures; bound decompression, page count, extraction size and processing time; constrain storage reads to managed roots; keep originals outside web roots. Record partial/truncated extraction and OCR limitations. Abstract storage for encrypted object storage, retention and deletion. A failed scan or parser never becomes a negative candidate recommendation.

Exclude names/contact information where feasible, photos, birth dates, marital status, national/banking identifiers and unrelated sensitive facts from scoring inputs. Do not infer protected characteristics or personality. Location/work-eligibility criteria require approved job justification. Redaction can fail, so evaluate residual leakage and enforce human review as well.

Before pilot, HR/privacy owners must establish deployment-specific candidate notice, processing basis, correction/appeal route, retention and cross-border/provider requirements. This document does not establish legal compliance. Do not repurpose candidate data for training without a separately approved basis.

Keep CV text, prompts and sensitive tool arguments out of ordinary logs/traces. Encrypt restricted artifacts and apply deletion across originals, chunks, caches and stored outputs, retaining only the minimum permitted audit record. Avoid a vector store in the MVP; application-local retrieval is sufficient. Embeddings would require a separate isolation and deletion design.

## 9. API and UI implementation

Keep current routes as compatibility entry points while moving policy into services. Proposed authenticated operations:

| Operation | Contract |
| --- | --- |
| `POST /recruitment/applications/{id}/agent-runs` | Scoped request with idempotency key/use case; return run reference promptly |
| `GET .../agent-runs/{runId}` | Verify run/application binding; return sanitized status/results |
| `POST .../agent-runs/{runId}/retry` | Validate ownership/retryability; create linked run against current inputs |
| `POST .../agent-runs/{runId}/cancel` | Authorized cancellation; suppress late publication |
| `POST .../agent-runs/{runId}/reviews` | Current usable result, expected version and trusted actor required |
| `POST /recruitment/proposals/{proposalId}/approve` | Bind approval to exact payload hash, target version and expiry |
| `POST /recruitment/proposals/{proposalId}/execute` | Idempotent command, with approval/policy/freshness revalidation |

Protect Thymeleaf forms with CSRF; configure explicit authentication for JSON APIs. Use 403 for denied capability, scoped 404 for unavailable resources, 409 for stale versions and bounded validation errors. Run IDs never serve as candidate access credentials.

Extend `application-detail.html` with queued/running status, analysis type/model/version, input/rubric freshness, extraction warnings, criterion evidence, gaps, corrections and review history. Keep unknown distinct from zero suitability. Link to exact available document pages/sections. Use bounded HTMX polling and stop at terminal state. Preserve manual screening during outages.

Add rubric approval to `job-opening-detail.html`, scoped paginated batch status to `applications.html`, and operational counts to `dashboard.html`. Budget batch runs and expose expected scope; never add bulk automatic rejection.

## 10. Delivery phases

Estimates assume two engineers plus part-time HR, QA and platform support. Ranges are planning assumptions, dependent on infrastructure/schema condition and provider approval.

| Phase | Effort | Deliverables | Exit gate |
| --- | --- | --- | --- |
| 0: Baseline/compatibility | 3–5 engineering days | Effective dependency tree, minimal Embabel/Spring AI smoke fixture, baseline tests, migration/schema assessment, architecture decisions and synthetic fixtures | Pinned framework/model integration demonstrated; blockers recorded |
| 1: Secure foundations | 7–10 days | Resource authorization/scoped repositories; retry/feature fixes; typed workflow commands; trusted identity; candidate verification; security configuration; transition coordinator | Cross-company/nested-ID attacks denied; no caller identity spoofing or stage bypass |
| 2: Reliable inputs/execution | 7–10 days | Additive migrations, secure ingestion/extraction, manifests/chunks, readiness barrier, durable jobs/leases/outbox/cancellation | Submission independent of model latency; restart/duplicate delivery safe; unreadable files visible |
| 3: Rubrics/agent | 8–12 days | Approved rubric versions, facade/actions/tools, prompt/schema, citations and deterministic scoring | Complete rubric coverage, valid evidence, uncertainty and bounded execution |
| 4: Workbench/evaluation | 6–9 days | UI/review audit, corrections, stale-result guards, offline corpus and metrics | Recruiter can verify/override; stale output cannot authorize transitions; quality gates pass |
| 5: Controlled pilot | 5–10 days plus observation | Flags, shadow mode, small cohort, runbook, cost/load measurement and kill-switch drill | HR/platform acceptance; no open P0 finding |
| 6: Interview/communications | 8–12 days | Interview packs, exact-content message approvals, dispatch reconciliation; later calendar adapter | Authorized recipient/content, no duplicate notification ownership or fabricated scorecards |

MVP includes phases 0–5. Critical path: compatibility/security -> safe inputs and approved criteria -> analysis -> review/evaluation -> pilot. UI scaffolding and synthetic fixture preparation can proceed alongside foundations.

### Actionable backlog

1. `REC-01`: establish build/dependency and workflow baseline; attach actual results.
2. `REC-02`: implement authorization matrix and company/assignment-scoped queries.
3. `REC-03`: fix retry ownership and enforce feature flags inside services/workers.
4. `REC-04`: typed task commands, trusted actor, transition coordinator and verified candidate response.
5. `REC-05`: migration baseline and additive run/provenance/approval schema.
6. `REC-06`: secure storage, scanning, extraction versions/chunks and readiness barrier.
7. `REC-07`: durable worker, leases/recovery, cancellation and outbox.
8. `REC-08`: immutable approved rubric and input snapshot services.
9. `REC-09`: bounded agent, scoped tools, model adapter and versioned prompts.
10. `REC-10`: schema/citation validation and deterministic scoring.
11. `REC-11`: review UI, corrections, audit and freshness guards.
12. `REC-12`: offline evaluation, operational metrics, shadow pilot and rollback drill.
13. `REC-13`: approved interview/communication proposals after screening acceptance.

Each ticket must specify permissions, migration implications, failure behavior, tests and an observable acceptance demonstration.

## 11. Verification and evaluation

### Engineering checks

- Unit tests: rubric calculation, missing/contradictory evidence, output limits, manifest hashes, freshness and permission decisions.
- Security integration: anonymous access; foreign-company IDs; mismatched application/run/document/interview/offer; forged reviewer/approver; workflow variable injection; feature-off run/retry; revoked queued-job permission; CSRF.
- PostgreSQL integration: baseline/upgrade migrations, uniqueness races, optimistic locking, lease recovery, duplicate delivery and concurrent review/publication. Add Testcontainers or an equivalent disposable PostgreSQL fixture; H2 alone does not validate production database behavior.
- Workflow: recommendation alone advances nothing; approved decision advances once; stale task/version denied; scheduling/offer creation cannot skip stages; candidate acceptance and onboarding preserve history/task consistency; HOLD semantics explicit.
- Ingestion: scanned/corrupt/password-protected PDFs, DOCX tables, encoding, unsupported DOC, MIME spoof, decompression expansion, oversized/truncated inputs and unavailable scanner/OCR.
- Agent contracts: invalid schema/criterion IDs, fabricated or misaligned citations, prompt injection, loops, rate limits/timeouts, token exhaustion and cancellation. Fake model in CI; separate opt-in live smoke tests with synthetic data.
- Browser flow: upload -> queue -> cited analysis -> review -> authorized workflow decision; denied access and manual fallback. Extend existing service/BPMN/UI tests.

First establish the existing recruitment baseline with:

```powershell
.\mvnw.cmd -Dtest=RecruitmentServiceTest,InterviewServiceTest,OfferServiceTest,RecruitmentProcessDefinitionTest,RecruitmentControllerUiAlignmentTest,RecruitmentTemplateAlignmentTest test
```

Then run focused new tests and the required broader suite. This is a proposed validation command, not a claim that tests passed in this review.

### Recruitment quality

Create de-identified or synthetic HR-adjudicated fixtures covering job families, clear/partial matches, nonmatches, career changes, gaps, alternative qualification wording and multilingual documents. Reserve a holdout set. Compare the keyword baseline and agent against the same approved rubric.

Measure citation validity, criterion coverage, unsupported factual assertions, correct unknown handling, agreement with adjudicated evidence, override reasons and time to a verified decision. Historical hiring decisions are not automatically unbiased ground truth; independently adjudicate disagreements.

Proposed pilot targets, to approve during phase 0:

- Every criterion has valid evidence or an explicit missing-evidence reason.
- At least 98% citation validity on holdout data; zero critical invented qualification claims in release evaluation.
- Zero unauthorized actions or cross-company disclosure in the security suite.
- At least 20% reduction in median review time without material quality regression.

These are targets, not measured results. Use paired counterfactual fixtures changing names/irrelevant demographic cues while preserving job evidence. Investigate score changes and proxies. Where lawful and statistically meaningful, evaluate group error differences in a separate restricted process; never feed those sensitive attributes into production scoring. Report sample sizes and uncertainty.

## 12. Operations, rollout and rollback

Provide global/company/use-case flags with distinct local, shadow and active-advisory modes. Start with synthetic staging, then approved shadow data and a small recruiter cohort. Shadow outputs cannot authorize actions or send communications.

Track queue age, duration, extraction/validation failure, unknown/stale rate, review completion, override reasons, provider errors, retries, token/currency cost and notification delivery. Avoid personal data and candidate IDs in metric labels. Low override rate alone does not demonstrate quality.

Initial service targets: submission does not wait for models; 95% of ready-input pilot runs finish within two minutes at agreed load; exhausted jobs are visible; approved external actions are auditable and reconcilable. Validate applications/day, peak concurrency and document sizes before setting production worker counts.

Estimate spend from measured input/output tokens, provider rates, retries, OCR, storage and infrastructure. Enforce per-run caps and company daily budgets; show batch estimates before enqueue. Do not hardcode unverified pricing or assume configured models meet recruitment quality needs.

Kill switch stops new provider work, cancels queued execution and blocks proposal execution while preserving manual recruitment. Suppress late cancelled/superseded results. Use additive schema compatibility for deployment rollback, retain audits, and avoid destructive downgrade migrations. Document recovery for provider outages, stuck leases, worker crashes, quarantined inputs and ambiguous message delivery.

## 13. Foundation decisions and ownership

- Product/HR: pilot companies/roles, rubric approvers, final-decision permissions, correction/appeal route and quality acceptance.
- Engineering/platform: exact resolved framework compatibility, provider/model/region, worker capacity, storage/scanning/OCR ownership and incident runbook.
- Privacy/security: candidate notice, processing basis, access verification, retention and provider/cross-border conditions by deployment.
- Workflow owner: whether HOLD remains terminal or becomes a separately designed resumable state.
- Integration owner: assess existing calendar/meeting adapters before scheduling automation. Declared Graph/Meet dependencies do not establish availability lookup or booking support.

These decisions do not prevent beginning the ownership, feature enforcement, authorization and execution foundations.

## 14. Definition of done

The MVP is ready when security prerequisites pass; document work and analysis survive restart; approved immutable inputs underpin every recommendation; recruiters can inspect evidence and uncertainty; stale results cannot authorize changes; duplicate execution cannot duplicate business effects; and manual recruitment remains available during provider failure. A human must remain accountable for recruitment outcomes.

## 15. External technical references

References support design concepts, not compatibility claims for the declared dependencies. Confirm version-specific APIs during phase 0.

- [Spring AI tool calling](https://docs.spring.io/spring-ai/reference/1.0/api/tools.html): application-executed tools require authorization at the implementation boundary. This version is conceptual background, not a 2.0.1 API prescription.
- [Spring AI structured output converters](https://docs.spring.io/spring-ai/reference/2.1/api/structured-output/converters.html): structured conversion still requires validation. Verify equivalent APIs for the resolved version.

Repository source supports current-state findings. Proposed classes, schema, estimates and targets above are recommendations.
