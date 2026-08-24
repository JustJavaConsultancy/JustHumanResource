package com.justjava.humanresource.recruitment.repository;

import com.justjava.humanresource.recruitment.entity.JobApplication;
import com.justjava.humanresource.recruitment.enums.ApplicationStatus;
import com.justjava.humanresource.recruitment.enums.RecruitmentStage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    boolean existsByCandidateIdAndJobOpeningId(Long candidateId, Long jobOpeningId);
    Optional<JobApplication> findByAccessTokenHash(String tokenHash);
    List<JobApplication> findAllByOrderBySubmittedAtDesc();
    List<JobApplication> findByJobOpeningIdOrderBySubmittedAtDesc(Long jobOpeningId);
    long countByStatus(ApplicationStatus status);
    long countByCurrentStage(RecruitmentStage stage);
}
