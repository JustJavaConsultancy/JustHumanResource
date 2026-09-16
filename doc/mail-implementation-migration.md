# Mail Implementation Migration Guide

This application has been migrated from the Resend Java SDK to Spring Boot's standard mail framework.

The current implementation uses:

- `spring-boot-starter-mail`
- `JavaMailSender`
- `EmailService`
- `SpringMailEmailService`

The email sender is now SMTP-based. Spring Boot provides the framework, but the application still needs a valid SMTP server or mail relay to deliver messages.

## Code Overview

Main files:

- `src/main/java/com/justjava/humanresource/utils/EmailService.java`
- `src/main/java/com/justjava/humanresource/utils/SpringMailEmailService.java`
- `src/main/resources/application.yml`

Email callers:

- `LeaveEmailService`
- `RequestEmailService`
- `PaySlipEmailService`

The old `ResendService` has been removed. New code should depend on `EmailService`, not on a provider-specific mail client.

## Required Configuration

Configure the following environment variables:

| Variable | Required | Description |
| --- | --- | --- |
| `SMTP_HOST` | Yes | SMTP server hostname. |
| `SMTP_PORT` | Yes | SMTP port, usually `587`, `465`, or `1025` for local test servers. |
| `SMTP_USERNAME` | Usually | SMTP username. Some local test servers do not require this. |
| `SMTP_PASSWORD` | Usually | SMTP password or app password. |
| `SMTP_AUTH` | Yes | Whether SMTP authentication is enabled. Usually `true` in production. |
| `SMTP_STARTTLS` | Yes | Whether STARTTLS is enabled. Usually `true` for port `587`. |
| `MAIL_FROM` | Yes | Sender email address used in outgoing messages. |

Current `application.yml` mapping:

```yaml
spring:
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: ${SMTP_AUTH:true}
          starttls:
            enable: ${SMTP_STARTTLS:true}

app:
  mail:
    from: ${MAIL_FROM:no-reply@yourdomain.com}
```

## Local Development Options

### Option 1: Mailpit or MailHog

Use this when developers need to test emails locally without sending real messages.

Example configuration:

```text
SMTP_HOST=localhost
SMTP_PORT=1025
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_AUTH=false
SMTP_STARTTLS=false
MAIL_FROM=no-reply@localhost.test
```

Mailpit UI usually runs on `http://localhost:8025`.

### Option 2: Real SMTP Provider

Use this when testing actual delivery.

Example for a typical STARTTLS SMTP service:

```text
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=your-smtp-username
SMTP_PASSWORD=your-smtp-password
SMTP_AUTH=true
SMTP_STARTTLS=true
MAIL_FROM=no-reply@yourdomain.com
```

For port `465`, additional SSL-specific configuration may be needed. Prefer port `587` with STARTTLS unless the provider requires SSL on port `465`.

## Production Configuration Checklist

Before deploying, confirm:

- `MAIL_FROM` uses a verified sender/domain accepted by the SMTP provider.
- SPF, DKIM, and DMARC DNS records are configured for the sender domain.
- SMTP credentials are stored as environment variables or secrets, not committed to source control.
- `SMTP_AUTH=true` unless using an internal trusted relay that explicitly disables authentication.
- `SMTP_STARTTLS=true` when using port `587`.
- The deployment environment allows outbound TCP connections to the SMTP host and port.
- The SMTP provider's rate limits are acceptable for payroll, leave, and workflow-request email volume.

## Removed Configuration

These variables are no longer used:

```text
RESEND_API_KEY
RESEND_FROM
```

Remove them from deployment environments after the SMTP configuration is working.

## Message ID Behavior

`EmailService` returns the JavaMail MIME message ID when available.

For payslip emails, the response field is now:

```text
messageId
```

It replaces the old provider-specific `resendEmailId` field.

## Verifying the Implementation

Run a compile/package check:

```powershell
.\mvnw.cmd -DskipTests package
```

Then test mail delivery through application flows that already send email:

- Submit a leave request.
- Submit a workflow request.
- Send a payslip email from payroll.

For local testing with Mailpit or MailHog, confirm the email appears in the local mailbox UI. For production SMTP, confirm delivery to a real inbox and check spam/quarantine if it does not arrive.

## Developer Guidelines

- Inject `EmailService` for new email features.
- Do not inject `JavaMailSender` directly into business services unless implementing a mail infrastructure class.
- Keep workflow services best-effort where appropriate: email failures should be logged without breaking leave/request approval flows.
- For payroll payslip sending, preserve per-recipient success/failure reporting.
- Avoid provider-specific names in DTOs, service methods, and configuration keys.
- Do not commit SMTP credentials, app passwords, or real provider secrets.

