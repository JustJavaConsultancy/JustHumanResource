package com.justjava.humanresource.utils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class SpringMailEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String defaultFrom;

    public SpringMailEmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:}") String defaultFrom
    ) {
        this.mailSender = mailSender;
        this.defaultFrom = defaultFrom;
    }

    @Override
    public String sendEmail(String to, String subject, String html, String text) {
        validateConfigured(to);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name()
            );
            applyCommonFields(helper, to, subject);
            helper.setText(resolveText(text, html), resolveHtml(text, html));
            mailSender.send(message);
            return message.getMessageID();
        } catch (MessagingException | MailException e) {
            throw new IllegalStateException("Spring Mail failed to send email: " + e.getMessage(), e);
        }
    }

    @Override
    public String sendPdfAttachment(
            String to,
            String subject,
            String html,
            String text,
            String filename,
            byte[] pdfBytes
    ) {
        validateConfigured(to);
        if (filename == null || filename.isBlank()) {
            throw new IllegalStateException("Attachment filename is not provided.");
        }
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalStateException("PDF attachment content is not provided.");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name()
            );
            applyCommonFields(helper, to, subject);
            helper.setText(resolveText(text, html), resolveHtml(text, html));
            helper.addAttachment(filename, new ByteArrayResource(pdfBytes), "application/pdf");
            mailSender.send(message);
            return message.getMessageID();
        } catch (MessagingException | MailException e) {
            throw new IllegalStateException("Spring Mail failed to send email: " + e.getMessage(), e);
        }
    }

    private void applyCommonFields(MimeMessageHelper helper, String to, String subject) throws MessagingException {
        helper.setFrom(defaultFrom.trim());
        helper.setTo(to.trim());
        helper.setSubject(subject == null ? "" : subject);
    }

    private void validateConfigured(String to) {
        if (defaultFrom == null || defaultFrom.isBlank()) {
            throw new IllegalStateException("Sender address is not configured.");
        }
        if (to == null || to.isBlank()) {
            throw new IllegalStateException("Recipient email address is not provided.");
        }
    }

    private String resolveText(String text, String html) {
        if (text != null && !text.isBlank()) {
            return text;
        }
        if (html != null && !html.isBlank()) {
            return html.replaceAll("<[^>]*>", "");
        }
        return "";
    }

    private String resolveHtml(String text, String html) {
        if (html != null && !html.isBlank()) {
            return html;
        }
        return resolveText(text, html);
    }
}
