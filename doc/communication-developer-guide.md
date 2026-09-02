# Communication Developer Guide

This guide lists the application settings, environment variables, and operational checks needed for the JustHR Communication module to run smoothly.

## Module Features

The Communication module currently supports:

- Direct employee chat.
- Group chat.
- Chat file attachments.
- HR broadcasts.
- HR broadcast file attachments.
- Broadcast read receipts and comments.
- Online presence through WebSocket/STOMP.
- HR/Admin meeting creation through Zoom, Microsoft Teams, and Google Meet.
- Targeted meeting invites through direct chat and email.
- Communication files surfaced in the Document Library.

## Core Runtime Requirements

The application must have:

- A working database connection.
- OAuth login configured through Keycloak.
- WebSocket/STOMP dependencies available.
- File-system write access for communication attachments.
- Email configuration if meeting invite emails should be sent.
- Meeting provider credentials if HR/Admin users should create external meetings.

## Database

The application currently uses Hibernate DDL update:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

Communication-related tables are created or updated automatically in development. For production, consider replacing automatic DDL updates with explicit migrations.

Important tables include:

- `communication_conversations`
- `communication_chat_messages`
- `communication_chat_message_attachments`
- `communication_chat_groups`
- `communication_group_chat_messages`
- `communication_group_chat_message_attachments`
- `communication_hr_broadcasts`
- `communication_hr_broadcast_attachments`
- `communication_hr_broadcast_receipts`
- `communication_hr_meetings`
- `communication_hr_meeting_participants`

## File Attachments

Communication attachments are stored on disk.

Default storage path:

```text
${user.home}/just-hr/communication-attachments
```

Override it with:

```text
app.communication.storage-path=/path/to/communication-attachments
```

In production, set this to a persistent writable volume. Do not point it at a temporary filesystem.

Attachment limits:

- Maximum attachments per message or broadcast: 5
- Maximum file size: 20 MB

Allowed content types:

- `application/pdf`
- `image/png`
- `image/jpeg`
- `text/plain`
- `application/msword`
- `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- `application/vnd.ms-excel`
- `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`

Spring multipart settings should align with the attachment limits:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 20MB
```

If users upload several 20 MB files at once, increase `max-request-size` accordingly.

## WebSocket Configuration

The communication UI depends on WebSocket/STOMP connectivity.

Relevant endpoint:

```text
/ws
```

Used destinations include:

- `/user/queue/messages`
- `/user/queue/broadcasts`
- `/user/queue/group-notifications`
- `/topic/hr-broadcasts`
- `/topic/presence`
- `/topic/chat-groups`
- `/topic/chat-groups/{groupId}/messages`

Reverse proxies must allow WebSocket upgrade headers. If running behind a load balancer, configure sticky sessions or externalize messaging if multiple app instances are used.

## Meeting Provider Credentials

All meeting providers are disabled by default. Enable only the providers that are configured.

### Defaults

```yaml
meeting:
  defaults:
    provider: ${MEETING_DEFAULT_PROVIDER:MICROSOFT_TEAMS}
    duration: ${MEETING_DEFAULT_DURATION:30m}
    timezone: ${MEETING_DEFAULT_TIMEZONE:Africa/Lagos}
    notify-participants: ${MEETING_NOTIFY_PARTICIPANTS:true}
    allow-instant-meeting: ${MEETING_ALLOW_INSTANT:true}
```

Environment variables:

```text
MEETING_DEFAULT_PROVIDER=MICROSOFT_TEAMS
MEETING_DEFAULT_DURATION=30m
MEETING_DEFAULT_TIMEZONE=Africa/Lagos
MEETING_NOTIFY_PARTICIPANTS=true
MEETING_ALLOW_INSTANT=true
```

### Zoom

```text
ZOOM_MEETING_ENABLED=true
ZOOM_ACCOUNT_ID=
ZOOM_CLIENT_ID=
ZOOM_CLIENT_SECRET=
ZOOM_DEFAULT_HOST_USER_ID=me
ZOOM_TOKEN_URL=https://zoom.us/oauth/token
ZOOM_API_BASE_URL=https://api.zoom.us/v2
```

The implementation uses Zoom Server-to-Server OAuth and creates meetings through:

```text
POST /users/{userId}/meetings
```

### Microsoft Teams

```text
MS_TEAMS_MEETING_ENABLED=true
MS_TENANT_ID=
MS_CLIENT_ID=
MS_CLIENT_SECRET=
MS_DEFAULT_ORGANIZER_USER_ID=
MS_GRAPH_BASE_URL=https://graph.microsoft.com/v1.0
```

The implementation uses Microsoft Graph client credentials and creates meetings through:

```text
POST /users/{userId}/onlineMeetings
```

The Microsoft app must have the required Graph permissions and an application access policy for the organizer user.

### Google Meet

```text
GOOGLE_MEET_ENABLED=true
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_MEET_REFRESH_TOKEN=
GOOGLE_MEET_REDIRECT_URI=
GOOGLE_DEFAULT_ORGANIZER_EMAIL=
GOOGLE_TOKEN_URL=https://oauth2.googleapis.com/token
GOOGLE_MEET_BASE_URL=https://meet.googleapis.com/v2
```

The implementation uses a configured OAuth refresh token and creates Meet spaces through:

```text
POST /spaces
```

## Meeting Invite Notifications

Meeting invites are targeted to selected participants only.

Configure notification behavior with:

```text
MEETING_SYSTEM_SENDER_EMPLOYEE_ID=
MEETING_SEND_CHAT_INVITES=true
MEETING_SEND_EMAIL_INVITES=true
```

`MEETING_SYSTEM_SENDER_EMPLOYEE_ID` must point to an active employee record. This employee appears as the sender of direct chat meeting invites.

If this value is missing, meetings can still be created, but chat invite delivery will be recorded as failed.

## Email Configuration

Meeting invite emails use `ResendService`.

Required environment variables:

```text
RESEND_API_KEY=
RESEND_FROM=
```

If these values are missing, meetings can still be created, but email invite delivery will be recorded as failed.

## Access Rules

HR/Admin users can:

- Create broadcasts.
- Attach files to broadcasts.
- Create chat groups across departments.
- Create Zoom, Teams, and Google Meet meetings.
- View all meeting records.
- Access Document Library communication files.

Heads of Department can:

- Create groups for their department.

Employees can:

- Send direct chat messages.
- Send group messages in groups they belong to.
- Receive targeted meeting invites when selected as participants.
- View meetings they were invited to through `/employee/communication/meetings`.

## API Endpoints

### Employee Communication

```text
GET  /employee/communication
GET  /employee/communication/employees
GET  /employee/communication/conversations
GET  /employee/communication/conversations/{conversationId}/messages
POST /employee/communication/messages
GET  /employee/communication/broadcasts
GET  /employee/communication/broadcasts/{broadcastId}/comments
POST /employee/communication/broadcasts/{broadcastId}/read
POST /employee/communication/broadcasts/{broadcastId}/comments
GET  /employee/communication/groups
POST /employee/communication/groups
GET  /employee/communication/groups/{groupId}/messages
POST /employee/communication/groups/{groupId}/messages
GET  /employee/communication/meetings
```

### HR/Admin Communication

```text
GET  /communication
GET  /communication/broadcasts
POST /communication/broadcasts
GET  /communication/groups
POST /communication/groups
GET  /communication/meetings
POST /communication/meetings
GET  /communication/meetings/{meetingId}
POST /communication/meetings/{meetingId}/cancel
GET  /api/communication/meeting-integrations/status
```

### Document Library

Communication files are available to HR/Admin users through Document Library source types:

```text
DIRECT_CHAT_ATTACHMENT
GROUP_CHAT_ATTACHMENT
BROADCAST_ATTACHMENT
```

Dedicated Document Library download endpoints are used so HR/Admin access does not depend on chat membership:

```text
GET /api/documents/library/direct-chat/{attachmentId}
GET /api/documents/library/direct-chat/{attachmentId}/view
GET /api/documents/library/group-chat/{attachmentId}
GET /api/documents/library/group-chat/{attachmentId}/view
GET /api/documents/library/broadcast/{attachmentId}
GET /api/documents/library/broadcast/{attachmentId}/view
```

## Local Verification

Compile:

```powershell
.\mvnw.cmd -q -DskipTests compile
```

Test compile:

```powershell
.\mvnw.cmd -q -DskipTests test-compile
```

Focused Document Library test:

```powershell
.\mvnw.cmd -q -Dtest=DocumentLibraryServiceTest test
```

Manual smoke checks:

- Sign in as HR/Admin.
- Open `/communication`.
- Send a broadcast with and without attachments.
- Create a direct chat message with an attachment.
- Create a group chat message with an attachment.
- Confirm attachments appear in `/documents/library`.
- Configure one meeting provider and create a meeting.
- Select participants and keep `Notify selected participants` checked.
- Confirm only selected participants receive direct chat invites.
- Confirm selected participants with email addresses receive email invites.
- Confirm non-selected employees do not receive meeting invites.

## Common Issues

If attachment uploads fail:

- Confirm the file type is allowed.
- Confirm file size is within the configured limit.
- Confirm `app.communication.storage-path` is writable.
- Confirm multipart request limits are high enough.

If WebSocket messages do not arrive:

- Confirm `/ws` is reachable.
- Confirm proxy WebSocket upgrade headers are enabled.
- Check browser console errors.
- Refresh after session expiry.

If meeting creation fails:

- Confirm the selected provider is enabled.
- Confirm all provider credentials are set.
- Confirm provider API permissions are granted.
- For Microsoft Teams, confirm the application access policy allows the organizer account.

If meeting chat invites fail:

- Confirm `MEETING_SYSTEM_SENDER_EMPLOYEE_ID` is set.
- Confirm the sender employee is active.
- Confirm the sender is not the same employee as the participant.

If meeting email invites fail:

- Confirm `RESEND_API_KEY` is set.
- Confirm `RESEND_FROM` is set.
- Confirm the participant has an email address.
