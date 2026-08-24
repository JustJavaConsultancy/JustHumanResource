package com.justjava.humanresource.recruitment.repository;
import com.justjava.humanresource.recruitment.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface InterviewRepository extends JpaRepository<Interview, Long> { List<Interview> findByApplicationIdOrderByScheduledStartAsc(Long applicationId); }
