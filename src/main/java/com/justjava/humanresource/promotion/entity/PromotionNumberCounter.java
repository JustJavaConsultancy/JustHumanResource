package com.justjava.humanresource.promotion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "hr_promotion_number_counter",
        uniqueConstraints = @UniqueConstraint(name = "uk_promotion_counter_prefix_year", columnNames = {"prefix", "counter_year"})
)
public class PromotionNumberCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String prefix;

    @Column(name = "counter_year", nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Long lastNumber;
}
