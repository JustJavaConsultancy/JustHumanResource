package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Employee;

public interface PromotionNotificationChannel {

    String name();

    default boolean enabled() {
        return true;
    }

    void send(Employee recipient, String subject, String htmlBody, String textBody, String context);
}
