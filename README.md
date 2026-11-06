# Betalink

Betalink is an Android-first community platform that connects app developers who need closed-test participants with testers who want to participate in genuine app testing campaigns.

## Product direction
- Native Android application (Kotlin + Jetpack Compose); no WebView wrapper.
- Supabase for authentication, PostgreSQL data, and file storage.
- Firebase Cloud Messaging for push notifications (planned).
- Turkish-first launch, with an architecture ready for localization.
- Tester and developer capabilities can coexist on one account.
- Each campaign tracks its own tester participation and independent 14-day period.

## Google Play testing integrity
Betalink helps users organize and document genuine testing participation. It does not access Google Play Console private data and cannot guarantee that Google will approve production access. Users must join tests with their own Google accounts, install and genuinely use the apps, and provide honest feedback. Never request Google passwords or Play Console credentials.

A tester's in-app daily confirmation is a self-report, not proof of Google Play opt-in status. The campaign clock and completion decision must be controlled by trusted backend logic. A campaign must not be marked complete merely because 14 calendar days elapsed; the required tester threshold must be maintained and the developer must still follow Google Play Console requirements.

## MVP modules
Authentication and profiles, tester/developer roles, developer campaigns, tester discovery and matching, tester groups, campaign membership and waitlists, task checklists, feedback and replies, notifications, reputation, reports/blocking, support, admin moderation, and account/data deletion.

## Android project
Open this repository in Android Studio. The native app lives in `app/`.

### Build
The GitHub Actions workflow builds a debug APK on every push to `main`. When a build succeeds, download the `betalink-debug-apk` artifact from the workflow run:
[GitHub Actions](https://github.com/hz-yazilim/betalink/actions)

## Backend setup
The initial schema is in `supabase/migrations/202611060001_initial_schema.sql`. Apply it only after a dedicated Betalink Supabase project is provisioned and the migration has been reviewed. Do not reuse Tivarto or Kalpkalp credentials or databases.

Never commit Supabase service-role keys. The Android client may use only the project's publishable/anon key. Privileged tasks (account deletion, moderation, membership approvals, campaign clock control, reputation updates, and notification dispatch) must run server-side.

## Account and data deletion
Deleting an account must be performed by trusted server-side code using the authenticated user identity. The database uses cascading foreign keys so profile-owned data is removed with the account, while references that must remain for moderation/audit are designed separately. The production deletion flow must also revoke sessions and handle stored files before release.

## Current status
- Native Android UI shell and five-tab navigation are in place.
- Initial PostgreSQL schema and row-level security policies are drafted.
- CI workflow is configured to build and upload a debug APK.
- Supabase project creation is currently blocked by the free-plan limit of two active projects in the selected organization.
- Authentication, live data screens, server-side workflows, push notifications, and end-to-end tests are not yet connected.
