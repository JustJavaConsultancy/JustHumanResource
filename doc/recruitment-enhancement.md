# Recruitment AI Enhancement — User Stories

Status: Ready for refinement and milestone implementation  
Date: 16 October 2026  
Audience: Developers, QA, HR product owner, and recruitment administrators

## 1. Purpose

Replace keyword-only shortlisting with evidence-backed AI assessment while improving document readiness, standardizing assessment criteria, and retaining human control of recruitment decisions.

This document defines the enhancement backlog. It builds on [the original shortlisting plan](recruitment-shortlisting-plan.md) and takes precedence for the enhancements described here. Existing recruitment functionality must continue to work.

Every milestone is a usable release, including its screens, backend, persistence, permissions, error handling, and verification. Milestones 1–4 form the first complete assisted-shortlisting release. Milestones 5–7 improve throughput, quality measurement, and administration.

## 2. Users and common rules

| User | Responsibility |
| --- | --- |
| Candidate | Submit an application and supporting documents through the existing careers portal. |
| Recruiter / HR reviewer | Inspect documents, assess applications, review AI findings, and record recruitment decisions within existing permissions. |
| Recruitment administrator | Manage criteria, company/opening settings, evaluation versions, and operational controls within their authorized scope. |
| Platform administrator | Manage deployment-level provider configuration and global operating controls. |

Map these responsibilities to the application's existing roles; this document does not require creating new security roles.

Rules applying to every story:

- Enforce application ownership, company boundaries, and permissions in services and endpoints, including downloads, previews, jobs, exports, and evidence links.
- AI is advisory. It must not advance, hold, reject, hire, send offers, or contact candidates automatically.
- Preserve existing Flowable BPMN definitions and use existing authorized workflow actions.
- Keep internal assessments, prompts, and scores out of candidate-facing pages.
- Treat candidate documents as untrusted evidence, never as instructions for the model.
- Assess only approved job-related criteria. Do not infer or score protected or sensitive personal characteristics.
- Keep API keys out of source code and candidate content out of ordinary application logs.
- Model or extraction failures must not undo a successfully saved application or prevent manual recruitment.
- Preserve existing keyword assessments and review history with their original evaluator identity.

## 3. Milestone overview

| Milestone | Usable outcome | Dependencies |
| --- | --- | --- |
| M1 | HR can inspect document readiness and fix extraction problems. | Existing recruitment application/document flow |
| M2 | HR can configure criteria and complete consistent manual scorecards. | M1 for evidence references |
| M3 | HR can request and inspect a model-backed assessment. | M1–M2 |
| M4 | HR can review, correct, and act on an assessment through the existing workflow. | M3 |
| M5 | Assessments run automatically and recruiters manage a shared work queue. | M3–M4 |
| M6 | Administrators can benchmark evaluator versions and run controlled pilots. | M4; M5 provides additional operational feedback |
| M7 | Administrators can control cost, retention, recovery, and rollout. | M3–M6 |

Implement stories within each milestone as a coherent release. Do not enable unfinished user flows in production.

## 4. M1 — Reliable document readiness and extraction

### REC-101 — Inspect application documents

**Story:** As an HR reviewer, I want to inspect each document and its extracted text so that I know what information is available for assessment.

**Acceptance criteria**

1. The application page lists each document's filename, type, upload date, and extraction status.
2. An authorized reviewer can open the original file and a safely rendered text preview.
3. PDF text retains page references; DOCX text retains paragraph/table references; TXT text has stable segment references.
4. Evidence references resolve to the correct application, document version, and passage.
5. Another company's user cannot access the original, preview, or passage by changing identifiers.

### REC-102 — Recognize incomplete or unreadable documents

**Story:** As an HR reviewer, I want clear document quality warnings so that incomplete input does not look like a complete assessment source.

**Acceptance criteria**

1. Extraction supports readable PDF, DOCX paragraphs and tables, and TXT.
2. The interface distinguishes processing, successful extraction, incomplete extraction, unreadable content, unsupported format, and failure.
3. A scanned PDF without readable text displays a manual-review or readable-replacement instruction.
4. If processing limits omit content, the interface identifies the limitation and does not mark extraction as complete.
5. Extraction records the content hash, extraction version, extracted length, and warnings.
6. Corrupt or oversized processing inputs fail with a safe, actionable message without affecting the saved application.

### REC-103 — Recover document readiness

**Story:** As an HR reviewer, I want to retry extraction or add a replacement document so that I can resolve readiness problems without recreating the application.

**Acceptance criteria**

1. Authorized users can retry a failed extraction and see its updated status.
2. Authorized users can upload a replacement and identify which document it supersedes.
3. Historical evidence references remain linked to the document version originally assessed, subject to retention rules.
4. Repeated retry requests do not create conflicting extraction results.
5. Application submission succeeds when documents are saved but extraction fails; HR sees the resulting issue.

**Milestone demonstration:** Upload a DOCX containing a table, a readable PDF, and a scanned PDF; inspect extracted content, identify the unreadable file, and resolve it with a replacement.

## 5. M2 — Configurable criteria and manual scorecards

### REC-201 — Configure opening criteria

**Story:** As a recruitment administrator, I want to define an opening's assessment criteria so that every candidate is assessed against the same requirements.

**Acceptance criteria**

1. Authorized users can create and edit draft criteria with a description, required/preferred classification, positive weight, rubric, display order, and optional acceptable equivalents.
2. Each criterion has a stable identifier within its criteria version.
3. Blank descriptions, invalid weights, and incomplete rubrics prevent activation with field-level messages.
4. The criteria preview shows the complete rubric and how weights contribute to the overall score.
5. Criteria are explicitly managed; the system does not silently discard requirements after a fixed count.

### REC-202 — Activate and version criteria

**Story:** As a recruitment administrator, I want to activate a reviewed criteria version so that assessments remain consistent and reproducible.

**Acceptance criteria**

1. An opening has at most one active criteria version for new assessments.
2. Activation records the actor and timestamp.
3. An active version cannot be edited in place; changes create a new draft version.
4. Existing assessments retain their original criteria snapshot and version.
5. Without active criteria, manual and AI assessment actions explain what setup is required.

### REC-203 — Complete a manual scorecard

**Story:** As an HR reviewer, I want to assess a candidate manually against the active criteria so that I can shortlist consistently without relying on AI.

**Acceptance criteria**

1. The reviewer can save a draft and complete a scorecard with an assessment, comment, and evidence or information gap for each criterion.
2. Assessment values distinguish supported, partially supported, not evidenced, contradicted, and unable to assess.
3. The server calculates weighted scores using a versioned rubric; the interface shows evidence coverage separately.
4. Missing evidence is labelled as missing information, not proof that the candidate lacks the qualification.
5. The configured policy defines when incomplete information suppresses an overall score or requires manual review.
6. Completed scorecards retain reviewer identity, date, criteria version, and document references; later corrections preserve history.
7. Manual scorecards remain usable when AI is disabled or unavailable.

**Milestone demonstration:** Activate criteria, manually assess two applications, then activate revised criteria and verify the earlier scorecards remain unchanged.

## 6. M3 — Evidence-backed AI assessment on demand

### REC-301 — Request and track an assessment

**Story:** As a recruiter, I want to request an AI assessment and return later so that assessment processing does not block my work.

**Acceptance criteria**

1. The action is available only to authorized users when company AI is enabled, criteria are active, and input readiness permits assessment.
2. The request creates a durable job and shows queued, running, completed, failed, or needs-human-review status.
3. Refreshing or leaving the page does not cancel processing.
4. Duplicate requests for the same application, input fingerprint, criteria, and evaluator version do not create duplicate active work.
5. Queued work survives restart; interrupted work is recovered or safely marked for retry.
6. Transient failures use bounded retries; permanent failures show an actionable message and permitted retry action.
7. Provider calls execute outside long-running database transactions and respect configured timeouts and concurrency limits.

### REC-302 — Read evidence-backed findings

**Story:** As an HR reviewer, I want an assessment for every criterion with traceable evidence so that I can verify the AI's reasoning.

**Acceptance criteria**

1. Every approved criterion appears exactly once, with an allowed assessment value and concise rationale.
2. Supported claims cite an application document, stable segment reference, and quotation.
3. Each evidence link opens the cited document passage for an authorized reviewer.
4. Missing, conflicting, or ambiguous information is explicit and may include a relevant clarification question.
5. Candidate-reported experience is not presented as independently verified employment or qualifications.
6. The result identifies incomplete input coverage and requests human review when coverage is inadequate.
7. The interface identifies the model-backed evaluator and distinguishes it from historical keyword assessments.

### REC-303 — Publish only validated results

**Story:** As an HR reviewer, I want invalid AI output withheld so that unsupported or malformed findings do not appear trustworthy.

**Acceptance criteria**

1. Server validation rejects unknown or duplicate criteria, missing criteria, invalid assessment values, oversized fields, and invalid document references.
2. Cited quotations are checked against the exact supplied document segments using a documented normalization rule.
3. Supported findings without valid evidence cannot be published as a completed assessment.
4. Java scoring rules calculate totals from validated assessments; model-generated totals cannot override them.
5. Evidence coverage is separate from score; an unassessable result does not display a misleading zero or probability of suitability.
6. Invalid output is recorded as a classified failure or needs-human-review outcome, with no recruitment status change.
7. Instructions embedded in a CV cannot change criteria, obtain other applications' data, invoke tools, or perform recruitment actions.

### REC-304 — Trace assessment provenance

**Story:** As an authorized administrator, I want assessment provenance and usage records so that results can be investigated and compared.

**Acceptance criteria**

1. Each run records company/application IDs, initiating actor or automatic trigger, timestamps, input fingerprint, document/extraction versions, and criteria snapshot.
2. Each run records evaluator, provider/model identifier, prompt/schema/scoring versions, attempt count, and classified errors.
3. Provider request reference and token usage are recorded when available; estimated cost is identified as an estimate and its pricing basis is versioned.
4. Input snapshots and retained output are restricted and follow configured retention rules; secrets are never stored with a run.
5. Ordinary logs contain operational identifiers rather than CV content, full prompts, or raw provider payloads.
6. Provenance supports investigation; the interface does not promise identical model output on a rerun.

**Milestone demonstration:** Request an assessment, leave the page, inspect evidence on return, and demonstrate safe provider failure, invalid-output handling, and restart recovery.

## 7. M4 — Human review and decision workflow

### REC-401 — Review and correct findings

**Story:** As an HR reviewer, I want to agree with or correct individual findings so that my final assessment reflects the evidence.

**Acceptance criteria**

1. A reviewer can compare each AI finding with its evidence and record agreement, correction, or an information request.
2. Corrections capture the reviewer, timestamp, replacement assessment, and reason without overwriting original AI output.
3. The reviewed score is recalculated separately using the same versioned scoring rules.
4. Structured feedback distinguishes incorrect evidence, missed evidence, incorrect interpretation, and rubric concerns, with optional comments.
5. A running or failed assessment cannot be submitted as a completed AI review; manual assessment remains available.

### REC-402 — Record and act on a human decision

**Story:** As an authorized HR reviewer, I want to record my decision and use existing workflow actions so that I retain control of candidate progression.

**Acceptance criteria**

1. HR can record advance, hold, reject, or no-decision outcomes with comments, subject to existing workflow permissions.
2. A decision references the exact AI run or manual scorecard reviewed and records actor and timestamp.
3. Disagreement with the recommendation requires an override reason.
4. Recording an assessment review does not implicitly transition the candidate; progression uses an explicit existing workflow action.
5. The interface distinguishes a recorded review decision from the application's actual workflow status.
6. No model output, background job, or provider callback can execute a workflow transition.

### REC-403 — Manage stale results and history

**Story:** As an HR reviewer, I want to know when an assessment is outdated so that I do not mistake it for an assessment of current information.

**Acceptance criteria**

1. Adding/replacing relevant documents or activating different criteria marks affected assessments stale.
2. Reviewers can inspect run history, input versions, reviews, and superseding results.
3. A rerun creates a new run and preserves earlier assessments and decisions.
4. Before recording a decision against stale findings, HR must rerun, complete a current manual assessment, or explicitly acknowledge staleness with a reason.
5. Historical reviews remain attached to their original runs rather than automatically appearing accepted for a new run.

**Milestone demonstration:** Correct an AI finding, record an override, progress through an existing authorized action, then replace a CV and verify stale warnings and preserved history.

## 8. M5 — Automatic assessment and recruiter work queue

### REC-501 — Enable automatic assessment per opening

**Story:** As a recruitment administrator, I want to enable automatic assessment for selected openings so that new applications are prepared for review without repetitive requests.

**Acceptance criteria**

1. Opening-level automatic assessment requires company AI enablement and active criteria.
2. Initial assessment is queued after the application and initial document set are committed and extraction reaches a usable or terminal outcome.
3. Queue/provider failure does not prevent the candidate from receiving confirmation of a successfully saved application.
4. Disabling company AI prevents both new manual model requests and automatic requests; opening controls cannot override it.
5. Updated documents mark results stale; automatic reassessment occurs only when explicitly configured and within limits.
6. Automatic work uses the same validation, idempotency, permissions, and audit rules as manual requests.

### REC-502 — Find applications needing attention

**Story:** As a recruiter, I want a work queue so that I can prioritize applications requiring review or recovery.

**Acceptance criteria**

1. The queue shows application/opening references, processing status, review status, evidence readiness, and stale warnings.
2. Filters include opening, ready-for-review, document issues, failures, stale results, and reviewed applications.
3. Each row opens the correct application and appropriate next action.
4. Queue totals and pagination respect the same company and access filters as row contents.
5. If score sorting is offered, incomplete/unscored results are visibly distinguished and are not silently treated as zero.

### REC-503 — Run controlled bulk actions

**Story:** As a recruiter, I want to assess or retry selected applications together so that I can manage larger recruitment campaigns efficiently.

**Acceptance criteria**

1. Authorized users can select applications and request assessment or retry eligible failures.
2. The system reports queued, skipped, duplicate, ineligible, and limit-exceeded items individually.
3. Bulk actions respect company quotas, concurrency limits, and job idempotency.
4. Progress remains visible after leaving and returning to the queue.
5. Bulk actions never perform recruitment decisions or transitions.

**Milestone demonstration:** Enable one opening, submit applications, inspect the queue, and run a mixed bulk request containing eligible, duplicate, and failed items.

## 9. M6 — Quality evaluation and controlled pilot

### REC-601 — Maintain a reviewed benchmark

**Story:** As an authorized recruitment administrator, I want a reviewed benchmark so that evaluator quality can be measured against agreed expectations.

**Acceptance criteria**

1. Authorized users can maintain restricted synthetic or appropriately authorized examples with expected criterion assessments and evidence.
2. Cases cover equivalent wording, keyword stuffing, ambiguous dates, missing/conflicting evidence, extraction limitations, and embedded instructions.
3. Cases record rubric version, reviewer identity, and resolved disagreements.
4. Benchmark versions are immutable once used for a published evaluation report.
5. Evaluation fixtures are prepared from M2 onward; this milestone makes them repeatable through an administrator workflow.

### REC-602 — Compare evaluator versions

**Story:** As a recruitment administrator, I want to compare a proposed evaluator with the current version and keyword baseline so that release decisions are evidence-based.

**Acceptance criteria**

1. An authorized administrator can start a benchmark run and inspect a versioned report.
2. Reports include criterion-level accuracy measures, unsupported positive claims, citation validity/relevance, information-gap handling, repeated-run consistency, latency, failures, and usage.
3. Results identify benchmark, criteria, model, prompt, schema, and scoring versions and allow inspection of disagreements.
4. Groundedness evaluation checks whether quotations support claims, not merely whether quotations exist.
5. Approved numerical thresholds are recorded before promotion; a version cannot be labelled approved without satisfying them.
6. Evaluation runs cannot change live application decisions or expose benchmark records to unauthorized users.

### REC-603 — Operate a controlled pilot

**Story:** As a recruitment administrator, I want to restrict a new evaluator to selected openings so that its usefulness can be validated before broader rollout.

**Acceptance criteria**

1. A proposed version can run in shadow mode without displaying recommendations in the normal HR decision flow.
2. An approved pilot version can be enabled for selected openings only.
3. Pilot reviewers can flag incorrect evidence or assessment directly from a result.
4. Reports summarize review time where measured, override reasons, failures, and usage without treating reviewer agreement alone as proof of correctness.
5. The version can be disabled without disabling manual recruitment or removing historical results.
6. Known critical evidence-fabrication defects and observed unauthorized access or automatic transitions block release.

**Milestone demonstration:** Run a benchmark, inspect a disagreement, apply release thresholds, and enable/disable a pilot for one opening.

## 10. M7 — Production administration and operating controls

### REC-701 — Control and inspect usage

**Story:** As an administrator, I want company-level usage controls and reports so that assessment costs and demand remain manageable.

**Acceptance criteria**

1. Authorized administrators can set assessment quotas, estimated spending limits, and warning thresholds for an explicit time window.
2. Reports attribute assessments, token usage, and estimated cost to company, opening, evaluator version, and period where available.
3. Capacity is reserved atomically before dispatch so concurrent requests cannot bypass configured quotas.
4. Failed/retried work and unavailable provider usage are represented explicitly; estimates are not presented as provider invoices.
5. Limit-reached messages explain the next action and preserve manual assessment access.

### REC-702 — Monitor, pause, and recover processing

**Story:** As an authorized administrator, I want to monitor and recover assessment jobs so that failures can be resolved without developer intervention.

**Acceptance criteria**

1. A dashboard shows queued/running jobs, age, attempts, failures, and abandoned-job indicators within the user's scope.
2. Permitted recovery actions safely retry or cancel eligible jobs without duplicate result publication.
3. Global and company pause controls stop new model dispatch; queued work remains visible and resumes only when permitted.
4. In-flight work follows a documented completion/cancellation policy and never changes recruitment status.
5. Manual recruitment, prior assessments, and review history remain available during a pause.
6. Administrators can select an approved evaluator version for future work and roll back without rewriting historical runs; failures never silently fall back to keyword results under a model-backed label.

### REC-703 — Apply retention and export audit history

**Story:** As an authorized administrator, I want controlled retention and audit export so that recruitment data can be managed throughout its lifecycle.

**Acceptance criteria**

1. Retention policies identify original files, extracted text/segments, assessment snapshots, provider payloads retained by the application, results, and review records separately.
2. Authorized users can preview policy impact and execute permitted deletion with an audit record.
3. Deletion does not leave accessible copies in application-managed snapshots or invalidate the display of remaining records without explanation.
4. Evidence links to deleted content show an explicit unavailable-by-retention message.
5. Authorized exports contain scoped run provenance and review history; exports exclude secrets and respect data permissions.
6. Application deletion is not represented as deletion from a provider; any provider-side retention/deletion capability is documented and handled separately.

**Milestone demonstration:** Reach a test quota, pause dispatch, recover an abandoned job, roll back an evaluator version, and verify retention impact and a scoped audit export.

## 11. Implementation guidance

The following is guidance for developers; equivalent designs are acceptable if the user stories and acceptance criteria are met.

| Existing area | Expected work |
| --- | --- |
| `CandidateDocumentExtractionService`, `CandidateDocumentService` | Improve extraction, add traceable segments/versioning, readiness and recovery. |
| `JobOpening` and recruitment configuration | Add approved, versioned criteria and per-opening automation settings. |
| `AiShortlistingService` | Separate orchestration, model evaluation, validation, and server-side scoring; retain the keyword evaluator as a clearly labelled baseline. |
| `AiShortlistingRun`, criterion scores and repositories | Extend provenance, structured evidence, durable execution, and historical relationships. |
| `AiShortlistingReviewService` | Add review eligibility, corrections, stale handling, and feedback without overwriting original results. |
| `CareersController`, recruitment controllers | Queue work after commit, expose authorized actions, preserve public submission behavior. |
| `templates/recruitment/application-detail.html` and new screens | Deliver document inspection, criteria, scorecards, AI review, queues, and administration. |
| `pom.xml` and environment configuration | Verify existing Spring AI/Embabel compatibility; configure a bounded provider adapter without adding unnecessary agent orchestration. |

Use reviewed, additive migrations and preserve legacy records. Do not rely solely on Hibernate `ddl-auto: update` for production changes. Remote model calls must run outside long database transactions. Durable dispatch must avoid the gap where an application is committed but no recoverable job is recorded; a database job table or transactional outbox can address this.

A normal CV should be assessed with its relevant content within configured limits. Larger inputs require explicit coverage tracking; adding a vector database is not a prerequisite.

## 12. Decisions to resolve before the affected milestone

These decisions do not block unrelated milestones. Record agreed values in configuration or the approved rubric rather than leaving behavior implicit.

| Decision | Needed by | Suggested owner |
| --- | --- | --- |
| Assessment-to-points mapping, essential-criterion handling, minimum coverage, and score suppression | M2 | HR product owner |
| Provider/model, permitted data handling, credentials, and input/output limits | M3 | Technical lead and product owner |
| Retry count, timeouts, concurrency, and initial usage limits | M3 | Technical lead |
| Benchmark approval thresholds and pilot openings | M6 | HR product owner and QA |
| Quota periods, budget behavior, retention periods, and export permissions | M7 | Product owner and authorized administrators |

## 13. Definition of done for every milestone

- All milestone stories work through the intended user interface in the agreed environment.
- Acceptance criteria have recorded verification results, including relevant permission and failure scenarios.
- Meaningful automated tests cover business rules and integrations; model tests use controlled fixtures/stubs, with live evaluation kept separate.
- Database migrations and compatibility with existing recruitment records are verified.
- Existing application submission and recruitment workflow checks pass; BPMN definitions remain unchanged.
- Feature settings, operational instructions, and user guidance are updated.
- A complete milestone demonstration is accepted by the product owner; incomplete features stay disabled.

## 14. Deferred scope

- OCR for scanned documents and legacy DOC extraction beyond the current readable-replacement/manual-review flow.
- AI-generated criteria, autonomous agents, candidate messaging, automatic hiring/rejection, and candidate-facing assessment scores.
- Training models automatically from HR feedback. Feedback supports reviewed evaluation; it is not automatically trusted training data.
- AI expansion into payroll, appraisal, or other modules.
