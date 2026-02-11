package com.justjava.humanresource.payroll.event;


import com.justjava.humanresource.hr.entity.Employee;
import lombok.Getter;
import java.time.LocalDate;

@Getter
public class PayGroupChangedEvent {

    private final Employee employee;
    private final LocalDate effectiveDate;

    public PayGroupChangedEvent(Employee employee, LocalDate effectiveDate) {
        this.employee = employee;
        this.effectiveDate = effectiveDate;
    }
}

