# kNupdate Privacy and Deletion Model

## What is stored

The minimum server-side data is:

- `users`: UUID, hashed phone number, display name, and account timestamps.
- `devices`: UUID, owner, protected FCM token, platform, and token timestamps.
- `connections`: one canonical pair of user UUIDs and creation time.
- `connection_requests`: requester, target, status, timestamps, and cleanup expiry.
- `connection_codes`: owner, hash, expiry, and one-time usage timestamp.
- `activities`: author, controlled activity type, controlled quantity, start/end time, creation time, expiry time, and timezone at creation.

The app does not store public handles, profile photos, bios, location, media, captions, contact books, social graph indexes, behavioural events, engagement data, or activity history.

## Who can see an activity

An activity is visible only to:

1. its author; and
2. the author's current, accepted, mutual connections.

Every feed request is authorized on the server. A removed connection cannot fetch new activities, and formerly visible activities are removed from that connection's feed immediately.

## Retention

The first deployment uses one global expiry window: **72 hours from server `created_at`**. Users cannot choose a different duration.

The client hides an activity as soon as local time passes its `expires_at`. The server also filters expired rows from every read query, independent of the deletion worker.

## Hard deletion procedure

At or after `expires_at`, a scheduled worker running at least every 15 minutes:

1. deletes the activity row from PostgreSQL;
2. deletes dependent notification-delivery records;
3. removes any cached or queued activity payload;
4. relies on foreign-key cascades where appropriate;
5. does not write deleted IDs, payloads, counts, or timing data to a retained deletion log.

The client purges expired local records at launch, foreground resume, and periodic sync. Activity text must never be written to Android backup, clipboard, share cache, or permanent file storage.

There is no soft deletion, trash, grace period, restore, archive, export, history, memory, highlight, or retained event log.

## Account deletion

Account deletion permanently removes the account, device tokens, connections, pending requests, connection codes, and current activities. It does not create an archive or recovery period.

## Operational requirements

- TLS is required for network traffic.
- Phone numbers are normalized and hashed before application-database storage.
- Access and refresh tokens are protected by the client with Android Keystore-backed storage.
- Application and HTTP logs must omit phone numbers, OTPs, tokens, connection codes, and activity payloads.
- Backups and infrastructure caches must not retain activity data longer than the product promise. If the selected provider cannot meet this requirement, its retention configuration or the provider must change before production.
- No analytics, advertising, behavioural tracking, experimentation, or data-broker integration is permitted.
