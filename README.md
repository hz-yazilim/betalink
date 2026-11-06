# Betalink

Betalink is an Android-first community platform that connects app developers who need closed-test participants with testers who want to participate in real app testing campaigns.

## Product direction
- Native Android application (Kotlin + Jetpack Compose); no WebView wrapper.
- Supabase for authentication, PostgreSQL data, and file storage.
- Firebase Cloud Messaging for push notifications.
- Turkish-first launch, with an architecture ready for localization.
- Tester and developer capabilities can coexist on one account.
- Each campaign tracks its own tester participation and 14-day period independently.

## Google Play testing integrity
Betalink helps users organize and document genuine testing participation. It does not access Google Play Console private data and cannot guarantee that Google will approve production access. Users must join tests with their own Google accounts, install and genuinely use the apps, and provide honest feedback. Never request Google passwords or Play Console credentials.

## Planned modules
Authentication and profiles, developer campaigns, tester discovery and matching, tester groups, campaign membership and waitlists, task checklists, feedback and replies, notifications, reputation, reports/blocking, support, admin moderation, and account/data deletion.

## Android project
Open this repository in Android Studio. The Android app lives in `app/`.

## Backend setup
Supabase project configuration will be added after a dedicated Betalink project is provisioned. Do not reuse Tivarto or Kalpkalp credentials or databases. Keep secrets out of Git; use local Gradle properties or secure CI secrets.

## Current status
Initial native Android scaffold. Features will be delivered incrementally with database migrations, Row Level Security policies, and tests.
