package com.justjava.humanresource.recruitment.repository;
import com.justjava.humanresource.recruitment.entity.InterviewScorecard;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface InterviewScorecardRepository extends JpaRepository<InterviewScorecard, Long> { List<InterviewScorecard> findByInterviewId(Long interviewId); }
