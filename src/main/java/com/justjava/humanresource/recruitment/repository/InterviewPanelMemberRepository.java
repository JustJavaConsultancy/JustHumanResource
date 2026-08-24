package com.justjava.humanresource.recruitment.repository;
import com.justjava.humanresource.recruitment.entity.InterviewPanelMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface InterviewPanelMemberRepository extends JpaRepository<InterviewPanelMember, Long> { List<InterviewPanelMember> findByInterviewId(Long interviewId); }
