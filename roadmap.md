# kNupdate Roadmap

## 1. Final goal

Build and release a small, Android-only application that lets a trusted circle quietly record that a structured activity happened.

The finished product is a **private, mutual, temporary activity ledger**:

1. A person signs in and sets a display name.
2. They connect only with people who are already close to them and who pass the mutual-connection rule.
3. They submit an activity from a fixed list, with a start time, end time, and optional controlled quantity.
4. The app renders the activity as one plain-text sentence and shows it to current mutual connections.
5. Each connection receives one ordinary notification.
6. Nobody can like, comment, reply, message, reshare, search, follow, rank, or publicly discover anyone.
7. The activity disappears after the global 72-hour expiry window and is hard-deleted from the server and local device data.

The product succeeds when it works reliably for the owner and a small close circle. It does not need growth, engagement, monetisation, a public launch, or an expanding feature set.

## 2. Product constitution

These are guardrails for every implementation decision. A feature that weakens them is not a roadmap item, even if it would make the app easier to use or more popular.

- **Private by construction:** no public profiles, public URLs, discovery, explore, reach, or follower counts.
- **Mutual connections only:** there is one symmetric connection state; no one-way follow.
- **Trusted graph only:** no global search, suggested people, contact import, or open invite mechanism.
- **Structured text only:** activities come from a controlled taxonomy; there are no captions, free-form titles, links, images, videos, audio, location, or attachments.
- **No engagement loop:** no likes, comments, replies, reactions, reposts, saves, views, streaks, rankings, badges, or leaderboards.
- **No messaging:** notifications are one-way activity alerts; there are no DMs, groups, replies, or inboxes.
- **No behavioural optimisation:** no recommendations, ranking, analytics, ad SDKs, experimentation, or retention prompts.
- **Temporary by design:** current activities only; no archive, history, export, restore, backup retention, or yearly recap.
- **No external sharing:** no Android share sheet, copy action, deep link to an activity, or social integration.
- **Finite maintenance:** use boring, understandable technology with a small dependency tree.

## 3. Current starting point

The repository currently contains an Android Compose starter project:

- Kotlin/Jetpack Compose app module at `:app`.
- Application ID already set to `com.knupdate.app`.
- A placeholder `MainActivity` displaying “Hello Android!”.
- Basic unit and instrumentation test scaffolding.
- No backend, authentication, database, network layer, notification integration, product screens, release configuration, or documentation yet.

Before implementation, resolve these project decisions:

- Keep the current `:app` module or rename/restructure it to the brief’s proposed `/android-app` layout. Prefer keeping `:app` initially unless a repository layout change is useful for the backend work.
- Use the brief’s recommended `minSdk 26` rather than the current `minSdk 24` if the target device group allows it.
- Make the global expiry window **72 hours** for the first deployment.
- Use Kotlin + Ktor + PostgreSQL for the backend unless a concrete deployment constraint requires TypeScript/Fastify.
- Keep OTP and FCM behind small interfaces so providers can be replaced without changing product behaviour.

## 4. Delivery order at a glance

| Order | Milestone | Outcome | Depends on |
| --- | --- | --- | --- |
| 0 | Product and technical foundation | Decisions, repository structure, contracts, threat model, local environments | None |
| 1 | Vertical walking skeleton | App launches, authenticates against a local backend, and shows an empty feed | 0 |
| 2 | Identity and onboarding | A real user can sign in, set a display name, and manage their account | 1 |
| 3 | Trusted connections | Seed users can connect; later requests enforce the two-mutual rule | 2 |
| 4 | Activity creation and feed | A user can create a structured activity and connections can view it | 3 |
| 5 | Notifications | Connections receive one activity notification | 4 |
| 6 | Expiry and deletion | Activities disappear at 72 hours and are permanently deleted everywhere | 4 |
| 7 | Privacy, security, and resilience | Access control, rate limits, encrypted local data, and failure handling are verified | 2–6 |
| 8 | Release hardening | Signed APK, checksum, install/update validation, and complete repository docs | 7 |
| 9 | Small-circle pilot and freeze | Owner and invited close circle use the product; only correctness fixes are considered | 8 |

The first usable slice should be: **sign in → establish one trusted connection → create one activity → see it in the other person’s feed**. Notifications and deletion can follow immediately after that loop is working.

## 5. Detailed roadmap

### Milestone 0 — Foundation and scope lock

Goal: make the product and technical boundaries explicit before building screens.

Tasks:

- [x] Add `README.md` with the product purpose, local setup, build commands, release approach, and explicit non-goals.
- [x] Add `LICENSE` and strengthen `.gitignore` for keystores, secrets, `.env` files, APKs, build output, raw phone data, and database dumps.
- [x] Create the intended top-level structure for backend, infrastructure, and docs, while preserving the existing Android module unless there is a strong reason to move it.
- [x] Write the API contract for authentication, profile, devices, connections, and activities.
- [x] Write the privacy/deletion model and threat notes before data is persisted.
- [ ] Decide deployment target, OTP provider, FCM project, PostgreSQL hosting, domain/TLS approach, and secret storage.
- [ ] Define the supported Android version range and set the release package/version policy.
- [x] Define the single global expiry configuration as 72 hours.
- [x] Define the controlled activity taxonomy and quantity rules in source-controlled configuration.
- [x] Define initial error-state and intentionally sparse UI direction in the Android shell and repository docs.

Exit criteria:

- The repository can be set up by one person from a clean checkout.
- The API and database shape are documented.
- There is no unresolved product decision that could accidentally introduce public discovery, engagement, media, messaging, or history.

### Milestone 1 — Vertical walking skeleton

Goal: prove the Android client and backend can communicate without building the full product.

Tasks:

- [x] Replace the placeholder Compose screen with the initial navigation shell: `Feed`, `Add`, and `Profile/Settings`.
- [x] Add a small, explicit app state model for signed-out, onboarding, signed-in, loading, empty, and error states.
- [x] Create the Ktor service with health checking, environment-based configuration, structured error responses, and TLS-ready deployment settings.
- [x] Add a development-only fixed OTP (`9620`) behind an environment guard; production OTP delivery remains unconfigured.
- [ ] Add PostgreSQL migrations and a local development database.
- [x] Add a minimal repository/network layer on Android using Ktor Client.
- [ ] Add token storage using Android Keystore-backed encrypted storage.
- [ ] Establish test fixtures for one user, one connection, and one activity.

Exit criteria:

- A debug APK launches and reaches a real backend.
- The client can handle a successful response, an expired session, a network failure, and an empty feed.
- No product data is logged in the client or server logs.

### Milestone 2 — Identity and onboarding

Goal: allow a real person to enter the product with the minimum identity data.

Backend:

- [x] Implement development `POST /auth/request-otp`.
- [x] Implement development `POST /auth/verify-otp`.
- [ ] Hash phone numbers before database storage; never return them through the API.
- [ ] Issue short-lived access tokens and refresh tokens.
- [ ] Implement `POST /auth/sign-out`.
- [ ] Implement `GET /me` and `PATCH /me/display-name`.
- [ ] Validate display names: 2–40 characters, plain text, not emoji-only.
- [ ] Add OTP rate limiting and abuse-resistant error responses.

Android:

- [x] Build the minimal purpose statement screen.
- [x] Build display-name entry.
- [x] Build phone-number entry and OTP verification.
- [x] Explain the deliberate non-features: no public account, followers, likes, messages, or archive.
- [x] Land a new development-authenticated user on an empty feed.
- [ ] Avoid contact, SMS, storage, camera, microphone, location, fitness, and photo permissions.

Exit criteria:

- A user can sign in, set/change their display name, sign out, and sign back in.
- No phone number, username, profile photo, bio, location, or public identity is exposed.

### Milestone 3 — Trusted mutual connections

Goal: create the private graph without turning it into a social discovery system.

Backend data and rules:

- [x] Add a development-only in-memory connection service so the first end-to-end connection flow can be exercised before PostgreSQL persistence exists.
- [ ] Create `connections` with a canonical pair constraint so a relationship exists once.
- [ ] Create `connection_requests` with pending/accepted/declined status and cleanup expiry.
- [ ] Create `connection_codes` storing only a hash, expiry, and used timestamp.
- [ ] Add the app-owner-controlled seed-user allowlist using hashed phone numbers, kept out of the public UI.
- [ ] Implement `GET /connections`.
- [ ] Implement `POST /connection-codes` for short-lived target-supplied codes.
- [ ] Implement `POST /connection-requests` with server-side code validation and eligibility checks.
- [ ] Enforce the two-existing-mutual-connections rule for non-seed requests.
- [ ] Implement incoming request list, accept, decline, and connection removal.
- [ ] Make removal immediately stop future activity delivery and remove the former connection’s current feed visibility.
- [ ] Ensure request/code replay, self-connection, duplicate connection, and unauthorized access are rejected.

Android:

- [x] Build the private Connections list with names only.
- [x] Build code creation/entry without search, contact import, suggestions, or mutuals browsing.
- [x] Show the target only the requester display name and shared-mutual count.
- [ ] Add explicit accept/decline and remove-connection confirmation states.

Exit criteria:

- Two seed users can form a connection through the bootstrap path.
- A non-seed request cannot bypass the code or the two-mutual rule.
- There is no user directory, search endpoint, follower state, or public profile route.

### Milestone 4 — Structured activity creation and feed

Goal: deliver the core product loop as plain text.

Backend:

- [ ] Create the `activities` table with UUIDs, controlled activity type, controlled quantity fields, server timestamps, timezone, and `expires_at`.
- [ ] Implement `POST /activities`.
- [ ] Implement `GET /feed` for current mutual connections only, ordered strictly by `created_at DESC`.
- [ ] Implement `GET /me/activities` for the author’s unexpired activities.
- [ ] Validate end-after-start, maximum duration of 24 hours, positive quantity, and sensible type-specific maxima.
- [ ] Derive units from quantity type; never accept a typed unit or arbitrary activity title.
- [ ] Calculate expiry on the server from the global 72-hour setting.
- [ ] Do not create edit/update activity endpoints.
- [ ] Authorize every read and write against current mutual-connection state.

Android:

- [ ] Replace the placeholder feed with a chronological, text-only list.
- [ ] Add the empty state: `No current activity updates.`
- [ ] Build `Add activity` with a controlled activity picker.
- [ ] Build start/end date-time pickers.
- [ ] Add optional unit-appropriate quantity controls for distance, pages, minutes, sessions, or none.
- [ ] Show the exact system-rendered sentence before submission.
- [ ] Use the action label `Notify connections`.
- [ ] Show confirmation: `Activity shared with your current connections. It will disappear in 72 hours.`
- [ ] Render only display name, activity, quantity, and time.
- [ ] Make feed rows non-interactive; tapping does not open a detail page or action sheet.
- [ ] Hide locally expired records on launch, foreground resume, and sync.

Initial activity types:

- Run
- Walk
- Cycle
- Swim
- Strength training
- Yoga
- Sport / game
- Study
- Read
- Work
- Cook
- Travel
- Rest
- Other structured activity, backed by a finite server-configured list

Exit criteria:

- A user can create an activity without any free-text field.
- The activity appears in the author’s view and each current connection’s chronological feed.
- There is no media, link, copy, share, edit, caption, tag, or proof integration.

### Milestone 5 — Notifications

Goal: let current connections know an activity exists without creating an engagement or messaging loop.

Tasks:

- [ ] Add `devices` storage with encrypted FCM tokens where feasible.
- [ ] Implement `POST /devices/register` and `DELETE /devices/{id}`.
- [ ] Configure FCM as delivery infrastructure only.
- [ ] On activity creation, send one standard push notification to each current mutual connection.
- [ ] Use the same fixed activity sentence in the notification.
- [ ] Make notification taps open the home feed only.
- [ ] Handle invalid/rotated tokens without exposing delivery status to users.
- [ ] Respect Android notification settings and never repeatedly ask users to re-enable them.

Explicitly exclude Firebase Analytics, Remote Config, Performance Monitoring, Crashlytics, ads, notification experiments, social summaries, quick replies, read receipts, and reactivation prompts.

Exit criteria:

- Each current connection receives at most one notification per created activity.
- A notification cannot open a public URL, activity detail page, reply flow, or share flow.

### Milestone 6 — Expiry and hard deletion

Goal: make impermanence a tested system property, not just a UI convention.

Tasks:

- [ ] Add a scheduled backend deletion worker running at least every 15 minutes.
- [ ] Delete activities where `expires_at <= current server time` from the primary database.
- [ ] Cascade/delete activity-specific notification-delivery records.
- [ ] Remove activity payloads from server cache and queues, if any exist.
- [ ] Ensure no activity payload enters logs, analytics, audit trails, exports, soft-delete tables, or retained backups.
- [ ] Purge expired local Room/encrypted-storage records on app launch, foreground resume, and periodic sync.
- [ ] Never write activity text to Android backup, clipboard, share cache, or permanent file storage.
- [ ] Make feed and profile queries exclude expired activities even before the worker runs.
- [ ] Decide and document timezone/DST behaviour using `timezone_at_creation` and server timestamps.
- [ ] Test deletion with the database directly: after expiry there is no activity row or retained payload.

Exit criteria:

- An activity is hidden client-side as soon as the local device considers it expired.
- The server permanently deletes it on schedule.
- There is no archive, restore, grace period, history, deletion log, retained activity ID, or activity count.

### Milestone 7 — Account controls, privacy, security, and resilience

Goal: close the most important privacy and failure gaps before release.

Account and settings:

- [ ] Build Profile with display name, unexpired own activities, optional current-connection list/count, and Settings.
- [ ] Show the expiry window as read-only.
- [ ] Show app version.
- [ ] Link users to Android notification settings without adding re-enable prompts.
- [ ] Implement `DELETE /account`.
- [ ] Permanently delete the account, device tokens, connections, pending requests, and current activities.
- [ ] Do not provide recovery, archive, export, or grace periods.

Security and privacy:

- [ ] Enforce TLS for every network request.
- [ ] Use UUIDs and avoid sequential identifiers.
- [ ] Authorize every endpoint server-side; test removed-connection access explicitly.
- [ ] Rate-limit OTPs, code redemption, request creation, and activity creation.
- [ ] Keep raw phone numbers out of the application database where possible.
- [ ] Keep tokens out of logs and protect them with Android Keystore-backed storage.
- [ ] Add a concise privacy notice covering what is stored, who can see it, and the exact deletion window.
- [ ] Review all dependencies and remove anything that introduces analytics, ads, social login, tracking, or unnecessary permissions.

Resilience:

- [ ] Define behaviour for offline launch, failed activity submission, duplicate submission, expired auth, clock skew, and server maintenance.
- [ ] Make activity creation idempotent or safely reject retries so one tap cannot create duplicates.
- [ ] Ensure notification failure does not roll back a successfully created activity.
- [ ] Ensure a removed connection cannot receive or fetch newly created activities.

Exit criteria:

- Privacy and authorization tests pass for every data-bearing endpoint.
- Failure states are quiet and understandable, without nudges to increase usage.
- The app requests only the permissions it genuinely needs.

### Milestone 8 — Release hardening

Goal: produce a reproducible private/unlisted APK release that can be updated safely.

Tasks:

- [ ] Configure signed release builds and a stable version name/version code policy.
- [ ] Store the release keystore and password securely outside the repository.
- [ ] Verify a later APK signed with the same key updates an existing installation.
- [ ] Build the release APK and publish its SHA-256 checksum in the private/unlisted GitHub Release or repository.
- [ ] Document manual installation, including Android “Install unknown apps” permission steps.
- [ ] Add an environment-variable template with no real secrets.
- [ ] Document backend deployment, database migrations, OTP/FCM configuration, and scheduled deletion.
- [ ] Document signing-key handling and the consequence of losing the key.
- [ ] Verify backups do not retain activity data contrary to the deletion promise; if provider-level backups cannot meet the promise, change the deployment/retention configuration before release.
- [ ] Run a clean-checkout build and install test on supported Android hardware.

Exit criteria:

- The release APK installs manually.
- A later APK signed by the same key updates the original installation.
- README, API contract, privacy/deletion model, threat notes, checksum, and non-goals are present.

### Milestone 9 — Small-circle pilot and product freeze

Goal: confirm the product works for the intended circle without turning the pilot into a feedback-driven expansion programme.

Tasks:

- [ ] Bootstrap the owner and a very small number of trusted seed users.
- [ ] Test the complete flow on real devices: onboarding, connection, activity, feed, notification, removal, expiry, and account deletion.
- [ ] Verify database deletion and server logs after test activities expire.
- [ ] Record only correctness/security defects that prevent the stated product from working.
- [ ] Fix release-blocking defects.
- [ ] Freeze the feature set and stop accepting convenience features that dilute the product constitution.

Final exit criteria:

- All release acceptance criteria from `idea.md` pass.
- The app is useful to one person and a small close circle.
- No growth, engagement, monetisation, public-discovery, or archive surface has been introduced.

## 6. Feature inventory and ownership

This is the complete intended v1 feature set. It is also a checklist for preventing accidental omissions.

| Area | Required capabilities | Not included |
| --- | --- | --- |
| Onboarding | Purpose statement, display name, phone OTP, non-feature explanation | Tutorial carousel, referral, contact sync, permission fishing |
| Account | Minimal private identity, sign out, permanent account deletion | Username, photo, bio, public profile, social login |
| Connections | Seed bootstrap, target-supplied code, two-mutual eligibility, accept/decline, removal | Search, suggestions, contacts, one-way follows, public graph |
| Activity input | Fixed type, start/end time, optional controlled quantity, preview, notify | Free text, caption, media, links, location, proof integrations |
| Feed | Unexpired current-connection activities, created-time descending, text-only rows, empty state | Ranking, infinite scroll, detail page, interaction controls |
| Notifications | One standard FCM alert per connection, opens feed | DMs, replies, read receipts, social summaries, reactivation prompts |
| Expiry | 72-hour global setting, local hiding, scheduled hard deletion, cascades | Archive, restore, history, export, backup retention |
| Profile/settings | Name, current own activities, connections, read-only expiry, version, Android notification settings, sign out, delete account | Activity totals, streaks, charts, achievements, memories |
| Distribution | Signed APK, checksum, manual install, same-key updates | Google Play, web client, iOS app, public marketing funnel |
| Operations | Small Ktor service, PostgreSQL, migrations, deletion scheduler, TLS, rate limits | Microservices, Kubernetes, data warehouse, analytics stack |

## 7. Suggested implementation order inside each milestone

When a milestone is active, use this order:

1. Database schema and migration.
2. Server validation and authorization.
3. API contract tests.
4. Android repository and state model.
5. Compose screen and interaction states.
6. Android integration tests.
7. Privacy/logging review.
8. Manual device verification.

This keeps the server as the source of truth for privacy rules and prevents the UI from accidentally becoming the only enforcement layer.

## 8. Test plan

### Product-flow tests

- [ ] New user signs in, sets a valid name, and sees an empty feed.
- [ ] Invalid and expired OTPs fail safely and are rate-limited.
- [ ] Two seed users can connect.
- [ ] A non-seed user cannot connect without a target-supplied code and two existing mutuals.
- [ ] Connections are symmetric and duplicate/self-connections are impossible.
- [ ] Connection removal stops future visibility and delivery.
- [ ] Valid structured activities render using the fixed sentence template.
- [ ] Invalid time ranges, quantities, and durations are rejected.
- [ ] Activities appear only for the author and current mutual connections.
- [ ] Feed order is strictly newest-created first.
- [ ] Notification tap opens the feed.
- [ ] Account deletion permanently removes all account-owned records.

### Negative capability tests

Confirm there is no route, control, permission, or stored model for:

- [ ] public profiles or URLs;
- [ ] user/person search or contact import;
- [ ] one-way follows or follower counts;
- [ ] likes, comments, replies, reactions, reshares, saves, or messages;
- [ ] media, links, captions, tags, location, or third-party proof;
- [ ] copy/share/export/history/archive/restore;
- [ ] recommendations, ranking, analytics, ads, experiments, or growth prompts.

### Deletion and privacy tests

- [ ] Client hides expired records using `expires_at` even if the worker has not run.
- [ ] Worker permanently deletes expired primary rows.
- [ ] Dependent delivery/cache/queue data is removed.
- [ ] Expired content cannot be fetched through any endpoint.
- [ ] Database, logs, backups, and exports contain no retained activity payload contrary to policy.
- [ ] Removed connections cannot retrieve new data.
- [ ] Tokens and phone numbers are absent from application logs.

## 9. Definition of done for v1

v1 is done only when:

- The end-to-end private activity loop works on supported Android devices.
- The trusted-connection rules are enforced by the backend.
- Activities are structured, text-only, uneditable, and visible only to current mutual connections.
- Notifications work without creating communication or engagement features.
- The 72-hour expiry and hard-deletion promise is implemented and directly verified.
- Account deletion is permanent.
- The signed APK can be installed and updated with the preserved release key.
- The repository contains setup, deployment, API, privacy, threat, checksum, signing, and non-goals documentation.
- The feature set is frozen around the original purpose.

## 10. Permanent backlog policy

After v1, only changes that preserve the product constitution should be considered, such as security patches, compatibility fixes, dependency updates, or a narrowly scoped bug fix required to keep the existing flow working.

Do not add features merely because they improve convenience, discovery, retention, social interaction, status signalling, data collection, monetisation, or scale. If the app stops working because the platform or dependencies change, a small maintenance fix is acceptable; redesigning it into a general social product is not.
