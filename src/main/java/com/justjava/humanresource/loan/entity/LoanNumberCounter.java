package com.justjava.humanresource.loan.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** One row per prefix and year; generates stable numbers such as LOAN-2026-0001. */
@Getter
@Setter
@Entity
@Table(
        name = "loan_number_counters",
        uniqueConstraints = @UniqueConstraint(name = "uk_loan_counter_prefix_year", columnNames = {"prefix", "counter_year"})
)
public class LoanNumberCounter extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String prefix = "LOAN";

    @Column(name = "counter_year", nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Long lastNumber = 0L;
}
