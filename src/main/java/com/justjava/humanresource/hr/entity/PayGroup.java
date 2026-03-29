package com.justjava.humanresource.hr.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.core.enums.PayFrequency;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.entity.TaxRelief;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "pay_groups")
public class PayGroup extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayFrequency payFrequency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;

    @ManyToOne
    @JoinColumn(name = "parent_pay_group_id")
    private PayGroup parent;

    @ManyToMany
    @JoinTable(
            name = "paygroup_tax_reliefs",
            joinColumns = @JoinColumn(name = "paygroup_id"),
            inverseJoinColumns = @JoinColumn(name = "tax_relief_id")
    )
    private List<TaxRelief> taxReliefs;
}
