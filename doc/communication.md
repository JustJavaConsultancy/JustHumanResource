# Communication User Guide

This guide explains how to use the Communication module in JustHR for direct chats, group chats, file attachments, HR broadcasts, broadcast comments, online presence, mobile messaging, and HR/Admin meeting creation.

## Who Can Use Communication

Employees can:

- Chat directly with another employee.
- Send and receive supported file attachments in direct and group chats.
- View who is online in real time.
- Receive HR broadcasts while online.
- Receive missed HR broadcasts immediately after coming back online.
- Comment on HR broadcasts.
- Join and participate in chat groups they belong to.
- Receive targeted meeting invites in chat when selected as a participant.

HR and admin users can:

- Send HR broadcasts to employees.
- Attach supported files to HR broadcasts.
- View broadcast delivery, read, and comment counts.
- See live employee comments on broadcasts.
- Create chat groups across departments.
- Start or schedule Zoom, Microsoft Teams, or Google Meet meetings when the provider is configured.
- Notify selected meeting participants by direct chat and email.

Heads of Department can:

- Create chat groups for employees in their own department.
- Send and receive messages in groups they belong to.

## Accessing Communication

### Employee Desktop

1. Sign in to JustHR.
2. Open the employee portal.
3. Select **Communication** from the sidebar.

The page contains:

- **People** for direct employee chats.
- **Groups** for group conversations.
- **HR Broadcasts** for company or HR announcements.

### Employee Mobile

1. Sign in through the mobile employee portal.
2. Tap **Chat** in the bottom navigation.

The mobile page contains:

- **People**
- **Groups**
- **Chat**
- **HR Updates**

### HR Desktop

1. Sign in as HR or admin.
2. Select **Communication** from the sidebar.

The HR console contains:

- Broadcast composer.
- Meeting creator.
- Broadcast history.
- Online employee list.
- Chat group creation.
- Group list.
- Meeting list.
- Broadcast comment activity.

## Direct Employee Chat

### Start A Direct Chat

1. Open **Communication**.
2. In **People**, search for an employee by name or department.
3. Select the employee.
4. Type your message in the message box.
5. Press the send button.

The chat opens immediately. If no previous conversation exists, JustHR creates one after the first message is sent.

### Recent Chats

Recently active chats automatically move to the top of the People list.

Each contact row can show:

- Employee name.
- Online/offline indicator.
- Department.
- Latest message preview.
- Latest message time.
- Unread message count.

### Online Status

A green dot means the employee is currently online.

The online count updates live as employees connect or disconnect.

### Unread Messages

When a new message arrives from an employee whose chat is not currently open, an unread badge appears beside their name.

Opening that chat clears the badge on your screen.

### Send File Attachments

1. Open a direct chat.
2. Click the attach button.
3. Select one or more supported files.
4. Optionally type a message.
5. Press send.

Supported attachment types include PDF, PNG, JPEG, text files, Word documents, and Excel files.

Each message can include up to 5 attachments. Each file can be up to 20 MB.

## Group Chat

### Who Can Create Groups

HR/admin users can create groups with employees from any department.

Heads of Department can create groups only with employees from their own department.

Regular employees cannot create groups unless they are also HR/admin or HOD.

### Create A Group On Desktop

1. Open **Communication**.
2. In the left panel, select **Groups**.
3. Click **Group**.
4. Enter a group name.
5. Optionally enter a description.
6. Search and select members.
7. Click **Create**.

After creation, the group appears in the Groups list.

### Create A Group On Mobile

1. Tap **Chat** in the mobile bottom navigation.
2. Open the **Groups** tab.
3. Tap **Create**.
4. Enter a group name and optional description.
5. Search and select members.
6. Tap **Create Group**.

### Send A Group Message

1. Open **Groups**.
2. Select a group.
3. Type your message.
4. Press send.

Messages appear in real time for group members who are connected.

Group messages also support the same file attachment rules as direct chats.

### Group List Updates

When a group message is sent, the group moves up in the list and shows the latest message preview.

Group updates are sent only to members of the group.

## HR Broadcasts

HR broadcasts are announcements sent by HR/admin users to all employees.

### Send A Broadcast As HR

1. Open **Communication** from the HR sidebar.
2. Go to **New HR Broadcast**.
3. Enter a broadcast title.
4. Enter the announcement message.
5. Optionally click **Attach** and select supported files.
6. Click **Send Broadcast**.

Online employees receive the broadcast immediately.

Offline employees receive the broadcast when they next come online.

Broadcast attachments use the same supported file types and limits as chat attachments.

### View Broadcast History

In the HR Communication page, the broadcast history shows:

- Broadcast title.
- Message summary.
- Sent time.
- Delivered count.
- Read count.
- Comment count.

The counts update in real time.

## Employee HR Updates

### Read A Broadcast

Desktop:

1. Open **Communication**.
2. Select a broadcast from **HR Broadcasts**.
3. Read the broadcast details.

Mobile:

1. Tap **Chat**.
2. Open **HR Updates**.
3. Select an update.

When a broadcast is opened, it is marked as read.

### Comment On A Broadcast

1. Open the broadcast.
2. Type your comment in the comment box.
3. Press send.

Your comment appears immediately in the broadcast comment thread.

The comment count updates live for employees and HR users.

## HR/Admin Meetings

HR and admin users can create meetings from the HR Communication page when at least one meeting provider is configured.

Supported providers are:

- Zoom
- Microsoft Teams
- Google Meet

### Start Or Schedule A Meeting

1. Open **Communication** from the HR sidebar.
2. Go to **Start or Schedule Meeting**.
3. Select the meeting provider.
4. Enter the meeting subject.
5. Optionally enter an agenda.
6. Leave **Start now** checked for an instant meeting, or uncheck it and enter a start time.
7. Optionally enter an end time. If no end time is supplied, the default duration is used.
8. Select the employee participants.
9. Leave **Notify selected participants** checked if invites should be sent.
10. Click **Create Meeting**.

After the provider creates the meeting, JustHR saves the meeting and shows it in the HR meeting list with a join link.

### Meeting Invite Delivery

Meeting invites are targeted to selected participants only.

When **Notify selected participants** is checked:

- Each selected participant receives a direct chat message with the meeting details and join link.
- Each selected participant with an email address receives an email invite.
- The invite is not sent as a general HR broadcast.
- Employees who were not selected as participants do not receive the meeting invite.

The HR meeting list shows how many selected participants received chat and email notifications.

### Meeting Notification Requirements

Chat invites require a configured system sender employee:

```text
MEETING_SYSTEM_SENDER_EMPLOYEE_ID
```

This must point to an active employee account that should appear as the sender of meeting invite chat messages.

Email invites require Resend email configuration:

```text
RESEND_API_KEY
RESEND_FROM
```

If email is not configured, the meeting can still be created, but email notification status is recorded as failed or skipped for affected participants.

### Meeting Provider Configuration

Provider credentials are supplied through environment variables.

Zoom:

```text
ZOOM_MEETING_ENABLED
ZOOM_ACCOUNT_ID
ZOOM_CLIENT_ID
ZOOM_CLIENT_SECRET
ZOOM_DEFAULT_HOST_USER_ID
```

Microsoft Teams:

```text
MS_TEAMS_MEETING_ENABLED
MS_TENANT_ID
MS_CLIENT_ID
MS_CLIENT_SECRET
MS_DEFAULT_ORGANIZER_USER_ID
```

Google Meet:

```text
GOOGLE_MEET_ENABLED
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
GOOGLE_MEET_REFRESH_TOKEN
GOOGLE_DEFAULT_ORGANIZER_EMAIL
```

If a provider is not enabled or is missing required credentials, meeting creation for that provider fails with a provider configuration error.

## Offline Broadcast Delivery

If HR sends a broadcast while you are offline:

1. The broadcast is saved by JustHR.
2. When you return online and open the app, JustHR delivers the missed broadcast.
3. The broadcast appears in your HR Updates or HR Broadcasts list.

This means employees do not need to manually refresh to receive missed HR broadcasts after reconnecting.

## Mobile Usage Tips

Use the mobile tabs as follows:

- **People**: find colleagues and start direct chats.
- **Groups**: view or create department/group conversations.
- **Chat**: read and send messages in the selected direct chat or group chat.
- **HR Updates**: read HR broadcasts and comment on them.

The message box stays at the bottom of the chat view so it is easy to continue a conversation on small screens.

## Good Communication Practices

- Use direct chat for one-to-one conversations.
- Use groups for department or project discussions.
- Use HR broadcasts for official announcements.
- Use meeting invites for time-sensitive conversations that require selected employees to join a live call.
- Keep comments on broadcasts relevant to the announcement.
- Avoid sharing sensitive payroll, disciplinary, or personal data in group chats unless the group is intended for that purpose.

## Troubleshooting

If messages are not appearing in real time:

- Check that your internet connection is active.
- Refresh the page if the WebSocket connection was interrupted.
- Sign out and sign back in if your session expired.

If you cannot create a group:

- Confirm you are HR, admin, or a Head of Department.
- If you are a Head of Department, confirm the selected members are in your department.
- Confirm your employee profile has a department assigned.

If an employee does not appear in the member list:

- The employee may be inactive.
- The employee may be restricted from visibility.
- The employee may be outside your department if you are creating the group as HOD.

If a broadcast does not show immediately:

- Confirm you are online.
- Reopen the Communication page.
- If the broadcast was sent while you were offline, it should appear after reconnecting.

If a meeting cannot be created:

- Confirm the selected provider is enabled and fully configured.
- Confirm the provider credentials have the required API permissions.
- For Microsoft Teams, confirm the app has the required Microsoft Graph permissions and application access policy.
- Confirm the selected participants are active and visible employees.

If a participant does not receive a meeting invite:

- Confirm the employee was selected as a participant.
- Confirm `MEETING_SYSTEM_SENDER_EMPLOYEE_ID` is configured for chat invites.
- Confirm the employee has a valid email address for email invites.
- Confirm `RESEND_API_KEY` and `RESEND_FROM` are configured for email delivery.
