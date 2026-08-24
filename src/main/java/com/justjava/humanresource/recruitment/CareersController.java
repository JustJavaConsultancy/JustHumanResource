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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller @RequestMapping("/careers") @RequiredArgsConstructor
public class CareersController {
    private final JobOpeningRepository openingRepository;
    private final JobApplicationRepository applicationRepository;
    private final CandidateRepository candidateRepository;
    private final EmploymentOfferRepository offerRepository;
    private final RecruitmentService recruitmentService;
    private final OfferService offerService;

    @GetMapping
    public String jobs(Model model) {
        model.addAttribute("jobs", openingRepository.findByStatusOrderByCreatedAtDesc(JobOpeningStatus.PUBLISHED));
        return "careers/jobs";
    }
    @GetMapping("/jobs/{slug}")
    public String job(@PathVariable String slug, Model model) {
        model.addAttribute("job", openingRepository.findByPublicSlugAndStatus(slug, JobOpeningStatus.PUBLISHED).orElseThrow());
        model.addAttribute("application", new PublicApplicationCommand());
        return "careers/job-detail";
    }
    @PostMapping("/jobs/{slug}/apply")
    public String apply(@PathVariable String slug, @Valid @ModelAttribute("application") PublicApplicationCommand command,
                        BindingResult errors, Model model) {
        var job = openingRepository.findByPublicSlugAndStatus(slug, JobOpeningStatus.PUBLISHED).orElseThrow();
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
        return "careers/application-status";
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
        return "redirect:/careers/applications/" + token;
    }
}
