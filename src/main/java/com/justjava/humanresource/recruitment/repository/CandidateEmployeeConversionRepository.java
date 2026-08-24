package com.justjava.humanresource.recruitment.repository;
import com.justjava.humanresource.recruitment.entity.CandidateEmployeeConversion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CandidateEmployeeConversionRepository extends JpaRepository<CandidateEmployeeConversion, Long> { Optional<CandidateEmployeeConversion> findByApplicationId(Long applicationId); }
