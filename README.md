# Betalink

Betalink is a native Android community platform connecting app developers who need closed-test participants with testers who want to participate in genuine app testing campaigns.

## Product direction
- Native Android application (Kotlin + Jetpack Compose); no WebView wrapper.
- Supabase Auth, PostgreSQL, row-level security, and Edge Functions.
- Turkish-first launch.
- One account can act as both tester and developer.
- Each campaign has its own tester threshold and independent 14-day clock.\n- Each tester group has a separate 15-member threshold and independent 14-day clock.
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
- Public/private tester groups, joining, and owner approval of private-group requests.\n- Group testing starts only when 15 approved tester members are present; a shared 14-day clock is recorded server-side. If the approved count falls below 15 before completion, the clock pauses and restarts when the group is full again.\n- Group participants can explicitly opt in to sharing their Play test email; only approved members can view addresses of members who consented.
- In-app notifications for applications, group requests, task reviews, feedback replies, and group test start/pause/completion.\n- Android 13+ notification permission explanation and runtime request, with a notification channel.
- Reputation points from reviewed tasks and dropouts.
- Profile reputation score.
- Account deletion from inside the app.
- Hidden administrator panel, visible only when the verified `hz.yazilimm@gmail.com` account passes server-side email allowlisting.

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
- The administrator allowlist is pinned server-side to `hz.yazilimm@gmail.com`; that exact email must register/sign in to see the admin panel.
- Remote push delivery (FCM) is not configured yet. The Android permission prompt and notification channel are implemented, but Firebase project configuration and server credentials are still required for notifications while the app is closed.
- End-to-end tests with real tester/developer accounts and real Play Console campaigns remain necessary.
- Admin identity, support contact, privacy policy, and external account-deletion URL must be finalized with the owner before Play Store submission.
- The app has not yet completed real-device QA or a signed release build.


## BetaPuan economy

- Currency: BetaPuan (BP), virtual and non-transferable; it has no cash value.
- A user must complete at least two distinct 14-day campaign tests before creating campaigns, creating tester groups, or joining groups.
- A completed campaign test awards 25 BP once per user/campaign.\n- A completed 15-tester group test awards 25 BP once to each approved tester in that group; the group creator/leader is not counted as one of the 15 testers.
- Creating a developer campaign costs 50 BP.
- Creating a tester group costs 30 BP.
- Joining a public tester group costs 5 BP. Private-group join requests are free after the two-test eligibility gate.
- Wallet balance and spending are enforced by private-schema database functions; the Android client cannot directly change balances.
- Campaign rewards are issued when the campaign tracking endpoint finalizes a continuous 14-day campaign with the required tester count. A client-side check-in is a self-report and is not independent Google Play Console verification.
