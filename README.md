# Betalink

Betalink is a native Android community platform connecting app developers who need closed-test participants with testers who want to participate in genuine app testing campaigns.

## Product direction
- Native Android application (Kotlin + Jetpack Compose); no WebView wrapper.
- Supabase Auth, PostgreSQL, row-level security, and Edge Functions.
- Turkish-first launch.
- One account can act as both tester and developer.
- Each campaign has its own tester threshold and independent 14-day clock.
- Campaigns are filtered by the tester's Android release version.

## Implemented Android flows
- Email/password sign-up, sign-in, session restoration, and sign-out.
- Encrypted session storage using Android Keystore.
- Automatic tester device/Android version profile sync.
- Campaign discovery and application.
- Developer campaign creation and applicant approval/rejection/waitlist promotion.
- Tester Play opt-in self-attestation and daily check-ins.
- Independent campaign progress display and 12 active-tester threshold.
- Starter tasks, task submission, developer approval/change requests, and tester resubmission.
- Tester feedback, developer replies, and report/block actions.
- Public/private tester groups, joining, and owner approval of private-group requests.
- In-app notifications for applications, group requests, task reviews, and feedback replies.
- Reputation points from reviewed tasks and dropouts.
- Profile reputation score.
- Account deletion from inside the app.
- Hidden administrator panel, visible only after server-side admin authorization.

## Google Play testing integrity
Betalink helps users organize and document genuine testing participation. It does not access Google Play Console private data and cannot guarantee production access approval. Users must join tests with their own Google accounts, install and genuinely use the apps, and provide honest feedback. Never request Google passwords or Play Console credentials.

An in-app Play opt-in confirmation is a self-report, not proof of Google Play Console enrollment. The campaign clock starts only after the required number of testers have been accepted and self-attested. The server pauses the clock if the active tester threshold falls below 12. The progress endpoint reports eligibility after 14 days only while the threshold remains satisfied. Developers must still verify actual participation in Play Console.

## Android project
Open this repository in Android Studio. The native application is in app/.

### Build and tests
GitHub Actions runs unit tests and builds a debug APK on pushes to main. A successful run uploads the betalink-debug-apk artifact:
https://github.com/hz-yazilim/betalink/actions

The debug APK is for installation/testing. A signed release build, device-level QA, privacy-policy URL, Play Console data-deletion URL, and store listing are still required before publication.

## Backend
Betalink uses its own Supabase project. Do not reuse Tivarto or any other project's credentials or database.

Database migrations are stored in supabase/migrations/. They define the initial schema, RLS policies, profile creation, per-campaign tracking, task workflows, group moderation, notifications, reputation, and policy/index hardening.

Deployed Edge Functions:
- delete-account: deletes the authenticated user's account server-side.
- admin-api: admin-only dashboard, report moderation, account suspension, and announcements. It checks the platform_admins table server-side.

Never commit Supabase service-role keys. The Android client contains only the project's publishable key. Privileged actions must remain server-side.

## Security notes
- All public tables have RLS enabled.
- Admin tables and the raw campaign-tracking table intentionally have no direct client policies; access is through trusted server-side functions.
- Private privileged RPC implementations live in a non-exposed database schema.
- Security advisor warnings for the admin audit table, platform-admin table, and raw tracking table are intentional server-only restrictions.
- Account deletion cascades through profile-owned data. Final release QA must verify deletion, session revocation, and any future uploaded-file cleanup.

## Current limitations before public release
- The initial administrator account must be explicitly provisioned in platform_admins; no public self-claim is allowed.
- Push notifications (FCM) are not configured; current notifications are in-app.
- End-to-end tests with real tester/developer accounts and real Play Console campaigns remain necessary.
- Admin identity, support contact, privacy policy, and external account-deletion URL must be finalized with the owner before Play Store submission.
- The app has not yet completed real-device QA or a signed release build.
