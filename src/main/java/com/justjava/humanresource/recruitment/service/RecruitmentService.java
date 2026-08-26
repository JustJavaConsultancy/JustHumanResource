package com.justjava.humanresource.recruitment.service;

import com.justjava.humanresource.recruitment.dto.ApplicationSubmissionResult;
import com.justjava.humanresource.recruitment.dto.PublicApplicationCommand;
import com.justjava.humanresource.recruitment.entity.*;
import com.justjava.humanresource.recruitment.enums.*;
import com.justjava.humanresource.recruitment.repository.*;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.enums.RequestStatus;
import com.justjava.humanresource.request.enums.RequestType;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.request.repository.StaffRequisitionDetailRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecruitmentService {
    private static final String CONSENT_VERSION = "2026-01";
    private final JobOpeningRepository openingRepository;
    private final CandidateRepository candidateRepository;
    private final JobApplicationRepository applicationRepository;
    private final ApplicationStageHistoryRepository historyRepository;
    private final StaffRequisitionDetailRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final RecruitmentNumberService numberService;
    private final RuntimeService runtimeService;

    @Transactional
    public JobOpening createOpeningFromApprovedRequisition(WorkflowRequest request) {
        if (request.getRequestType() != RequestType.STAFF_REQUISITION || request.getStatus() != RequestStatus.APPROVED) {
            throw new IllegalStateException("Only an approved staff requisition can create a job opening.");
        }
        return openingRepository.findByStaffRequisitionRequestId(request.getId()).orElseGet(() -> {
            var detail = staffRepository.findByWorkflowRequestId(request.getId())
                    .orElseThrow(() -> new IllegalStateException("Approved staff requisition details were not found."));
            Long companyId = departmentRepository.findById(detail.getDepartmentId())
                    .map(department -> department.getCompany() != null ? department.getCompany().getId() : null)
                    .orElse(1L);
            JobOpening opening = new JobOpening();
            opening.setStaffRequisitionRequestId(request.getId());
            opening.setCompanyId(companyId);
            opening.setDepartmentId(detail.getDepartmentId());
            opening.setJobGradeId(detail.getJobGradeId());
            opening.setJobTitle(detail.getJobTitle());
            opening.setNumberOfPositions(detail.getNumberOfPositions());
            opening.setEmploymentType(detail.getEmploymentType());
            opening.setRequisitionReason(detail.getRequisitionReason());
            opening.setReplacementEmployeeId(detail.getReplacementEmployeeId());
            opening.setTargetStartDate(detail.getTargetStartDate());
            opening.setEstimatedMonthlyCost(detail.getEstimatedMonthlyCost());
            opening.setReasonForHire(detail.getReasonForHire());
            opening.setHiringManagerEmployeeId(request.getRequesterEmployeeId());
            opening.setPublicSlug(slug(detail.getJobTitle(), request.getId()));
            opening.setStatus(JobOpeningStatus.DRAFT);
            JobOpening saved = openingRepository.saveAndFlush(opening);
            var process = runtimeService.startProcessInstanceByKey(
                    "recruitmentJobOpeningProcess",
                    "JOB_OPENING_" + saved.getId(),
                    Map.of(
                            "jobOpeningId", saved.getId(),
                            "staffRequisitionRequestId", request.getId(),
                            "companyId", saved.getCompanyId(),
                            "hiringManagerEmployeeId", String.valueOf(saved.getHiringManagerEmployeeId())
                    ));
            saved.setWorkflowInstanceId(process.getProcessInstanceId());
            return openingRepository.save(saved);
        });
    }

    @Transactional
    public JobOpening prepareOpening(Long openingId, String description, String responsibilities,
                                     String requirements, String preferredQualifications, String location,
                                     String workArrangement, java.time.LocalDate openDate,
                                     java.time.LocalDate closeDate, Long recruiterId) {
        JobOpening opening = requireOpening(openingId);
        if (opening.getStatus() != JobOpeningStatus.DRAFT && opening.getStatus() != JobOpeningStatus.CONTENT_REVIEW) {
            throw new IllegalStateException("Only draft or returned openings can be prepared.");
        }
        opening.setDescription(required(description, "Job description"));
        opening.setResponsibilities(required(responsibilities, "Responsibilities"));
        opening.setRequirements(required(requirements, "Requirements"));
        opening.setPreferredQualifications(preferredQualifications);
        opening.setLocation(location);
        opening.setWorkArrangement(workArrangement);
        opening.setApplicationOpenDate(openDate);
        opening.setApplicationCloseDate(closeDate);
        opening.setAssignedRecruiterEmployeeId(recruiterId);
        opening.setStatus(JobOpeningStatus.CONTENT_REVIEW);
        return openingRepository.save(opening);
    }

    @Transactional
    public JobOpening publish(Long openingId) {
        JobOpening opening = requireOpening(openingId);
        validatePublication(opening);
        opening.setStatus(JobOpeningStatus.PUBLISHED);
        if (opening.getApplicationOpenDate() == null) opening.setApplicationOpenDate(java.time.LocalDate.now());
        return openingRepository.save(opening);
    }

    @Transactional
    public JobOpening closeOpening(Long openingId) {
        JobOpening opening = requireOpening(openingId);
        if (opening.getStatus() != JobOpeningStatus.PUBLISHED && opening.getStatus() != JobOpeningStatus.PAUSED) {
            throw new IllegalStateException("Only published or paused openings can be closed.");
        }
        opening.setStatus(JobOpeningStatus.CLOSED);
        JobOpening saved = openingRepository.save(opening);
        notifyOpeningClose(saved);
        return saved;
    }

    @Transactional
    public void recordHire(Long applicationId) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        JobOpening opening = requireOpening(application.getJobOpeningId());
        int filled = opening.getPositionsFilled() == null ? 0 : opening.getPositionsFilled();
        int capacity = opening.getNumberOfPositions() == null ? 0 : opening.getNumberOfPositions();
        if (filled < capacity) {
            opening.setPositionsFilled(filled + 1);
        }
        if (capacity > 0 && opening.getPositionsFilled() >= capacity) {
            opening.setStatus(JobOpeningStatus.FILLED);
            openingRepository.save(opening);
            notifyOpeningClose(opening);
        } else {
            openingRepository.save(opening);
        }
    }

    @Transactional
    public ApplicationSubmissionResult apply(String slug, PublicApplicationCommand command) {
        JobOpening opening = openingRepository.findPubliclyAvailableBySlug(slug, JobOpeningStatus.PUBLISHED, LocalDate.now())
                .orElseThrow(() -> new IllegalArgumentException("This job opening is not available."));
        String email = command.getEmail().trim().toLowerCase();
        Candidate candidate = candidateRepository.findFirstByNormalizedEmailOrderByCreatedAtDesc(email)
                .orElseGet(() -> {
                    Candidate created = new Candidate();
                    created.setCandidateNumber(numberService.candidateNumber());
                    created.setFirstName(command.getFirstName().trim());
                    created.setMiddleName(command.getMiddleName());
                    created.setLastName(command.getLastName().trim());
                    created.setEmail(email);
                    created.setNormalizedEmail(email);
                    created.setPhone(command.getPhone());
                    created.setAddress(command.getAddress());
                    created.setLinkedInUrl(command.getLinkedInUrl());
                    created.setPortfolioUrl(command.getPortfolioUrl());
                    created.setSource(command.getSource());
                    created.setConsentVersion(CONSENT_VERSION);
                    created.setConsentedAt(LocalDateTime.now());
                    created.setRetentionDeadline(LocalDateTime.now().plusYears(2));
                    return candidateRepository.save(created);
                });
        if (applicationRepository.existsByCandidateIdAndJobOpeningId(candidate.getId(), opening.getId())) {
            throw new IllegalStateException("An application already exists for this candidate and job opening.");
        }
        String rawToken = UUID.randomUUID().toString() + UUID.randomUUID();
        JobApplication application = new JobApplication();
        application.setApplicationNumber(numberService.applicationNumber());
        application.setCandidateId(candidate.getId());
        application.setJobOpeningId(opening.getId());
        application.setCompanyId(opening.getCompanyId());
        application.setRecruiterEmployeeId(opening.getAssignedRecruiterEmployeeId());
        application.setApplicationSource(command.getSource());
        application.setCurrentStage(RecruitmentStage.SCREENING);
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setSubmittedAt(LocalDateTime.now());
        application.setAccessTokenHash(hash(rawToken));
        application.setAccessTokenExpiresAt(LocalDateTime.now().plusDays(90));
        JobApplication saved = applicationRepository.saveAndFlush(application);
        recordTransition(saved, RecruitmentStage.APPLICATION, ApplicationStatus.DRAFT, "Application submitted", null, null);
        var process = runtimeService.startProcessInstanceByKey(
                "candidateApplicationProcess", "JOB_APPLICATION_" + saved.getId(),
                Map.of("applicationId", saved.getId(), "jobOpeningId", opening.getId(), "companyId", opening.getCompanyId()));
        saved.setWorkflowInstanceId(process.getProcessInstanceId());
        applicationRepository.save(saved);
        return new ApplicationSubmissionResult(saved.getId(), saved.getApplicationNumber(), rawToken);
    }

    @Transactional(readOnly = true)
    public JobApplication requireApplicationByAccessToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Application access token is required.");
        }
        JobApplication application = applicationRepository.findByAccessTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Application access token is invalid."));
        if (application.getAccessTokenExpiresAt() != null
                && application.getAccessTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Application access token has expired.");
        }
        return application;
    }

    @Transactional
    public JobApplication moveApplication(Long applicationId, RecruitmentStage stage, ApplicationStatus status,
                                          String reason, String flowableTaskId, Long actorEmployeeId) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        RecruitmentStage previousStage = application.getCurrentStage();
        ApplicationStatus previousStatus = application.getStatus();
        application.setCurrentStage(stage);
        application.setStatus(status);
        if (status == ApplicationStatus.REJECTED) application.setRejectionReason(reason);
        JobApplication saved = applicationRepository.save(application);
        recordTransition(saved, previousStage, previousStatus, reason, flowableTaskId, actorEmployeeId);
        return saved;
    }

    @Transactional(readOnly = true) public JobOpening requireOpening(Long id) {
        return openingRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Job opening not found."));
    }

    @Transactional(readOnly = true)
    public JobOpening requirePubliclyAvailableOpening(String slug) {
        return openingRepository.findPubliclyAvailableBySlug(slug, JobOpeningStatus.PUBLISHED, LocalDate.now())
                .orElseThrow(() -> new IllegalArgumentException("This job opening is not available."));
    }

    private void recordTransition(JobApplication application, RecruitmentStage previousStage,
                                  ApplicationStatus previousStatus, String reason, String taskId, Long actorId) {
        ApplicationStageHistory history = new ApplicationStageHistory();
        history.setApplicationId(application.getId());
        history.setPreviousStage(previousStage);
        history.setNewStage(application.getCurrentStage());
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(application.getStatus());
        history.setReason(reason);
        history.setFlowableTaskId(taskId);
        history.setActorEmployeeId(actorId);
        historyRepository.save(history);
    }

    private void validatePublication(JobOpening opening) {
        required(opening.getDescription(), "Job description");
        required(opening.getResponsibilities(), "Responsibilities");
        required(opening.getRequirements(), "Requirements");
        if (opening.getApplicationCloseDate() != null && opening.getApplicationOpenDate() != null
                && opening.getApplicationCloseDate().isBefore(opening.getApplicationOpenDate())) {
            throw new IllegalStateException("Application closing date cannot precede the opening date.");
        }
    }
    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalStateException(field + " is required.");
        return value.trim();
    }
    private String slug(String title, Long requestId) {
        String normalized = title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return normalized + "-" + requestId;
    }
    private void notifyOpeningClose(JobOpening opening) {
        if (opening.getWorkflowInstanceId() == null || opening.getWorkflowInstanceId().isBlank()) {
            return;
        }
        var execution = runtimeService.createExecutionQuery()
                .processInstanceId(opening.getWorkflowInstanceId())
                .messageEventSubscriptionName("JOB_OPENING_CLOSE")
                .singleResult();
        if (execution != null) {
            runtimeService.messageEventReceived("JOB_OPENING_CLOSE", execution.getId());
        }
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("Unable to secure candidate access token.", ex); }
    }
}
