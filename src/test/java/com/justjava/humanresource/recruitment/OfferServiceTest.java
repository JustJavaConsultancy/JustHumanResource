package com.justjava.humanresource.recruitment;

import com.justjava.humanresource.recruitment.entity.EmploymentOffer;
import com.justjava.humanresource.recruitment.entity.JobApplication;
import com.justjava.humanresource.recruitment.enums.ApplicationStatus;
import com.justjava.humanresource.recruitment.enums.OfferStatus;
import com.justjava.humanresource.recruitment.enums.RecruitmentStage;
import com.justjava.humanresource.recruitment.repository.EmploymentOfferRepository;
import com.justjava.humanresource.recruitment.repository.JobApplicationRepository;
import com.justjava.humanresource.recruitment.service.OfferService;
import com.justjava.humanresource.utils.AfterCommitExecutor;
import com.justjava.humanresource.utils.RecruitmentEmailService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfferServiceTest {
    @Mock EmploymentOfferRepository offerRepository;
    @Mock JobApplicationRepository applicationRepository;
    @Mock RuntimeService runtimeService;
    @Mock RecruitmentEmailService recruitmentEmailService;
    @Mock AfterCommitExecutor afterCommitExecutor;
    OfferService service;

    @BeforeEach
    void setUp() {
        service = new OfferService(offerRepository, applicationRepository, runtimeService,
                recruitmentEmailService, afterCommitExecutor);
    }

    @Test
    void acceptingLatestSentOfferUpdatesOfferAndApplication() {
        EmploymentOffer offer = offer(10L, 20L, 2, OfferStatus.SENT);
        JobApplication application = new JobApplication();
        application.setId(20L);
        when(offerRepository.findById(10L)).thenReturn(Optional.of(offer));
        when(offerRepository.findFirstByApplicationIdOrderByOfferVersionDesc(20L)).thenReturn(Optional.of(offer));
        when(applicationRepository.findById(20L)).thenReturn(Optional.of(application));
        when(offerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        EmploymentOffer saved = service.respond(10L, true);

        assertEquals(OfferStatus.ACCEPTED, saved.getStatus());
        assertEquals(ApplicationStatus.OFFER_ACCEPTED, application.getStatus());
        assertEquals(RecruitmentStage.PRE_EMPLOYMENT, application.getCurrentStage());
        verify(applicationRepository).save(application);
        verify(afterCommitExecutor).runAfterCommit(any(Runnable.class));
    }

    @Test
    void olderOfferCannotBeAnswered() {
        EmploymentOffer oldOffer = offer(10L, 20L, 1, OfferStatus.SENT);
        EmploymentOffer latestOffer = offer(11L, 20L, 2, OfferStatus.SENT);
        when(offerRepository.findById(10L)).thenReturn(Optional.of(oldOffer));
        when(offerRepository.findFirstByApplicationIdOrderByOfferVersionDesc(20L)).thenReturn(Optional.of(latestOffer));

        assertThrows(IllegalStateException.class, () -> service.respond(10L, true));
        verifyNoInteractions(applicationRepository);
    }

    @Test
    void onlyLatestUnexpiredSentOfferIsRespondable() {
        EmploymentOffer oldOffer = offer(10L, 20L, 1, OfferStatus.SENT);
        EmploymentOffer latestOffer = offer(11L, 20L, 2, OfferStatus.SENT);
        when(offerRepository.findFirstByApplicationIdOrderByOfferVersionDesc(20L)).thenReturn(Optional.of(latestOffer));

        assertFalse(service.isRespondable(oldOffer));
        assertTrue(service.isRespondable(latestOffer));
    }

    @Test
    void expiredSentOfferIsNotRespondable() {
        EmploymentOffer offer = offer(10L, 20L, 1, OfferStatus.SENT);
        offer.setExpiresOn(LocalDate.now().minusDays(1));
        when(offerRepository.findFirstByApplicationIdOrderByOfferVersionDesc(20L)).thenReturn(Optional.of(offer));

        assertFalse(service.isRespondable(offer));
    }

    private EmploymentOffer offer(Long id, Long applicationId, int version, OfferStatus status) {
        EmploymentOffer offer = new EmploymentOffer();
        offer.setId(id);
        offer.setApplicationId(applicationId);
        offer.setOfferVersion(version);
        offer.setStatus(status);
        offer.setExpiresOn(LocalDate.now().plusDays(7));
        return offer;
    }
}
