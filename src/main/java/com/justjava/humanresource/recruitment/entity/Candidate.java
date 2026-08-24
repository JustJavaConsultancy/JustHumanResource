package com.justjava.humanresource.recruitment.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Entity
@Table(name = "recruitment_candidates", indexes = {
        @Index(name = "idx_candidate_normalized_email", columnList = "normalizedEmail")
}, uniqueConstraints = @UniqueConstraint(name = "uk_candidate_number", columnNames = "candidateNumber"))
public class Candidate extends BaseEntity {
    @Column(nullable = false, length = 30) private String candidateNumber;
    @Column(nullable = false, length = 100) private String firstName;
    @Column(length = 100) private String middleName;
    @Column(nullable = false, length = 100) private String lastName;
    @Column(nullable = false, length = 200) private String email;
    @Column(nullable = false, length = 200) private String normalizedEmail;
    @Column(length = 40) private String phone;
    @Column(length = 500) private String address;
    private String linkedInUrl;
    private String portfolioUrl;
    @Column(length = 50) private String source;
    @Column(nullable = false, length = 40) private String consentVersion;
    @Column(nullable = false) private LocalDateTime consentedAt;
    private LocalDateTime retentionDeadline;
    @Column(nullable = false) private boolean doNotContact;
    public String getFullName() { return firstName + " " + lastName; }
}
