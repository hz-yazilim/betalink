# Betalink 0.3.0

## New in 0.3.0
- Daily campaign and group attendance processing with a one-day grace period.
- In-app absence reminders; two consecutive missed days release the active tester slot.
- Automatic promotion of the oldest eligible backup tester into a vacated campaign or group slot.
- Campaign owners can manually run an attendance review and inspect the campaign report.
- Tester reliability metrics are visible on the user's profile and to developers reviewing campaign applicants.
- Group capacity and 14-day progress bars.
- Group owners can remove an approved member; the oldest pending applicant is promoted into the free slot and notified. Two consecutive missed group check-ins also release the slot and call the next pending member.
- Developer campaign report: active/backup/dropped tester counts, check-ins, feedback count, average rating, bug reports and suggestions.
- Automatic in-app feedback summary when the 14-day campaign tracking clock completes.
- Android versionName 0.3.0 and versionCode 2.

## Notification scope
FCM is intentionally deferred. Attendance reminders, backup invitations, group moderation messages and completion summaries are written to Betalink's in-app notification inbox.

## Operational notes
- Daily absence processing is scheduled through pg_cron at 09:15 UTC.
- Absence processing is based on the previous UTC day, so users are not penalized before the day ends.
- Backup promotion is FIFO among applicants explicitly placed on the backup/waitlist queue.
- A daily check-in is a self-reported confirmation, not independent proof of Play Store installation or use.

## People and group invitations (10 November 2026)
- Added a dedicated Kişiler tab with a paginated directory of registered Betalink users.
- Added a profile setting to opt in or out of receiving group invitations.
- Group owners can invite eligible users with a custom note; invitees can accept or decline from the Kişiler inbox.
- Invitation creation, capacity checks, duplicate prevention, and acceptance are enforced by server-side database functions.
- Invitation events generate in-app notifications for both recipients and group owners.
- Share sheet text includes the group name, description, optional note, Betalink purpose, and Play Store link.
- The owner-only admin dashboard now shows account email addresses instead of internal user IDs; email addresses are never exposed in the public people directory.
