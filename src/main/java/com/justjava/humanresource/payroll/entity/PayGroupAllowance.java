package com.justjava.humanresource.payroll.entity;


import com.justjava.humanresource.common.entity.BaseEntity;
import com.justjava.humanresource.hr.entity.PayGroup;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pay_group_allowances",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"pay_group_id", "allowance_id"})
    )
public class PayGroupAllowance extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "pay_group_id")
    private PayGroup payGroup;

    @ManyToOne(optional = false)
    @JoinColumn(name = "allowance_id")
    private Allowance allowance;
}
