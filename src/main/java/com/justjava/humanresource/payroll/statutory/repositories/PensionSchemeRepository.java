package com.justjava.humanresource.payroll.statutory.repositories;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.statutory.entity.PensionScheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PensionSchemeRepository extends JpaRepository<PensionScheme, Long> {
    List<PensionScheme> findByStatus(RecordStatus status);


}
