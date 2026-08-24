package com.justjava.humanresource.recruitment.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name = "recruitment_interview_panel_members", uniqueConstraints =
        @UniqueConstraint(name = "uk_interview_panel_member", columnNames = {"interviewId", "employeeId"}))
public class InterviewPanelMember extends BaseEntity {
    @Column(nullable = false) private Long interviewId;
    @Column(nullable = false) private Long employeeId;
    @Column(nullable = false) private boolean chairperson;
}
