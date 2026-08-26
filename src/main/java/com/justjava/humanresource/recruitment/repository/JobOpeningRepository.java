package com.justjava.humanresource.recruitment.repository;

import com.justjava.humanresource.recruitment.entity.JobOpening;
import com.justjava.humanresource.recruitment.enums.JobOpeningStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JobOpeningRepository extends JpaRepository<JobOpening, Long> {
    Optional<JobOpening> findByStaffRequisitionRequestId(Long requestId);
    Optional<JobOpening> findByPublicSlugAndStatus(String slug, JobOpeningStatus status);
    List<JobOpening> findByStatusOrderByCreatedAtDesc(JobOpeningStatus status);
    @Query("""
            select opening from JobOpening opening
            where opening.status = :status
              and (opening.applicationOpenDate is null or opening.applicationOpenDate <= :today)
              and (opening.applicationCloseDate is null or opening.applicationCloseDate >= :today)
            order by opening.createdAt desc
            """)
    List<JobOpening> findPubliclyAvailable(@Param("status") JobOpeningStatus status, @Param("today") LocalDate today);
    @Query("""
            select opening from JobOpening opening
            where opening.publicSlug = :slug
              and opening.status = :status
              and (opening.applicationOpenDate is null or opening.applicationOpenDate <= :today)
              and (opening.applicationCloseDate is null or opening.applicationCloseDate >= :today)
            """)
    Optional<JobOpening> findPubliclyAvailableBySlug(@Param("slug") String slug,
                                                     @Param("status") JobOpeningStatus status,
                                                     @Param("today") LocalDate today);
    List<JobOpening> findAllByOrderByCreatedAtDesc();
    long countByStatus(JobOpeningStatus status);
}
