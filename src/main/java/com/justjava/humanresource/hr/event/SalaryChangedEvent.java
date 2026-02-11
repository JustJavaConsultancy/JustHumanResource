package com.justjava.humanresource.hr.event;


import com.justjava.humanresource.hr.entity.Employee;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class SalaryChangedEvent {

    private final Employee employee;
    private final LocalDate localDate;

    public SalaryChangedEvent(Employee employee, LocalDate localDate) {
        this.employee = employee;
        this.localDate = localDate;
    }
}
