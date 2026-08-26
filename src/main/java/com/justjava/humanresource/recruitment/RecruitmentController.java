package com.justjava.humanresource.recruitment;

import com.justjava.humanresource.recruitment.enums.*;
import com.justjava.humanresource.recruitment.repository.*;
import com.justjava.humanresource.recruitment.service.RecruitmentService;
import com.justjava.humanresource.recruitment.service.InterviewService;
import com.justjava.humanresource.recruitment.service.OfferService;
import com.justjava.humanresource.recruitment.service.CandidateHireService;
import com.justjava.humanresource.recruitment.dto.HireCandidateCommand;
import com.justjava.humanresource.core.config.AuthenticationManager;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller @RequestMapping("/recruitment") @RequiredArgsConstructor
public class RecruitmentController {
    private final JobOpeningRepository openingRepository;
    private final JobApplicationRepository applicationRepository;
    private final CandidateRepository candidateRepository;
    private final ApplicationStageHistoryRepository historyRepository;
    private final RecruitmentService recruitmentService;
    private final InterviewService interviewService;
    private final OfferService offerService;
    private final CandidateHireService candidateHireService;
    private final InterviewRepository interviewRepository;
    private final InterviewScorecardRepository scorecardRepository;
    private final EmploymentOfferRepository offerRepository;
    private final CandidateEmployeeConversionRepository conversionRepository;
    private final TaskService taskService;
    private final AuthenticationManager authenticationManager;

    @GetMapping
    public String dashboard(Model model) {
        requireRecruitmentAccess();
        model.addAttribute("title", "Recruitment");
        model.addAttribute("subTitle", "Applicant tracking and hiring workflows");
        model.addAttribute("openJobs", openingRepository.countByStatus(JobOpeningStatus.PUBLISHED));
        model.addAttribute("newApplications", applicationRepository.countByStatus(ApplicationStatus.SUBMITTED));
        model.addAttribute("interviewApplications", applicationRepository.countByCurrentStage(RecruitmentStage.INTERVIEW));
        model.addAttribute("acceptedOffers", applicationRepository.countByStatus(ApplicationStatus.OFFER_ACCEPTED));
        model.addAttribute("openings", openingRepository.findAllByOrderByCreatedAtDesc().stream().limit(6).toList());
        model.addAttribute("applications", applicationRepository.findAllByOrderBySubmittedAtDesc().stream().limit(8).toList());
        return "recruitment/dashboard";
    }

    @GetMapping("/job-openings")
    public String openings(Model model) {
        requireRecruitmentAccess();
        model.addAttribute("title", "Job Openings");
        model.addAttribute("subTitle", "Approved requisitions and published roles");
        model.addAttribute("openings", openingRepository.findAllByOrderByCreatedAtDesc());
        return "recruitment/job-openings";
    }

    @GetMapping("/job-openings/{id}")
    public String opening(@PathVariable Long id, Model model) {
        var opening = recruitmentService.requireOpening(id);
        requireOpeningAccess(opening);
        model.addAttribute("title", "Job Opening");
        model.addAttribute("subTitle", "Prepare and manage publication content");
        model.addAttribute("opening", opening);
        model.addAttribute("applications", applicationRepository.findByJobOpeningIdOrderBySubmittedAtDesc(id));
        model.addAttribute("activeTask", activeTask(opening.getWorkflowInstanceId()));
        model.addAttribute("canPrepareOpening", hasRecruitmentAccess());
        model.addAttribute("canReviewOpening", hasRecruitmentAccess() || isCurrentEmployee(opening.getHiringManagerEmployeeId()));
        model.addAttribute("canCloseOpening", hasRecruitmentAccess());
        return "recruitment/job-opening-detail";
    }

    @PostMapping("/job-openings/{id}/prepare")
    public String prepare(@PathVariable Long id, @RequestParam String description,
                          @RequestParam String responsibilities, @RequestParam String requirements,
                          @RequestParam(required = false) String preferredQualifications,
                          @RequestParam(required = false) String location,
                          @RequestParam(required = false) String workArrangement,
                          @RequestParam(required = false) java.time.LocalDate applicationOpenDate,
                          @RequestParam(required = false) java.time.LocalDate applicationCloseDate,
                          @RequestParam(required = false) Long recruiterEmployeeId) {
        requireRecruitmentAccess();
        recruitmentService.prepareOpening(id, description, responsibilities, requirements,
                preferredQualifications, location, workArrangement, applicationOpenDate,
                applicationCloseDate, recruiterEmployeeId);
        var task = activeTask(recruitmentService.requireOpening(id).getWorkflowInstanceId());
        if (task != null && "prepareJobOpening".equals(task.getTaskDefinitionKey())) taskService.complete(task.getId());
        return "redirect:/recruitment/job-openings/" + id;
    }

    @PostMapping("/job-openings/{id}/review")
    public String reviewOpening(@PathVariable Long id, @RequestParam String publicationDecision) {
        var opening = recruitmentService.requireOpening(id);
        requireOpeningReviewAccess(opening);
        var task = activeTask(opening.getWorkflowInstanceId());
        if (task == null || !"reviewJobOpening".equals(task.getTaskDefinitionKey())) throw new IllegalStateException("The job opening is not awaiting review.");
        taskService.complete(task.getId(), java.util.Map.of("publicationDecision", publicationDecision));
        return "redirect:/recruitment/job-openings/" + id;
    }

    @GetMapping("/applications")
    public String applications(Model model) {
        requireRecruitmentAccess();
        model.addAttribute("title", "Applications");
        model.addAttribute("subTitle", "Candidate pipeline and recruitment decisions");
        model.addAttribute("applications", applicationRepository.findAllByOrderBySubmittedAtDesc());
        return "recruitment/applications";
    }

    @GetMapping("/applications/{id}")
    public String application(@PathVariable Long id, Model model) {
        var application = applicationRepository.findById(id).orElseThrow();
        requireApplicationAccess(application);
        model.addAttribute("title", application.getApplicationNumber());
        model.addAttribute("subTitle", "Application workflow and activity");
        model.addAttribute("application", application);
        model.addAttribute("candidate", candidateRepository.findById(application.getCandidateId()).orElseThrow());
        model.addAttribute("opening", openingRepository.findById(application.getJobOpeningId()).orElseThrow());
        model.addAttribute("history", historyRepository.findByApplicationIdOrderByCreatedAt(id));
        model.addAttribute("interviews", interviewRepository.findByApplicationIdOrderByScheduledStartAsc(id));
        model.addAttribute("scorecards", interviewRepository.findByApplicationIdOrderByScheduledStartAsc(id).stream()
                .collect(java.util.stream.Collectors.toMap(
                        interview -> interview.getId(),
                        interview -> scorecardRepository.findByInterviewId(interview.getId())
                )));
        model.addAttribute("offers", offerRepository.findByApplicationIdOrderByOfferVersionDesc(id));
        model.addAttribute("conversion", conversionRepository.findByApplicationId(id).orElse(null));
        model.addAttribute("activeTask", activeTask(application.getWorkflowInstanceId()));
        model.addAttribute("canManageRecruitment", hasRecruitmentAccess());
        model.addAttribute("canApproveOffers", hasRecruitmentAccess() || authenticationManager.isFinancialOfficer());
        model.addAttribute("canScheduleInterview", hasRecruitmentAccess());
        model.addAttribute("canSubmitScorecard", hasRecruitmentAccess());
        model.addAttribute("canCreateOffer", hasRecruitmentAccess());
        model.addAttribute("canSendOffer", hasRecruitmentAccess());
        model.addAttribute("canStartOnboarding", hasRecruitmentAccess());
        return "recruitment/application-detail";
    }

    @PostMapping("/applications/{id}/interviews")
    public String scheduleInterview(@PathVariable Long id, @RequestParam String title,
                                    @RequestParam java.time.LocalDateTime scheduledStart,
                                    @RequestParam java.time.LocalDateTime scheduledEnd,
                                    @RequestParam(required = false) String locationOrMeetingLink,
                                    @RequestParam Long coordinatorEmployeeId,
                                    @RequestParam java.util.List<Long> panelEmployeeIds) {
        requireRecruitmentAccess();
        interviewService.schedule(id, title, scheduledStart, scheduledEnd, locationOrMeetingLink,
                coordinatorEmployeeId, panelEmployeeIds);
        return "redirect:/recruitment/applications/" + id;
    }

    @PostMapping("/applications/{id}/interviews/{interviewId}/scorecards")
    public String submitScorecard(@PathVariable Long id, @PathVariable Long interviewId,
                                  @RequestParam Long reviewerEmployeeId,
                                  @RequestParam int overallScore,
                                  @RequestParam InterviewRecommendation recommendation,
                                  @RequestParam(required = false) String comments) {
        requireRecruitmentAccess();
        interviewService.submitScorecard(interviewId, reviewerEmployeeId, overallScore, recommendation, comments);
        return "redirect:/recruitment/applications/" + id;
    }

    @PostMapping("/applications/{id}/offers")
    public String createOffer(@PathVariable Long id, @RequestParam java.math.BigDecimal annualGrossCompensation,
                              @RequestParam(defaultValue = "NGN") String currency,
                              @RequestParam(required = false) Long jobStepId,
                              @RequestParam(required = false) Long payGroupId,
                              @RequestParam java.time.LocalDate proposedStartDate,
                              @RequestParam java.time.LocalDate expiresOn,
                              @RequestParam(required = false) String terms) {
        requireRecruitmentAccess();
        offerService.createDraft(id, annualGrossCompensation, currency, jobStepId, payGroupId,
                proposedStartDate, expiresOn, terms);
        return "redirect:/recruitment/applications/" + id;
    }

    @PostMapping("/offers/{offerId}/approve")
    public String approveOffer(@PathVariable Long offerId, @RequestParam Long applicationId,
                               @RequestParam Long approverEmployeeId) {
        requireOfferApprovalAccess();
        var offer = offerRepository.findById(offerId).orElseThrow();
        var task = activeTask(offer.getWorkflowInstanceId());
        if (task == null) throw new IllegalStateException("The offer is not awaiting approval.");
        taskService.complete(task.getId(), java.util.Map.of("offerApprovalDecision", "APPROVE", "approverEmployeeId", approverEmployeeId));
        return "redirect:/recruitment/applications/" + applicationId;
    }

    @PostMapping("/offers/{offerId}/reject")
    public String rejectOffer(@PathVariable Long offerId, @RequestParam Long applicationId) {
        requireOfferApprovalAccess();
        var offer = offerRepository.findById(offerId).orElseThrow();
        var task = activeTask(offer.getWorkflowInstanceId());
        if (task == null) throw new IllegalStateException("The offer is not awaiting approval.");
        taskService.complete(task.getId(), java.util.Map.of("offerApprovalDecision", "REJECT"));
        return "redirect:/recruitment/applications/" + applicationId;
    }

    @PostMapping("/offers/{offerId}/send")
    public String sendOffer(@PathVariable Long offerId, @RequestParam Long applicationId) {
        requireRecruitmentAccess(); offerService.send(offerId);
        return "redirect:/recruitment/applications/" + applicationId;
    }

    @PostMapping("/job-openings/{id}/close")
    public String closeOpening(@PathVariable Long id) {
        requireRecruitmentAccess();
        recruitmentService.closeOpening(id);
        return "redirect:/recruitment/job-openings/" + id;
    }

    @PostMapping("/applications/{id}/onboarding")
    public String startOnboarding(@PathVariable Long id, @ModelAttribute HireCandidateCommand command) {
        requireRecruitmentAccess();
        Object email = authenticationManager.get("email");
        candidateHireService.startOnboarding(id, command, email == null ? "RECRUITMENT" : email.toString());
        var application = applicationRepository.findById(id).orElseThrow();
        var task = activeTask(application.getWorkflowInstanceId());
        if (task != null && "preEmploymentTask".equals(task.getTaskDefinitionKey())) {
            taskService.complete(task.getId());
        }
        return "redirect:/recruitment/applications/" + id;
    }

    @PostMapping("/applications/{id}/decision")
    public String applicationDecision(@PathVariable Long id, @RequestParam String recruitmentDecision,
                                      @RequestParam(required = false) String recruitmentComment) {
        requireRecruitmentAccess();
        var application = applicationRepository.findById(id).orElseThrow();
        var task = activeTask(application.getWorkflowInstanceId());
        if (task == null) throw new IllegalStateException("No active recruitment task was found.");
        java.util.Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("recruitmentDecision", recruitmentDecision);
        variables.put("recruitmentComment", recruitmentComment == null ? "" : recruitmentComment);
        variables.put("flowableTaskId", task.getId());
        taskService.complete(task.getId(), variables);
        return "redirect:/recruitment/applications/" + id;
    }

    private org.flowable.task.api.Task activeTask(String processInstanceId) {
        return processInstanceId == null ? null : taskService.createTaskQuery().processInstanceId(processInstanceId).active().singleResult();
    }
    private boolean hasRecruitmentAccess() {
        return authenticationManager.isHumanResource() || authenticationManager.isAdmin()
                || authenticationManager.isJobHR() || authenticationManager.isRestrictedHr();
    }
    private void requireRecruitmentAccess() {
        if (!hasRecruitmentAccess()) throw new IllegalStateException("Recruitment access is required.");
    }
    private void requireOfferApprovalAccess() {
        if (!hasRecruitmentAccess() && !authenticationManager.isFinancialOfficer()) {
            throw new IllegalStateException("Offer approval access is required.");
        }
    }
    private void requireOpeningAccess(com.justjava.humanresource.recruitment.entity.JobOpening opening) {
        if (hasRecruitmentAccess() || isCurrentEmployee(opening.getHiringManagerEmployeeId())) return;
        throw new IllegalStateException("Recruitment access is required.");
    }
    private void requireOpeningReviewAccess(com.justjava.humanresource.recruitment.entity.JobOpening opening) {
        if (hasRecruitmentAccess() || isCurrentEmployee(opening.getHiringManagerEmployeeId())) return;
        throw new IllegalStateException("Job opening review access is required.");
    }
    private void requireApplicationAccess(com.justjava.humanresource.recruitment.entity.JobApplication application) {
        if (hasRecruitmentAccess()) return;
        if (authenticationManager.isFinancialOfficer()
                && offerRepository.findByApplicationIdOrderByOfferVersionDesc(application.getId()).stream()
                .anyMatch(offer -> offer.getStatus() == OfferStatus.IN_APPROVAL)) return;
        throw new IllegalStateException("Recruitment access is required.");
    }
    private boolean isCurrentEmployee(Long employeeId) {
        Long current = currentEmployeeId();
        return employeeId != null && current != null && employeeId.equals(current);
    }
    private Long currentEmployeeId() {
        for (String claim : java.util.List.of("employeeId", "employee_id", "employeeNumber", "employee_number")) {
            Object value = authenticationManager.get(claim);
            if (value instanceof Number number) return number.longValue();
            if (value instanceof String text && !text.isBlank()) {
                try { return Long.valueOf(text); } catch (NumberFormatException ignored) { }
            }
        }
        return null;
    }
}
