package com.justjava.humanresource.communication.meeting.repository;

import com.justjava.humanresource.communication.meeting.entity.HrMeeting;
import com.justjava.humanresource.communication.meeting.entity.HrMeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HrMeetingRepository extends JpaRepository<HrMeeting, Long> {
    List<HrMeeting> findByStatusOrderByStartTimeDesc(HrMeetingStatus status);
}
