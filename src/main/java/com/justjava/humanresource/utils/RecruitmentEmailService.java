package com.justjava.humanresource.utils;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.recruitment.entity.Candidate;
import com.justjava.humanresource.recruitment.entity.EmploymentOffer;
import com.justjava.humanresource.recruitment.entity.Interview;
import com.justjava.humanresource.recruitment.entity.JobApplication;
import com.justjava.humanresource.recruitment.entity.JobOpening;
import com.justjava.humanresource.recruitment.enums.ApplicationStatus;
import com.justjava.humanresource.recruitment.repository.CandidateRepository;
import com.justjava.humanresource.recruitment.repository.EmploymentOfferRepository;
import com.justjava.humanresource.recruitment.repository.InterviewPanelMemberRepository;
import com.justjava.humanresource.recruitment.repository.InterviewRepository;
import com.justjava.humanresource.recruitment.repository.JobApplicationRepository;
import com.justjava.humanresource.recruitment.repository.JobOpeningRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitmentEmailService {
    @Value("${app.tracking.base-url}")
    private String trackingBaseUrl;
    private static final String DEFAULT_COMPANY_NAME = "Human Resources";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM uuuu, HH:mm");

    private final ResendService resendService;
    private final CandidateRepository candidateRepository;
    private final JobApplicationRepository applicationRepository;
    private final JobOpeningRepository openingRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewPanelMemberRepository panelRepository;
    private final EmploymentOfferRepository offerRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyApplicationSubmitted(Long applicationId, String rawToken) {
        trace("START", "application submitted notice applicationId=" + applicationId + " hasAccessToken=" + (rawToken != null && !rawToken.isBlank()));
        ApplicationContext context = context(applicationId, "application submitted notice");
        if (context == null) return;
        String statusPath = rawToken == null || rawToken.isBlank() ? null : "/careers/applications/" + rawToken;
        trace("BUILD", "application submitted notice applicationId=" + applicationId + " hasStatusPath=" + (statusPath != null));
        String text = "Dear " + context.candidateName() + ",\n\n"
                + "We have received your application for " + context.jobTitle() + ". Your application number is "
                + context.application().getApplicationNumber() + "."
                + (statusPath == null ? "" : "\n\nYou can check your application status here: " + statusPath)
                + "\n\nRegards,\n" + context.companyName();
        String html = "<p>Dear " + html(context.candidateName()) + ",</p>"
                + "<p>We have received your application for <strong>" + html(context.jobTitle())
                + "</strong>. Your application number is <strong>" + html(context.application().getApplicationNumber()) + "</strong>.</p>"
                + (statusPath == null ? "" : "<p>You can check your application status here: " + html(statusPath) + "</p>")
                + "<p>Regards,<br><strong>" + html(context.companyName()) + "</strong></p>";
        sendCandidate(context.candidate(), "Application received", html, text, "application submitted notice");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyApplicationDecision(Long applicationId, String reason) {
        trace("START", "application decision notice applicationId=" + applicationId + " reason=" + reason);
        ApplicationContext context = context(applicationId, "application decision notice");
        if (context == null) return;
        ApplicationStatus status = context.application().getStatus();
        String subject = switch (status) {
            case REJECTED -> "Application update";
            case ON_HOLD -> "Application on hold";
            case OFFER_ACCEPTED -> "Offer accepted";
            case OFFER_DECLINED -> "Offer declined";
            case HIRED -> "Onboarding started";
            default -> "Application status update";
        };
        String statusText = status.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        trace("BUILD", "application decision notice applicationId=" + applicationId + " status=" + status + " subject=" + subject);
        String text = "Dear " + context.candidateName() + ",\n\n"
                + "Your application for " + context.jobTitle() + " is now " + statusText + "."
                + (reason == null || reason.isBlank() ? "" : "\n\nNote: " + reason.trim())
                + "\n\nRegards,\n" + context.companyName();
        String html = "<p>Dear " + html(context.candidateName()) + ",</p>"
                + "<p>Your application for <strong>" + html(context.jobTitle()) + "</strong> is now <strong>"
                + html(statusText) + "</strong>.</p>"
                + (reason == null || reason.isBlank() ? "" : "<p>Note: " + html(reason.trim()) + "</p>")
                + "<p>Regards,<br><strong>" + html(context.companyName()) + "</strong></p>";
        sendCandidate(context.candidate(), subject, html, text, "application decision notice");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyInterviewScheduled(Long interviewId) {
        trace("START", "interview scheduled notice interviewId=" + interviewId);
        InterviewContext context = interviewContext(interviewId, "interview scheduled notice");
        if (context == null) return;
        String schedule = schedule(context.interview());
        String location = location(context.interview());
        trace("BUILD", "interview scheduled notice interviewId=" + interviewId
                + " applicationId=" + context.interview().getApplicationId()
                + " schedule=" + schedule
                + " location=" + location);
        String candidateText = "Dear " + context.applicationContext().candidateName() + ",\n\n"
                + "Your interview for " + context.applicationContext().jobTitle() + " has been scheduled.\n\n"
                + "Interview: " + context.interview().getTitle() + "\n"
                + "Time: " + schedule + "\n"
                + "Location/link: " + location + "\n\n"
                + "Regards,\n" + context.applicationContext().companyName();
        String candidateHtml = "<p>Dear " + html(context.applicationContext().candidateName()) + ",</p>"
                + "<p>Your interview for <strong>" + html(context.applicationContext().jobTitle()) + "</strong> has been scheduled.</p>"
                + "<p><strong>Interview:</strong> " + html(context.interview().getTitle()) + "<br>"
                + "<strong>Time:</strong> " + html(schedule) + "<br>"
                + "<strong>Location/link:</strong> " + html(location) + "</p>"
                + "<p>Regards,<br><strong>" + html(context.applicationContext().companyName()) + "</strong></p>";
        sendCandidate(context.applicationContext().candidate(), "Interview scheduled", candidateHtml, candidateText,
                "candidate interview scheduled notice");

        String panelSubject = "Interview panel assignment";
        String panelText = "You have been assigned to an interview panel.\n\n"
                + "Candidate: " + context.applicationContext().candidateName() + "\n"
                + "Role: " + context.applicationContext().jobTitle() + "\n"
                + "Interview: " + context.interview().getTitle() + "\n"
                + "Time: " + schedule + "\n"
                + "Location/link: " + location;
        String panelHtml = "<p>You have been assigned to an interview panel.</p>"
                + "<p><strong>Candidate:</strong> " + html(context.applicationContext().candidateName()) + "<br>"
                + "<strong>Role:</strong> " + html(context.applicationContext().jobTitle()) + "<br>"
                + "<strong>Interview:</strong> " + html(context.interview().getTitle()) + "<br>"
                + "<strong>Time:</strong> " + html(schedule) + "<br>"
                + "<strong>Location/link:</strong> " + html(location) + "</p>";
        trace("RECIPIENTS", "interview scheduled internal recipients interviewId=" + interviewId);
        for (Employee employee : interviewRecipients(context.interview())) {
            sendEmployee(employee, panelSubject, panelHtml, panelText, "interview panel assignment");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyInterviewCompleted(Long interviewId) {
        trace("START", "interview completed notice interviewId=" + interviewId);
        InterviewContext context = interviewContext(interviewId, "interview completed notice");
        if (context == null) return;
        trace("BUILD", "interview completed notice interviewId=" + interviewId
                + " applicationId=" + context.interview().getApplicationId());
        String text = "Dear " + context.applicationContext().candidateName() + ",\n\n"
                + "Your interview for " + context.applicationContext().jobTitle()
                + " has been completed. The recruitment team will contact you with the next update.\n\n"
                + "Regards,\n" + context.applicationContext().companyName();
        String html = "<p>Dear " + html(context.applicationContext().candidateName()) + ",</p>"
                + "<p>Your interview for <strong>" + html(context.applicationContext().jobTitle())
                + "</strong> has been completed. The recruitment team will contact you with the next update.</p>"
                + "<p>Regards,<br><strong>" + html(context.applicationContext().companyName()) + "</strong></p>";
        sendCandidate(context.applicationContext().candidate(), "Interview completed", html, text,
                "candidate interview completed notice");

        String internalText = "All scorecards are in for " + context.applicationContext().candidateName()
                + " (" + context.applicationContext().jobTitle() + "). Please review the interview result.";
        String internalHtml = "<p>All scorecards are in for <strong>" + html(context.applicationContext().candidateName())
                + "</strong> (" + html(context.applicationContext().jobTitle())
                + "). Please review the interview result.</p>";
        notifyRecruitmentOwners(context.applicationContext(), "Interview result ready", internalHtml, internalText,
                "interview result ready notice");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyOfferSent(Long offerId) {
        trace("START", "offer sent notice offerId=" + offerId);
        OfferContext context = offerContext(offerId, "offer sent notice");
        if (context == null) return;
        EmploymentOffer offer = context.offer();
        trace("BUILD", "offer sent notice offerId=" + offerId
                + " applicationId=" + offer.getApplicationId()
                + " status=" + offer.getStatus()
                + " compensation=" + offer.getCurrency() + " " + offer.getAnnualGrossCompensation());

        // Build tracking URL using application number and email
        String applicationNumber = context.applicationContext().application().getApplicationNumber();
        String candidateEmail = context.applicationContext().candidate().getEmail();
        String encodedEmail = java.net.URLEncoder.encode(candidateEmail, java.nio.charset.StandardCharsets.UTF_8);
        String trackingUrl = trackingBaseUrl + "/careers/track?applicationNumber=" + applicationNumber + "&email=" + encodedEmail;

        String text = "Dear " + context.applicationContext().candidateName() + ",\n\n"
                + "An employment offer for " + context.applicationContext().jobTitle() + " has been sent to you.\n\n"
                + "Compensation: " + offer.getCurrency() + " " + offer.getAnnualGrossCompensation() + "\n"
                + "Proposed start date: " + value(offer.getProposedStartDate()) + "\n"
                + "Expires on: " + value(offer.getExpiresOn()) + "\n\n"
                + value(offer.getTerms()) + "\n\n"
                + "You can view your application and respond to the offer here: " + trackingUrl + "\n\n"
                + "Regards,\n" + context.applicationContext().companyName();

        String html = "<p>Dear " + html(context.applicationContext().candidateName()) + ",</p>"
                + "<p>An employment offer for <strong>" + html(context.applicationContext().jobTitle()) + "</strong> has been sent to you.</p>"
                + "<p><strong>Compensation:</strong> " + html(offer.getCurrency() + " " + offer.getAnnualGrossCompensation()) + "<br>"
                + "<strong>Proposed start date:</strong> " + html(value(offer.getProposedStartDate())) + "<br>"
                + "<strong>Expires on:</strong> " + html(value(offer.getExpiresOn())) + "</p>"
                + (offer.getTerms() == null || offer.getTerms().isBlank() ? "" : "<p>" + html(offer.getTerms()).replace("\n", "<br>") + "</p>")
                + "<p>View your application and respond to the offer: <a href=\"" + html(trackingUrl) + "\">here</a></p>"
                + "<p>Regards,<br><strong>" + html(context.applicationContext().companyName()) + "</strong></p>";

        sendCandidate(context.applicationContext().candidate(), "Employment offer", html, text, "offer sent notice");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyOfferApprovalRejected(Long offerId) {
        trace("START", "offer approval rejected notice offerId=" + offerId);
        OfferContext context = offerContext(offerId, "offer approval rejected notice");
        if (context == null) return;
        trace("BUILD", "offer approval rejected notice offerId=" + offerId + " applicationId=" + context.offer().getApplicationId());
        String text = "The employment offer for " + context.applicationContext().candidateName()
                + " (" + context.applicationContext().jobTitle() + ") was rejected during approval.";
        String html = "<p>The employment offer for <strong>" + html(context.applicationContext().candidateName())
                + "</strong> (" + html(context.applicationContext().jobTitle()) + ") was rejected during approval.</p>";
        notifyRecruitmentOwners(context.applicationContext(), "Employment offer rejected", html, text,
                "offer approval rejected notice");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyOfferResponse(Long offerId) {
        trace("START", "offer response notice offerId=" + offerId);
        OfferContext context = offerContext(offerId, "offer response notice");
        if (context == null) return;
        String outcome = context.offer().getStatus().name().replace('_', ' ').toLowerCase(Locale.ROOT);
        trace("BUILD", "offer response notice offerId=" + offerId
                + " applicationId=" + context.offer().getApplicationId()
                + " outcome=" + outcome);
        String candidateText = "Dear " + context.applicationContext().candidateName() + ",\n\n"
                + "We have recorded your employment offer response as " + outcome + ".\n\n"
                + "Regards,\n" + context.applicationContext().companyName();
        String candidateHtml = "<p>Dear " + html(context.applicationContext().candidateName()) + ",</p>"
                + "<p>We have recorded your employment offer response as <strong>" + html(outcome) + "</strong>.</p>"
                + "<p>Regards,<br><strong>" + html(context.applicationContext().companyName()) + "</strong></p>";
        sendCandidate(context.applicationContext().candidate(), "Offer response recorded", candidateHtml, candidateText,
                "candidate offer response notice");

        String internalText = context.applicationContext().candidateName() + " has " + outcome
                + " the employment offer for " + context.applicationContext().jobTitle() + ".";
        String internalHtml = "<p><strong>" + html(context.applicationContext().candidateName()) + "</strong> has <strong>"
                + html(outcome) + "</strong> the employment offer for " + html(context.applicationContext().jobTitle()) + ".</p>";
        notifyRecruitmentOwners(context.applicationContext(), "Candidate offer response", internalHtml, internalText,
                "candidate offer response internal notice");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void notifyOnboardingStarted(Long applicationId) {
        trace("START", "onboarding started notice applicationId=" + applicationId);
        ApplicationContext context = context(applicationId, "onboarding started notice");
        if (context == null) return;
        trace("BUILD", "onboarding started notice applicationId=" + applicationId);
        String text = "Dear " + context.candidateName() + ",\n\n"
                + "Your onboarding for " + context.jobTitle()
                + " has been started. The HR team will contact you with any remaining steps.\n\n"
                + "Regards,\n" + context.companyName();
        String html = "<p>Dear " + html(context.candidateName()) + ",</p>"
                + "<p>Your onboarding for <strong>" + html(context.jobTitle())
                + "</strong> has been started. The HR team will contact you with any remaining steps.</p>"
                + "<p>Regards,<br><strong>" + html(context.companyName()) + "</strong></p>";
        sendCandidate(context.candidate(), "Onboarding started", html, text, "onboarding started notice");
    }

    private void notifyRecruitmentOwners(ApplicationContext context, String subject, String html, String text, String sendContext) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(context.application().getRecruiterEmployeeId());
        ids.add(context.opening().getAssignedRecruiterEmployeeId());
        ids.add(context.opening().getHiringManagerEmployeeId());
        ids.remove(null);
        trace("RECIPIENTS", sendContext + " ownerEmployeeIds=" + ids);
        ids.stream()
                .map(employeeRepository::findById)
                .flatMap(java.util.Optional::stream)
                .forEach(employee -> sendEmployee(employee, subject, html, text, sendContext));
    }

    private List<Employee> interviewRecipients(Interview interview) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(interview.getCoordinatorEmployeeId());
        panelRepository.findByInterviewId(interview.getId()).forEach(member -> ids.add(member.getEmployeeId()));
        ids.remove(null);
        trace("RECIPIENTS", "interviewId=" + interview.getId() + " coordinatorAndPanelEmployeeIds=" + ids);
        return ids.stream()
                .map(employeeRepository::findById)
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    private OfferContext offerContext(Long offerId, String sendContext) {
        trace("RESOLVE", sendContext + " loading offer offerId=" + offerId);
        EmploymentOffer offer = offerRepository.findById(offerId).orElse(null);
        if (offer == null) {
            trace("SKIP", sendContext + " offer not found offerId=" + offerId);
            log.warn("Recruitment email: skipping {} - offer {} was not found.", sendContext, offerId);
            return null;
        }
        trace("RESOLVE", sendContext + " found offer offerId=" + offer.getId() + " applicationId=" + offer.getApplicationId());
        ApplicationContext applicationContext = context(offer.getApplicationId(), sendContext);
        return applicationContext == null ? null : new OfferContext(offer, applicationContext);
    }

    private InterviewContext interviewContext(Long interviewId, String sendContext) {
        trace("RESOLVE", sendContext + " loading interview interviewId=" + interviewId);
        Interview interview = interviewRepository.findById(interviewId).orElse(null);
        if (interview == null) {
            trace("SKIP", sendContext + " interview not found interviewId=" + interviewId);
            log.warn("Recruitment email: skipping {} - interview {} was not found.", sendContext, interviewId);
            return null;
        }
        trace("RESOLVE", sendContext + " found interview interviewId=" + interview.getId() + " applicationId=" + interview.getApplicationId());
        ApplicationContext applicationContext = context(interview.getApplicationId(), sendContext);
        return applicationContext == null ? null : new InterviewContext(interview, applicationContext);
    }

    private ApplicationContext context(Long applicationId, String sendContext) {
        trace("RESOLVE", sendContext + " loading application applicationId=" + applicationId);
        JobApplication application = applicationRepository.findById(applicationId).orElse(null);
        if (application == null) {
            trace("SKIP", sendContext + " application not found applicationId=" + applicationId);
            log.warn("Recruitment email: skipping {} - application {} was not found.", sendContext, applicationId);
            return null;
        }
        trace("RESOLVE", sendContext + " found application applicationId=" + application.getId()
                + " candidateId=" + application.getCandidateId()
                + " openingId=" + application.getJobOpeningId()
                + " stage=" + application.getCurrentStage()
                + " status=" + application.getStatus());
        Candidate candidate = candidateRepository.findById(application.getCandidateId()).orElse(null);
        if (candidate == null) {
            trace("SKIP", sendContext + " candidate not found applicationId=" + applicationId + " candidateId=" + application.getCandidateId());
            log.warn("Recruitment email: skipping {} for application {} - candidate {} was not found.",
                    sendContext, applicationId, application.getCandidateId());
            return null;
        }
        trace("RESOLVE", sendContext + " found candidate candidateId=" + candidate.getId()
                + " email=" + candidate.getEmail()
                + " doNotContact=" + candidate.isDoNotContact());
        JobOpening opening = openingRepository.findById(application.getJobOpeningId()).orElse(null);
        if (opening == null) {
            trace("SKIP", sendContext + " opening not found applicationId=" + applicationId + " openingId=" + application.getJobOpeningId());
            log.warn("Recruitment email: skipping {} for application {} - opening {} was not found.",
                    sendContext, applicationId, application.getJobOpeningId());
            return null;
        }
        trace("RESOLVE", sendContext + " found opening openingId=" + opening.getId()
                + " jobTitle=" + opening.getJobTitle()
                + " recruiterEmployeeId=" + opening.getAssignedRecruiterEmployeeId()
                + " hiringManagerEmployeeId=" + opening.getHiringManagerEmployeeId());
        return new ApplicationContext(application, candidate, opening, DEFAULT_COMPANY_NAME);
    }

    private void sendCandidate(Candidate candidate, String subject, String html, String text, String context) {
        if (candidate.isDoNotContact()) {
            trace("SKIP", context + " candidateId=" + candidate.getId() + " doNotContact=true");
            log.warn("Recruitment email: skipping {} for candidate {} - candidate is marked do-not-contact.",
                    context, candidate.getId());
            return;
        }
        trace("RECIPIENT", context + " candidateId=" + candidate.getId() + " email=" + candidate.getEmail());
        send(candidate.getEmail(), subject, html, text, context + " to candidate " + candidate.getId());
    }

    private void sendEmployee(Employee employee, String subject, String html, String text, String context) {
        trace("RECIPIENT", context + " employeeId=" + employee.getId() + " email=" + employee.getEmail());
        send(employee.getEmail(), subject, html, text, context + " to employee " + employee.getId());
    }

    private void send(String email, String subject, String html, String text, String context) {
        if (email == null || email.isBlank()) {
            trace("SKIP", context + " no email address");
            log.warn("Recruitment email: skipping {} - no email address on file.", context);
            return;
        }
        try {
            trace("SEND", context + " to=" + email.trim() + " subject=" + subject);
            String resendEmailId = resendService.sendEmail(email.trim(), subject, html, text);
            trace("SENT", context + " to=" + email.trim() + " resendEmailId=" + resendEmailId);
        } catch (Exception e) {
            trace("ERROR", context + " to=" + email.trim() + " errorClass=" + e.getClass().getName()
                    + " message=" + e.getMessage());
            e.printStackTrace(System.out);
            log.warn("Recruitment email: failed to send {}: {}", context, e.getMessage());
        }
    }

    private void trace(String stage, String message) {
        System.out.println("[RecruitmentMail][" + stage + "] " + message);
    }

    private String schedule(Interview interview) {
        if (interview.getScheduledStart() == null || interview.getScheduledEnd() == null) return "To be confirmed";
        return DATE_TIME.format(interview.getScheduledStart()) + " - " + DATE_TIME.format(interview.getScheduledEnd());
    }

    private String location(Interview interview) {
        return interview.getLocationOrMeetingLink() == null || interview.getLocationOrMeetingLink().isBlank()
                ? "To be confirmed"
                : interview.getLocationOrMeetingLink().trim();
    }

    private String value(Object value) {
        return value == null ? "Not set" : value.toString();
    }

    private String html(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record ApplicationContext(JobApplication application, Candidate candidate, JobOpening opening, String companyName) {
        String candidateName() {
            return candidate.getFullName();
        }

        String jobTitle() {
            return opening.getJobTitle();
        }
    }

    private record InterviewContext(Interview interview, ApplicationContext applicationContext) { }

    private record OfferContext(EmploymentOffer offer, ApplicationContext applicationContext) { }
}
