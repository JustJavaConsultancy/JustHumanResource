package com.justjava.humanresource.utils;

public interface EmailService {

    String sendEmail(String to, String subject, String html, String text);

    String sendPdfAttachment(
            String to,
            String subject,
            String html,
            String text,
            String filename,
            byte[] pdfBytes
    );
}
