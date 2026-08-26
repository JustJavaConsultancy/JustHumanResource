package com.justjava.humanresource.recruitment;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.recruitment.entity.Candidate;
import com.justjava.humanresource.recruitment.entity.EmploymentOffer;
import com.justjava.humanresource.recruitment.entity.JobApplication;
import com.justjava.humanresource.recruitment.entity.JobOpening;
import com.justjava.humanresource.recruitment.enums.OfferStatus;
import com.justjava.humanresource.recruitment.repository.*;
import com.justjava.humanresource.recruitment.service.CandidateHireService;
import com.justjava.humanresource.recruitment.service.InterviewService;
import com.justjava.humanresource.recruitment.service.OfferService;
import com.justjava.humanresource.recruitment.service.RecruitmentService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecruitmentControllerUiAlignmentTest {
    @Mock JobOpeningRepository openingRepository;
    @Mock JobApplicationRepository applicationRepository;
    @Mock CandidateRepository candidateRepository;
    @Mock ApplicationStageHistoryRepository historyRepository;
    @Mock RecruitmentService recruitmentService;
    @Mock InterviewService interviewService;
    @Mock OfferService offerService;
    @Mock CandidateHireService candidateHireService;
    @Mock InterviewRepository interviewRepository;
    @Mock InterviewScorecardRepository scorecardRepository;
    @Mock EmploymentOfferRepository offerRepository;
    @Mock CandidateEmployeeConversionRepository conversionRepository;
    @Mock TaskService taskService;
    @Mock AuthenticationManager authenticationManager;
    RecruitmentController controller;

    @BeforeEach
    void setUp() {
        controller = new RecruitmentController(openingRepository, applicationRepository, candidateRepository,
                historyRepository, recruitmentService, interviewService, offerService, candidateHireService,
                interviewRepository, scorecardRepository, offerRepository, conversionRepository,
                taskService, authenticationManager);
    }

    @Test
    void financeApplicationViewGetsOfferApprovalOnlyFlags() {
        JobApplication application = new JobApplication();
        application.setId(40L);
        application.setCandidateId(30L);
        application.setJobOpeningId(20L);
        Candidate candidate = new Candidate();
        candidate.setId(30L);
        JobOpening opening = new JobOpening();
        opening.setId(20L);
        EmploymentOffer offer = new EmploymentOffer();
        offer.setStatus(OfferStatus.IN_APPROVAL);

        when(applicationRepository.findById(40L)).thenReturn(Optional.of(application));
        when(authenticationManager.isFinancialOfficer()).thenReturn(true);
        when(offerRepository.findByApplicationIdOrderByOfferVersionDesc(40L)).thenReturn(List.of(offer));
        when(candidateRepository.findById(30L)).thenReturn(Optional.of(candidate));
        when(openingRepository.findById(20L)).thenReturn(Optional.of(opening));
        when(historyRepository.findByApplicationIdOrderByCreatedAt(40L)).thenReturn(List.of());
        when(interviewRepository.findByApplicationIdOrderByScheduledStartAsc(40L)).thenReturn(List.of());
        when(conversionRepository.findByApplicationId(40L)).thenReturn(Optional.empty());

        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("recruitment/application-detail", controller.application(40L, model));

        assertEquals(false, model.get("canManageRecruitment"));
        assertEquals(true, model.get("canApproveOffers"));
        assertEquals(false, model.get("canScheduleInterview"));
        assertEquals(false, model.get("canCreateOffer"));
        assertEquals(false, model.get("canStartOnboarding"));
    }

    @Test
    void assignedHiringManagerGetsReviewOnlyOpeningFlags() {
        JobOpening opening = new JobOpening();
        opening.setId(20L);
        opening.setHiringManagerEmployeeId(5L);
        when(recruitmentService.requireOpening(20L)).thenReturn(opening);
        when(authenticationManager.get("employeeId")).thenReturn("5");
        when(applicationRepository.findByJobOpeningIdOrderBySubmittedAtDesc(20L)).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("recruitment/job-opening-detail", controller.opening(20L, model));

        assertEquals(false, model.get("canPrepareOpening"));
        assertEquals(true, model.get("canReviewOpening"));
        assertEquals(false, model.get("canCloseOpening"));
    }
}
