package com.justjava.humanresource.recruitment.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.recruitment.enums.InterviewStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter @Entity
@Table(name = "recruitment_interviews", indexes = @Index(name = "idx_interview_application", columnList = "applicationId"))
public class Interview extends BaseEntity {
    @Column(nullable = false) private Long applicationId;
    @Column(nullable = false, length = 120) private String title;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private InterviewStatus status = InterviewStatus.DRAFT;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    @Column(length = 300) private String locationOrMeetingLink;
    @Column(length = 2000) private String instructions;
    private Long coordinatorEmployeeId;
    @Column(length = 100) private String workflowInstanceId;
}
