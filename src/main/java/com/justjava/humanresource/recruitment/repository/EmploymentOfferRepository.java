package com.justjava.humanresource.recruitment.repository;
import com.justjava.humanresource.recruitment.entity.EmploymentOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface EmploymentOfferRepository extends JpaRepository<EmploymentOffer, Long> {
    List<EmploymentOffer> findByApplicationIdOrderByOfferVersionDesc(Long applicationId);
    Optional<EmploymentOffer> findFirstByApplicationIdOrderByOfferVersionDesc(Long applicationId);
}
