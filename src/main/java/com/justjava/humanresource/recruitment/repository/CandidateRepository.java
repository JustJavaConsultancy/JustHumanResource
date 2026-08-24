package com.justjava.humanresource.recruitment.repository;

import com.justjava.humanresource.recruitment.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    Optional<Candidate> findFirstByNormalizedEmailOrderByCreatedAtDesc(String email);
}
