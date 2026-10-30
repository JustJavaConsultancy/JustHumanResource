package com.justjava.humanresource.request.grouped;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @Entity
@Table(name="grouped_approval_versions", uniqueConstraints=@UniqueConstraint(name="uk_group_version_revision", columnNames={"familyKey", "revision"}))
public class GroupedApprovalVersion extends BaseEntity {

    @Column(nullable=false, length=36) private String familyKey;
    @Column(nullable=false) private Integer revision;
    @Column(nullable=false, length=100) private String name;
    @Column(length=500) private String description;
    @Column(nullable=false, length=20) private String state = "DRAFT";
    @Column(nullable=false) private boolean enabled = true;
    private Long publishedBy;
    private LocalDateTime publishedAt;
}
