package com.justjava.humanresource.recruitment;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecruitmentTemplateAlignmentTest {
    @Test
    void publicApplicationFormExposesSupportedCandidateFields() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/templates/careers/job-detail.html"));

        assertTrue(html.contains("*{middleName}"));
        assertTrue(html.contains("*{address}"));
        assertTrue(html.contains("*{linkedInUrl}"));
        assertTrue(html.contains("*{portfolioUrl}"));
    }

    @Test
    void applicationDetailUsesRoleFlagsAndShowsOfferAndOnboardingDetails() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/templates/recruitment/application-detail.html"));

        assertTrue(html.contains("canScheduleInterview"));
        assertTrue(html.contains("canCreateOffer"));
        assertTrue(html.contains("canApproveOffers"));
        assertTrue(html.contains("canSendOffer"));
        assertTrue(html.contains("offer.proposedStartDate"));
        assertTrue(html.contains("offer.jobStepId"));
        assertTrue(html.contains("offer.payGroupId"));
        assertTrue(html.contains("offer.terms"));
        assertTrue(html.contains("name=\"pfa\""));
        assertTrue(html.contains("name=\"nextOfKinName\""));
        assertTrue(html.contains("name=\"guarantorName\""));
        assertTrue(html.contains("name=\"guarantorNinNumber\""));
    }

    @Test
    void jobOpeningDetailUsesRoleFlags() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/templates/recruitment/job-opening-detail.html"));

        assertTrue(html.contains("canPrepareOpening"));
        assertTrue(html.contains("canReviewOpening"));
        assertTrue(html.contains("canCloseOpening"));
    }
}
