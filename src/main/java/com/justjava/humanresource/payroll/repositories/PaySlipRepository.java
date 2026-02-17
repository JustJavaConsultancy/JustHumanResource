package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.payroll.entity.PaySlip;
import org.springframework.data.jpa.repository.JpaRepository;


import java.time.LocalDate;
import java.util.List;

public interface PaySlipRepository
        extends JpaRepository<PaySlip, Long> {

    List<PaySlip> findByEmployee_Id(Long employeeId);

    List<PaySlip> findByPayDateBetween(LocalDate start, LocalDate end);

    List<PaySlip> findByEmployee_IdAndPayDateBetween(
            Long employeeId,
            LocalDate start,
            LocalDate end
    );
}
