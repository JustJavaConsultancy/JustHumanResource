package com.justjava.humanresource.hr.event;


import com.justjava.humanresource.hr.entity.Employee;
import lombok.Getter;

@Getter
public class SalaryChangedEvent {

    private final Employee employee;

    public SalaryChangedEvent(Employee employee) {
        this.employee = employee;
    }
}
