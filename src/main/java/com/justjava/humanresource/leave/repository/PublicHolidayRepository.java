package com.justjava.humanresource.leave.repository;

import com.justjava.humanresource.leave.entity.PublicHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {
    List<PublicHoliday> findAllByOrderByDateAsc();
    List<PublicHoliday> findByDateBetween(LocalDate start, LocalDate end);
    boolean existsByDate(LocalDate date);
}