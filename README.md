# kNupdate

kNupdate is a private, temporary activity ledger for people who are already close to one another.

It is intentionally not a social network. There are no public profiles, discovery, followers, likes, comments, messages, media uploads, external sharing, recommendations, analytics, or activity history.

An activity is a controlled text record such as:

> Aniruddh completed an activity — Run · 5 km · 9:30 AM–10:00 AM

Activities are visible only to current mutual connections and are permanently deleted after the global 72-hour expiry window.

## Repository layout

- `app/` — Android client (`com.knupdate.app`), Kotlin and Jetpack Compose.
- `backend/` — planned Kotlin/Ktor API and deletion worker.
- `infra/` — planned deployment configuration and environment templates.
- `docs/` — API, privacy/deletion, and threat-model documentation.
- `idea.md` — original product and technical build brief.
- `roadmap.md` — implementation order and acceptance criteria.

## Current status

The Android project is currently a Compose starter. Implementation follows the milestones in [roadmap.md](roadmap.md), beginning with the repository foundation and then the vertical core flow:

`sign in → trusted connection → structured activity → connection feed`

## Android development

Prerequisites:

- Android Studio with the required Android SDK and JDK.
- An Android device or emulator.

Build and test the current client:

```sh
./gradlew test
./gradlew assembleDebug
```

The production build will be a manually installed, signed APK. It will not be submitted to Google Play. The release signing key must be preserved securely because future APK updates must use the same key.

## Backend development

The backend is planned as a small Kotlin/Ktor service backed by PostgreSQL. It will be added after the API and privacy contracts in `docs/` are reviewed against the product constitution.

Production configuration must be provided through environment variables or a secret manager. Never commit real OTP credentials, FCM credentials, phone numbers, database credentials, signing keys, APK user data, or database backups.

See [infra/.env.example](infra/.env.example) for the names of planned configuration values.

## Privacy and deletion

The backend is the source of truth for access control and expiry. The client hides records once their expiry time passes, while a scheduled backend worker permanently deletes expired activities and dependent delivery data at least every 15 minutes.

There is no archive, restore, export, history, soft-delete table, retained activity log, or activity-specific public URL. See [docs/privacy-and-deletion.md](docs/privacy-and-deletion.md).

## Release checklist

- [ ] Run the complete test suite from a clean checkout.
- [ ] Build a signed release APK.
- [ ] Verify the APK installs on a supported Android device.
- [ ] Verify a later APK signed with the same key updates an existing installation.
- [ ] Publish the APK checksum alongside the private or unlisted release.
- [ ] Confirm production secrets and signing material are outside the repository.
- [ ] Verify expiry deletion directly in the production database and relevant infrastructure stores.
- [ ] Confirm the non-goals below are still true.

## Permanent non-goals

Do not add public accounts, user search, contact import, one-way follows, follower counts, likes, comments, replies, reposts, saves, DMs, media, links, captions, tags, location, third-party activity proofs, GPS, rankings, recommendations, analytics, ads, monetisation, social sharing, archive, history, statistics, streaks, challenges, leaderboards, web, iOS, or growth prompts.

The app's restraint is part of its functionality. Security patches, compatibility fixes, and narrowly scoped correctness fixes are acceptable; turning kNupdate into a general social product is not.

## License

MIT. See [LICENSE](LICENSE).
