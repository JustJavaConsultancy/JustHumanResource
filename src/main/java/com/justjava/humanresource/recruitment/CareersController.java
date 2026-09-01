package com.justjava.humanresource.recruitment;

import com.justjava.humanresource.recruitment.dto.PublicApplicationCommand;
import com.justjava.humanresource.recruitment.enums.JobOpeningStatus;
import com.justjava.humanresource.recruitment.repository.CandidateRepository;
import com.justjava.humanresource.recruitment.repository.EmploymentOfferRepository;
import com.justjava.humanresource.recruitment.repository.JobApplicationRepository;
import com.justjava.humanresource.recruitment.repository.JobOpeningRepository;
import com.justjava.humanresource.recruitment.service.OfferService;
import com.justjava.humanresource.recruitment.service.RecruitmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Controller @RequestMapping("/careers") @RequiredArgsConstructor
public class CareersController {
    private final JobOpeningRepository openingRepository;
    private final JobApplicationRepository applicationRepository;
    private final CandidateRepository candidateRepository;
    private final EmploymentOfferRepository offerRepository;
    private final RecruitmentService recruitmentService;
    private final OfferService offerService;
    private final TaskService taskService;

    @GetMapping
    public String jobs(Model model) {
        model.addAttribute("jobs", openingRepository.findPubliclyAvailable(JobOpeningStatus.PUBLISHED, LocalDate.now()));
        return "careers/jobs";
    }
    @GetMapping("/jobs/{slug}")
    public String job(@PathVariable String slug, Model model) {
        model.addAttribute("job", recruitmentService.requirePubliclyAvailableOpening(slug));
        model.addAttribute("application", new PublicApplicationCommand());
        return "careers/job-detail";
    }
    @PostMapping("/jobs/{slug}/apply")
    public String apply(@PathVariable String slug, @Valid @ModelAttribute("application") PublicApplicationCommand command,
                        BindingResult errors, Model model) {
        var job = recruitmentService.requirePubliclyAvailableOpening(slug);
        if (errors.hasErrors()) { model.addAttribute("job", job); return "careers/job-detail"; }
        model.addAttribute("result", recruitmentService.apply(slug, command));
        return "careers/application-success";
    }

    @GetMapping("/applications/{token}")
    public String applicationStatus(@PathVariable String token, Model model) {
        var application = recruitmentService.requireApplicationByAccessToken(token);
        model.addAttribute("token", token);
        model.addAttribute("application", application);
        model.addAttribute("candidate", candidateRepository.findById(application.getCandidateId()).orElseThrow());
        model.addAttribute("job", openingRepository.findById(application.getJobOpeningId()).orElseThrow());
        model.addAttribute("offers", offerRepository.findByApplicationIdOrderByOfferVersionDesc(application.getId()));
        model.addAttribute("offerService", offerService);
        return "careers/application-status";
    }

    @GetMapping("/track")
    public String trackForm(@RequestParam(required = false) String applicationNumber,
                            @RequestParam(required = false) String email,
                            Model model) {
        model.addAttribute("applicationNumber", applicationNumber != null ? applicationNumber : "");
        model.addAttribute("email", email != null ? email : "");
        return "careers/track-application";
    }

    @PostMapping("/track")
    public String trackApplication(@RequestParam String applicationNumber,
                                   @RequestParam String email,
                                   Model model) {
        var applicationOpt = applicationRepository.findByApplicationNumber(applicationNumber.trim());
        if (applicationOpt.isEmpty()) {
            model.addAttribute("error", "Application not found. Please check your application number.");
            model.addAttribute("applicationNumber", applicationNumber);
            model.addAttribute("email", email);
            return "careers/track-application";
        }
        var application = applicationOpt.get();
        var candidate = candidateRepository.findById(application.getCandidateId()).orElse(null);
        if (candidate == null || !email.trim().equalsIgnoreCase(candidate.getEmail())) {
            model.addAttribute("error", "The email address does not match this application.");
            model.addAttribute("applicationNumber", applicationNumber);
            model.addAttribute("email", email);
            return "careers/track-application";
        }
        model.addAttribute("application", application);
        model.addAttribute("candidate", candidate);
        model.addAttribute("job", openingRepository.findById(application.getJobOpeningId()).orElseThrow());
        model.addAttribute("offers", offerRepository.findByApplicationIdOrderByOfferVersionDesc(application.getId()));
        model.addAttribute("offerService", offerService);
        model.addAttribute("applicationNumber", applicationNumber);
        model.addAttribute("email", email);
        return "careers/application-status";
    }

    @PostMapping("/track/offers/{offerId}/respond")
    public String respondToOfferByTracking(@RequestParam String applicationNumber,
                                           @RequestParam String email,
                                           @PathVariable Long offerId,
                                           @RequestParam boolean accepted) {
        var application = applicationRepository.findByApplicationNumber(applicationNumber.trim())
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        var candidate = candidateRepository.findById(application.getCandidateId())
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found."));
        if (!email.trim().equalsIgnoreCase(candidate.getEmail())) {
            throw new IllegalArgumentException("Email does not match this application.");
        }
        var offer = offerRepository.findById(offerId).orElseThrow();
        if (!offer.getApplicationId().equals(application.getId())) {
            throw new IllegalArgumentException("Offer does not belong to this application.");
        }
        offerService.respond(offerId, accepted);
        if (application.getWorkflowInstanceId() != null) {
            var task = taskService.createTaskQuery().processInstanceId(application.getWorkflowInstanceId()).active().singleResult();
            if (task != null && "offerTask".equals(task.getTaskDefinitionKey())) {
                taskService.complete(task.getId(), Map.of(
                        "recruitmentDecision", accepted ? "ACCEPT_OFFER" : "DECLINE_OFFER",
                        "recruitmentComment", accepted ? "Candidate accepted offer" : "Candidate declined offer",
                        "flowableTaskId", task.getId()
                ));
            }
        }
        return "redirect:/careers/track?applicationNumber=" + applicationNumber + "&email=" + email;
    }

    @PostMapping("/applications/{token}/offers/{offerId}/respond")
    public String respondToOffer(@PathVariable String token, @PathVariable Long offerId,
                                 @RequestParam boolean accepted) {
        var application = recruitmentService.requireApplicationByAccessToken(token);
        var offer = offerRepository.findById(offerId).orElseThrow();
        if (!offer.getApplicationId().equals(application.getId())) {
            throw new IllegalArgumentException("Offer does not belong to this application.");
        }
        offerService.respond(offerId, accepted);
        if (application.getWorkflowInstanceId() != null) {
            var task = taskService.createTaskQuery().processInstanceId(application.getWorkflowInstanceId()).active().singleResult();
            if (task != null && "offerTask".equals(task.getTaskDefinitionKey())) {
                taskService.complete(task.getId(), Map.of(
                        "recruitmentDecision", accepted ? "ACCEPT_OFFER" : "DECLINE_OFFER",
                        "recruitmentComment", accepted ? "Candidate accepted offer" : "Candidate declined offer",
                        "flowableTaskId", task.getId()
                ));
            }
        }
        return "redirect:/careers/applications/" + token;
    }
}
