package com.justjava.humanresource.recruitment.service;

import com.justjava.humanresource.recruitment.entity.*;
import com.justjava.humanresource.recruitment.enums.*;
import com.justjava.humanresource.recruitment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import org.flowable.engine.RuntimeService;
import java.util.Map;

@Service @RequiredArgsConstructor
public class OfferService {
    private final EmploymentOfferRepository offerRepository;
    private final JobApplicationRepository applicationRepository;
    private final RuntimeService runtimeService;

    @Transactional
    public EmploymentOffer createDraft(Long applicationId, BigDecimal annualGross, String currency,
                                       Long jobStepId, Long payGroupId, LocalDate startDate, LocalDate expiresOn, String terms) {
        JobApplication application = requireApplication(applicationId);
        if (application.getStatus() == ApplicationStatus.REJECTED || application.getStatus() == ApplicationStatus.WITHDRAWN)
            throw new IllegalStateException("An offer cannot be created for a closed application.");
        int version = offerRepository.findFirstByApplicationIdOrderByOfferVersionDesc(applicationId)
                .map(o -> o.getOfferVersion() + 1).orElse(1);
        EmploymentOffer offer = new EmploymentOffer();
        offer.setApplicationId(applicationId); offer.setOfferVersion(version);
        offer.setAnnualGrossCompensation(annualGross); offer.setCurrency(currency == null ? "NGN" : currency.toUpperCase());
        offer.setJobStepId(jobStepId); offer.setPayGroupId(payGroupId); offer.setProposedStartDate(startDate);
        offer.setExpiresOn(expiresOn); offer.setTerms(terms); offer.setStatus(OfferStatus.IN_APPROVAL);
        application.setCurrentStage(RecruitmentStage.OFFER); application.setStatus(ApplicationStatus.IN_PROCESS);
        applicationRepository.save(application);
        EmploymentOffer saved = offerRepository.saveAndFlush(offer);
        var process = runtimeService.startProcessInstanceByKey("employmentOfferProcess", "EMPLOYMENT_OFFER_" + saved.getId(),
                Map.of("offerId", saved.getId(), "applicationId", applicationId));
        saved.setWorkflowInstanceId(process.getProcessInstanceId());
        return offerRepository.save(saved);
    }

    @Transactional public EmploymentOffer approve(Long offerId, Long approverId) {
        EmploymentOffer offer = requireOffer(offerId);
        if (offer.getStatus() != OfferStatus.IN_APPROVAL) throw new IllegalStateException("Only an offer in approval can be approved.");
        offer.setStatus(OfferStatus.APPROVED); offer.setApprovedByEmployeeId(approverId); offer.setApprovedAt(LocalDateTime.now());
        return offerRepository.save(offer);
    }
    @Transactional public EmploymentOffer send(Long offerId) {
        EmploymentOffer offer = requireOffer(offerId);
        if (offer.getStatus() != OfferStatus.APPROVED) throw new IllegalStateException("Only an approved offer can be sent.");
        offer.setStatus(OfferStatus.SENT); offer.setSentAt(LocalDateTime.now()); return offerRepository.save(offer);
    }
    @Transactional(readOnly = true)
    public boolean isRespondable(EmploymentOffer offer) {
        if (offer == null || offer.getStatus() != OfferStatus.SENT) return false;
        EmploymentOffer latestOffer = offerRepository.findFirstByApplicationIdOrderByOfferVersionDesc(offer.getApplicationId())
                .orElse(null);
        return latestOffer != null
                && latestOffer.getId().equals(offer.getId())
                && (offer.getExpiresOn() == null || !offer.getExpiresOn().isBefore(LocalDate.now()));
    }
    @Transactional public EmploymentOffer respond(Long offerId, boolean accepted) {
        EmploymentOffer offer = requireOffer(offerId);
        if (offer.getStatus() != OfferStatus.SENT) throw new IllegalStateException("Only a sent offer can be answered.");
        EmploymentOffer latestOffer = offerRepository.findFirstByApplicationIdOrderByOfferVersionDesc(offer.getApplicationId())
                .orElseThrow(() -> new IllegalStateException("Offer history was not found."));
        if (!latestOffer.getId().equals(offer.getId())) {
            throw new IllegalStateException("Only the latest offer can be answered.");
        }
        if (offer.getExpiresOn() != null && offer.getExpiresOn().isBefore(LocalDate.now())) {
            offer.setStatus(OfferStatus.EXPIRED);
            offerRepository.save(offer);
            throw new IllegalStateException("This offer has expired.");
        }
        offer.setStatus(accepted ? OfferStatus.ACCEPTED : OfferStatus.DECLINED); offer.setRespondedAt(LocalDateTime.now());
        JobApplication application = requireApplication(offer.getApplicationId());
        application.setStatus(accepted ? ApplicationStatus.OFFER_ACCEPTED : ApplicationStatus.OFFER_DECLINED);
        application.setCurrentStage(accepted ? RecruitmentStage.PRE_EMPLOYMENT : RecruitmentStage.COMPLETED);
        applicationRepository.save(application); return offerRepository.save(offer);
    }
    private JobApplication requireApplication(Long id) { return applicationRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Application not found.")); }
    private EmploymentOffer requireOffer(Long id) { return offerRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Offer not found.")); }
}
