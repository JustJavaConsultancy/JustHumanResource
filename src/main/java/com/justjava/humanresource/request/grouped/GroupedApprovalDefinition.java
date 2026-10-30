package com.justjava.humanresource.request.grouped;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @Entity
@Table(name="grouped_approval_definitions", uniqueConstraints=@UniqueConstraint(name="uk_group_definition_order", columnNames={"pathVersionId", "ordinal"}))
public class GroupedApprovalDefinition extends BaseEntity {

    @Column(nullable=false) private Long pathVersionId;
    @Column(nullable=false) private Integer ordinal;
    @Column(nullable=false, length=100) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private ApprovalPolicy policy;
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="grouped_approval_definition_members", joinColumns=@JoinColumn(name="definition_id"),
        uniqueConstraints=@UniqueConstraint(name="uk_group_definition_employee", columnNames={"definition_id","employee_id"}))
    @OrderColumn(name="member_order")
    @Column(name="employee_id", nullable=false)
    private List<Long> employeeIds = new ArrayList<>();
}
