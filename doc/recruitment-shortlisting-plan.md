# Recruitment AI Shortlisting Implementation Plan

## Objective

Implement intelligent recruitment shortlisting from uploaded candidate documents without modifying Flowable BPMN process definitions.

The feature should allow candidates or HR users to upload application documents, extract readable text, run AI-assisted shortlisting against the job opening criteria, persist the recommendation and evidence, and let HR make a final human review decision before advancing, holding, or rejecting the application through the existing recruitment workflow actions.

## Current Implementation Stage

The implementation has reached the model and persistence scaffolding stage.

Existing work already present in the working tree:

- `CandidateDocument` entity for uploaded candidate document metadata and extracted text.
- `AiShortlistingRun` entity for an AI shortlisting execution and recommendation output.
- `AiShortlistingCriterionScore` entity for per-criterion scoring and evidence.
- `AiShortlistingReviewDecision` entity for HR review and override decisions.
- Supporting DTOs, enums, and Spring Data repositories.
- Company-level flag `recruitmentAiShortlistingEnabled`.
- Embabel agent dependency added to `pom.xml`.

No Flowable BPMN process files are currently modified, which matches the intended constraint.

## Non-Goals

- Do not modify `candidateApplicationProcess.bpmn` or any other Flowable BPMN file.
- Do not make AI output the final recruitment decision.
- Do not auto-reject candidates without HR review.
- Do not expose internal shortlisting scores to candidates on the public careers portal unless explicitly approved later.
- Do not store raw uploaded files in the database.

## Design Principles

- Keep Flowable unchanged. AI shortlisting should be implemented as application-layer services and controller actions around the existing recruitment application lifecycle.
- Treat AI as advisory. HR remains the decision maker.
- Persist evidence and warnings so HR can audit the recommendation.
- Avoid using protected characteristics or inferred sensitive attributes in prompts or scoring.
- Make extraction and AI execution retryable because document parsing and model calls can fail.
- Keep uploaded file storage consistent with existing project patterns.

## Proposed User Flow

1. Candidate opens a published job.
2. Candidate completes the public application form and uploads a resume, with optional supporting documents.
3. The system creates the candidate and job application as it does today.
4. Uploaded documents are stored and linked to the application.
5. The system extracts text from supported documents.
6. If company-level AI shortlisting is enabled, the application queues or triggers a shortlisting run.
7. The AI shortlisting service compares the extracted document text against the job opening requirements, responsibilities, preferred qualifications, location, and work arrangement.
8. HR opens the recruitment application detail page and sees:
   - Uploaded documents
   - Extraction status
   - AI recommendation
   - Overall score
   - Strengths
   - Concerns
   - Missing requirements
   - Fairness warnings
   - Per-criterion evidence
9. HR records a review decision:
   - Advance
   - Hold
   - Reject
   - No decision
10. HR can then use the existing workflow action buttons to advance, hold, or reject the application.

## Phase 1: Document Upload and Storage

### Backend

Add a `CandidateDocumentService` responsible for:

- Validating uploaded files.
- Generating a safe storage path.
- Saving files to configured storage.
- Persisting `CandidateDocument` records.
- Listing documents by application.
- Loading stored files for extraction.

Recommended methods:

- `storeCandidateUpload(JobApplication application, Candidate candidate, MultipartFile file, CandidateDocumentType type)`
- `storeRecruiterUpload(Long applicationId, MultipartFile file, CandidateDocumentType type, Long employeeId)`
- `findByApplication(Long applicationId)`
- `requireDocument(Long documentId)`

Validation rules:

- Reject empty files.
- Restrict file size using the existing multipart settings plus a service-level limit.
- Allow PDF, DOC, DOCX, TXT, and common image formats only if OCR is planned.
- Normalize and sanitize original filenames.
- Store content type and actual file size.

Storage recommendation:

- Use a local application storage directory first, matching existing document upload patterns.
- Store only the path and metadata in the database.
- Make the base path configurable.

### Public Careers Form

Update `careers/job-detail.html`:

- Add `enctype="multipart/form-data"` to the application form.
- Add required resume upload input.
- Add optional cover letter or supporting document upload input if desired.

Update `CareersController.apply(...)`:

- Accept `MultipartFile resume`.
- Accept optional `List<MultipartFile> supportingDocuments`.
- Preserve existing validation behavior.
- After successful application creation, store uploaded documents.

### HR Application Detail

Update `RecruitmentController.application(...)`:

- Add `candidateDocuments` to the model.
- Add `documentTypes` to support HR upload.

Add HR upload endpoint:

- `POST /recruitment/applications/{id}/documents`
- Requires recruitment access.
- Stores uploaded document against the existing application.

## Phase 2: Text Extraction

Add `CandidateDocumentExtractionService`.

Responsibilities:

- Extract text from supported documents.
- Update `extractedTextStatus`.
- Save extracted text into `CandidateDocument.extractedText`.
- Save a user-safe error message into `extractionError` when extraction fails.

Recommended extraction support:

- PDF: Apache PDFBox, already present in `pom.xml`.
- DOCX: Apache POI, already present in `pom.xml`.
- DOC: only if existing POI support is reliable enough; otherwise mark unsupported clearly.
- TXT: read as UTF-8 with fallback handling.

Statuses:

- `PENDING`: document stored but not processed.
- `EXTRACTED`: text was extracted.
- `FAILED`: extraction threw an error.
- `UNREADABLE`: file is technically valid but no usable text was found.

Triggering:

- Run extraction immediately after upload for the first implementation.
- Later, move extraction to async execution if request latency becomes a problem.

Important safeguards:

- Cap extracted text length used for AI input.
- Do not include binary content in prompts.
- Avoid logging extracted candidate document content.

## Phase 3: AI Shortlisting Service

Add `AiShortlistingService`.

Responsibilities:

- Determine whether shortlisting is enabled for the application company.
- Select eligible documents.
- Create an `AiShortlistingRun`.
- Build a job criteria snapshot from `JobOpening`.
- Build the AI prompt/input.
- Call the AI provider or Embabel agent.
- Parse the result into `ShortlistingRecommendationResult`.
- Persist the run and criterion scores.
- Mark failures without blocking normal recruitment flow.

Recommended methods:

- `boolean isEnabledForApplication(Long applicationId)`
- `AiShortlistingRun triggerShortlisting(Long applicationId, Long actorEmployeeId)`
- `AiShortlistingRun retryShortlisting(Long runId, Long actorEmployeeId)`
- `Optional<AiShortlistingRun> latestRun(Long applicationId)`
- `List<AiShortlistingCriterionScore> scoresForRun(Long runId)`

Run status behavior:

- Start as `PENDING`.
- Set `RUNNING` while calling the model.
- Set `COMPLETED` when a recommendation is usable.
- Set `NEEDS_HUMAN_REVIEW` when the model returns insufficient information or fairness warnings requiring explicit attention.
- Set `FAILED` on extraction/model/parsing failures.

Recommendation mapping:

- `RECOMMENDED`: candidate appears to meet core requirements.
- `MAYBE`: candidate partially meets requirements or needs recruiter review.
- `NOT_RECOMMENDED`: candidate appears not to meet core requirements.
- `INSUFFICIENT_INFORMATION`: uploaded documents do not provide enough evidence.

Scoring:

- Use a bounded score such as `0-100` overall.
- Use criterion-level scores with `maxScore`.
- Require evidence snippets or summaries for every positive score.
- Require missing evidence text for required criteria that are not met.

Prompt constraints:

- Evaluate only job-related criteria.
- Ignore protected characteristics and demographic information.
- Do not infer age, gender, ethnicity, religion, marital status, disability, or health status.
- Return structured output.
- Include uncertainty when evidence is weak.
- Prefer `INSUFFICIENT_INFORMATION` over guessing.

## Phase 4: Human Review Decision

Add `AiShortlistingReviewService`.

Responsibilities:

- Validate that the run belongs to the application.
- Persist HR review decision.
- Track whether HR accepted or overrode the AI recommendation.
- Require an override reason when HR disagrees with a strong recommendation.

Recommended endpoint:

- `POST /recruitment/applications/{id}/shortlisting/review`

Request DTO:

- Existing `ShortlistingReviewCommand`.

Validation:

- `runId` is required.
- `decision` must not be null.
- `overrideReason` is required when `aiRecommendationAccepted = false`.
- The review decision should not directly complete a Flowable task.

The existing `/recruitment/applications/{id}/decision` endpoint should remain the place where HR advances, holds, or rejects through the existing workflow task.

## Phase 5: Recruitment UI

Update `recruitment/application-detail.html`.

Add a shortlisting section showing:

- Feature enabled/disabled state.
- Uploaded documents and extraction status.
- Latest AI shortlisting run status.
- Recommendation badge.
- Overall score.
- Summary.
- Strengths.
- Concerns.
- Missing requirements.
- Fairness warnings.
- Criterion score table.
- Retry button for failed runs.
- Run shortlisting button when no run exists.
- Human review form.

Keep UI behavior clear:

- Failed extraction should not block normal recruitment actions.
- Failed AI shortlisting should show a retry action.
- AI recommendation should be visually distinct from HR decision.
- The workflow action panel should continue to behave as it currently does.

## Phase 6: Company Configuration

The company-level flag has been added to the entity and DTOs.

Remaining work:

- Confirm the company create/edit UI exposes `recruitmentAiShortlistingEnabled`.
- Confirm the company API or controller binds the flag correctly.
- Decide whether shortlisting is enabled by default for new companies. Current entity default is `false`, which is safer.

Because `spring.jpa.hibernate.ddl-auto=update` is configured, development databases may auto-create the new columns and tables. For controlled environments, add an explicit migration if this project later adopts Flyway or Liquibase.

## Phase 7: Error Handling and Auditability

Handle these cases explicitly:

- Candidate applies without a required resume.
- File type is unsupported.
- File upload succeeds but extraction fails.
- Extraction succeeds but text is empty.
- AI provider is unavailable.
- AI response cannot be parsed.
- AI run exists but has no criterion scores.
- HR tries to review a run belonging to another application.

Audit fields already available through `BaseEntity` should be used where possible. Additional explicit fields already present on the new entities include:

- `triggeredByEmployeeId`
- `triggeredAt`
- `completedAt`
- `decidedByEmployeeId`
- `decidedAt`

## Phase 8: Tests

Add focused tests before considering the implementation complete.

Service tests:

- Candidate document upload stores metadata and path.
- Unsupported file types are rejected.
- PDF extraction updates status to `EXTRACTED`.
- Empty extraction updates status to `UNREADABLE`.
- Failed extraction updates status to `FAILED`.
- AI shortlisting creates a run and criterion scores.
- AI shortlisting failure marks run as `FAILED`.
- Review decision requires override reason when AI is not accepted.

Controller tests:

- Public application accepts multipart resume upload.
- HR can upload a document to an application.
- HR can trigger shortlisting.
- HR can record a review decision.
- Unauthorized users cannot access shortlisting actions.

Regression tests:

- Existing recruitment application submission still creates the candidate application workflow instance.
- Existing application decision endpoint still completes the active Flowable task.
- No BPMN file changes are required.

## Suggested Implementation Order

1. Implement `CandidateDocumentService`.
2. Wire candidate resume upload into public application submission.
3. Add HR document upload and document listing on application detail.
4. Implement document text extraction.
5. Add extraction trigger after document upload.
6. Implement `AiShortlistingService` with a deterministic mock/fallback parser first.
7. Integrate the real AI/Embabel agent call behind the service interface.
8. Persist shortlisting runs and criterion scores.
9. Add shortlisting trigger/retry endpoints.
10. Add application detail UI for AI results.
11. Add human review service and endpoint.
12. Add tests and run Maven verification.

## Acceptance Criteria

The feature is complete when:

- Candidates can submit an application with a resume upload.
- HR can view uploaded candidate documents on the application detail page.
- The system extracts document text and records extraction status.
- HR can trigger or retry AI shortlisting without changing Flowable BPMN.
- AI recommendations are persisted with scores, evidence, warnings, and raw output.
- HR can record a review decision and override reason.
- Existing workflow decision actions still advance, hold, or reject applications through the current Flowable task.
- The app builds and tests pass.
- No BPMN files are modified.

## Files Expected To Change

Likely backend files:

- `src/main/java/com/justjava/humanresource/recruitment/CareersController.java`
- `src/main/java/com/justjava/humanresource/recruitment/RecruitmentController.java`
- `src/main/java/com/justjava/humanresource/recruitment/service/RecruitmentService.java`
- New `CandidateDocumentService`
- New `CandidateDocumentExtractionService`
- New `AiShortlistingService`
- New `AiShortlistingReviewService`

Likely frontend/template files:

- `src/main/resources/templates/careers/job-detail.html`
- `src/main/resources/templates/recruitment/application-detail.html`

Already-started files:

- `pom.xml`
- `src/main/java/com/justjava/humanresource/orgStructure/entity/Company.java`
- `src/main/java/com/justjava/humanresource/orgStructure/dto/CompanyDTO.java`
- `src/main/java/com/justjava/humanresource/orgStructure/dto/CompanyTreeDTO.java`
- `src/main/java/com/justjava/humanresource/orgStructure/services/impl/OrganogramServiceImpl.java`
- `src/main/java/com/justjava/humanresource/recruitment/entity/CandidateDocument.java`
- `src/main/java/com/justjava/humanresource/recruitment/entity/AiShortlistingRun.java`
- `src/main/java/com/justjava/humanresource/recruitment/entity/AiShortlistingCriterionScore.java`
- `src/main/java/com/justjava/humanresource/recruitment/entity/AiShortlistingReviewDecision.java`
- Supporting recruitment DTOs, enums, and repositories.

## Open Decisions

- Whether resume upload should be mandatory for all public applications.
- Whether supporting documents should be uploaded during initial application or only after submission.
- Whether AI shortlisting should run automatically after application submission or only when HR clicks a button.
- Whether the first implementation should use Embabel directly or a smaller Spring AI service wrapper first.
- Where candidate documents should be stored in production.
- Whether HR review should write an application stage history entry separate from the eventual Flowable decision.
