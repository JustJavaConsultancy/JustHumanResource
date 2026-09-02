package com.justjava.humanresource.communication.meeting.repository;

import com.justjava.humanresource.communication.meeting.entity.HrMeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface HrMeetingParticipantRepository extends JpaRepository<HrMeetingParticipant, Long> {
    List<HrMeetingParticipant> findByMeeting_IdOrderByEmployeeNameAsc(Long meetingId);
    List<HrMeetingParticipant> findByMeeting_IdInOrderByEmployeeNameAsc(Collection<Long> meetingIds);
    List<HrMeetingParticipant> findByEmployee_IdOrderByMeeting_StartTimeDesc(Long employeeId);
}
