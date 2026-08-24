package com.justjava.humanresource.recruitment.repository;

import com.justjava.humanresource.recruitment.entity.JobOpening;
import com.justjava.humanresource.recruitment.enums.JobOpeningStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JobOpeningRepository extends JpaRepository<JobOpening, Long> {
    Optional<JobOpening> findByStaffRequisitionRequestId(Long requestId);
    Optional<JobOpening> findByPublicSlugAndStatus(String slug, JobOpeningStatus status);
    List<JobOpening> findByStatusOrderByCreatedAtDesc(JobOpeningStatus status);
    List<JobOpening> findAllByOrderByCreatedAtDesc();
    long countByStatus(JobOpeningStatus status);
}
