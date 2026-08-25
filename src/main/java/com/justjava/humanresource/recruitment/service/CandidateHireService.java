package com.justjava.humanresource.recruitment.service;

import com.justjava.humanresource.hr.dto.EmployeeOnboardingResponseDTO;
import com.justjava.humanresource.onboarding.dto.StartEmployeeOnboardingCommand;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import com.justjava.humanresource.recruitment.dto.HireCandidateCommand;
import com.justjava.humanresource.recruitment.entity.*;
import com.justjava.humanresource.recruitment.enums.*;
import com.justjava.humanresource.recruitment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class CandidateHireService {
    private final CandidateEmployeeConversionRepository conversionRepository;
    private final JobApplicationRepository applicationRepository;
    private final CandidateRepository candidateRepository;
    private final EmploymentOfferRepository offerRepository;
    private final EmployeeOnboardingService onboardingService;
    private final RecruitmentService recruitmentService;

    @Transactional
    public CandidateEmployeeConversion startOnboarding(Long applicationId, HireCandidateCommand hire, String initiatedBy) {
        return conversionRepository.findByApplicationId(applicationId).orElseGet(() -> convert(applicationId, hire, initiatedBy));
    }

    private CandidateEmployeeConversion convert(Long applicationId, HireCandidateCommand hire, String initiatedBy) {
        JobApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        EmploymentOffer offer = offerRepository.findFirstByApplicationIdOrderByOfferVersionDesc(applicationId)
                .filter(o -> o.getStatus() == OfferStatus.ACCEPTED)
                .orElseThrow(() -> new IllegalStateException("An accepted offer is required before onboarding."));
        validateHireCommand(hire, offer);
        Candidate candidate = candidateRepository.findById(application.getCandidateId())
                .orElseThrow(() -> new IllegalStateException("Candidate record not found."));
        StartEmployeeOnboardingCommand command = map(candidate, offer, hire);
        EmployeeOnboardingResponseDTO response = onboardingService.startOnboarding(command, initiatedBy);
        CandidateEmployeeConversion conversion = new CandidateEmployeeConversion();
        conversion.setApplicationId(applicationId); conversion.setCandidateId(candidate.getId());
        conversion.setEmployeeId(response.getEmployeeId()); conversion.setOnboardingId(response.getId());
        conversion.setOnboardingProcessInstanceId(response.getProcessInstanceId());
        CandidateEmployeeConversion saved = conversionRepository.save(conversion);
        application.setHiredEmployeeId(response.getEmployeeId());
        application.setCurrentStage(RecruitmentStage.ONBOARDING); application.setStatus(ApplicationStatus.HIRED);
        applicationRepository.save(application);
        recruitmentService.recordHire(applicationId);
        return saved;
    }

    private StartEmployeeOnboardingCommand map(Candidate candidate, EmploymentOffer offer, HireCandidateCommand hire) {
        StartEmployeeOnboardingCommand command = new StartEmployeeOnboardingCommand();
        command.setFirstName(candidate.getFirstName()); command.setLastName(candidate.getLastName());
        command.setEmail(candidate.getEmail()); command.setPhoneNumber(candidate.getPhone());
        command.setDepartmentId(hire.getDepartmentId());
        command.setJobStepId(hire.getJobStepId() != null ? hire.getJobStepId() : offer.getJobStepId());
        command.setPayGroupId(hire.getPayGroupId() != null ? hire.getPayGroupId() : offer.getPayGroupId());
        command.setManagerId(hire.getManagerId()); command.setGroups(hire.getGroups());
        command.setTinNumber(hire.getTinNumber()); command.setRsaPin(hire.getRsaPin()); command.setPfa(hire.getPfa());
        command.setNinNumber(hire.getNinNumber()); command.setBvnNumber(hire.getBvnNumber());
        command.setNextOfKinName(hire.getNextOfKinName()); command.setNextOfKinPhoneNumber(hire.getNextOfKinPhoneNumber());
        command.setNextOfKinEmail(hire.getNextOfKinEmail()); command.setNextOfKinAddress(hire.getNextOfKinAddress());
        command.setGuarantorName(hire.getGuarantorName()); command.setGuarantorPhoneNumber(hire.getGuarantorPhoneNumber());
        command.setGuarantorEmail(hire.getGuarantorEmail()); command.setGuarantorAddress(hire.getGuarantorAddress());
        command.setGuarantorNinNumber(hire.getGuarantorNinNumber());
        return command;
    }

    private void validateHireCommand(HireCandidateCommand hire, EmploymentOffer offer) {
        if (hire == null) throw new IllegalArgumentException("Onboarding details are required.");
        if (hire.getDepartmentId() == null) throw new IllegalArgumentException("Department is required before onboarding.");
        if (hire.getGroups() == null || hire.getGroups().isEmpty()) throw new IllegalArgumentException("At least one employee group is required.");
        if (hire.getJobStepId() == null && offer.getJobStepId() == null) throw new IllegalArgumentException("Job step is required before onboarding.");
        if (hire.getPayGroupId() == null && offer.getPayGroupId() == null) throw new IllegalArgumentException("Pay group is required before onboarding.");
    }
}
