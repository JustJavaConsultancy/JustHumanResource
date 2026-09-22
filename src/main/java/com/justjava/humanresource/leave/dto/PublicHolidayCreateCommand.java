package com.justjava.humanresource.leave.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PublicHolidayCreateCommand {
    private LocalDate date;
    private String name;
}