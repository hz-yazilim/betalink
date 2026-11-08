# Betalink 0.3.0

## New in 0.3.0
- Daily campaign attendance processing with a one-day grace period.
- In-app absence reminders; two consecutive missed days release the active tester slot.
- Automatic promotion of the oldest eligible backup tester into a vacated campaign slot.
- Campaign owners can manually run an attendance review and inspect the campaign report.
- Tester reliability metrics are visible on the user's profile and to developers reviewing campaign applicants.
- Group capacity and 14-day progress bars.
- Group owners can remove an approved member; the oldest pending applicant is promoted into the free slot and notified.
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
