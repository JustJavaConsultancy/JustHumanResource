package com.justjava.humanresource.integration.inbound.payroll.enums;


public enum OutboxStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED,
    CANCELLED
}
