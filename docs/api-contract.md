# kNupdate API Contract

This document defines the first backend boundary. The server is authoritative for identity, connection eligibility, authorization, activity visibility, and expiry.

## Conventions

- Base path: `/api/v1`.
- JSON request and response bodies.
- TLS is required outside local development.
- Authenticated endpoints use `Authorization: Bearer <access-token>`.
- Resource identifiers are UUIDs and must not be sequential.
- Error responses use a stable machine-readable `code` and a safe human-readable `message`.
- Activity payloads, phone numbers, OTPs, tokens, and connection codes must not be written to logs.

## Authentication

### `POST /auth/request-otp`

Request:

```json
{ "phone_number": "+91XXXXXXXXXX" }
```

The raw number is passed only to the OTP provider when necessary. The application database stores a normalized hash, not the raw number.

Response:

```json
{ "challenge_id": "opaque-provider-challenge-id", "expires_in_seconds": 300 }
```

The endpoint must be rate-limited and must not reveal whether a phone number already has an account.

### `POST /auth/verify-otp`

Request:

```json
{ "challenge_id": "...", "otp": "123456", "display_name": "Aniruddh" }
```

Response:

```json
{
  "access_token": "...",
  "refresh_token": "...",
  "expires_in_seconds": 900,
  "user": { "id": "uuid", "display_name": "Aniruddh" }
}
```

Display names are plain text, 2–40 characters, and may not be emoji-only.

### `POST /auth/sign-out`

Invalidates the current refresh session. It does not delete the account.

### `DELETE /account`

Permanently deletes the authenticated account, devices, connections, pending requests, connection codes, and current activities. There is no recovery period.

## Profile

### `GET /me`

Returns only the authenticated user's display name, unexpired own activities, current connection names, and read-only app configuration such as the expiry window. Never returns a phone number, public handle, profile photo, bio, location, or activity history.

### `PATCH /me/display-name`

Request:

```json
{ "display_name": "New name" }
```

No other profile fields are accepted.

## Devices

### `POST /devices/register`

Registers the authenticated device's FCM token for notification delivery. Tokens must be protected at rest and excluded from logs.

### `DELETE /devices/{id}`

Removes a device token owned by the authenticated user.

## Connections

### `GET /connections`

Returns the current symmetric connections as names and opaque connection IDs. It does not expose a searchable graph or allow browsing mutuals.

### `POST /connection-codes`

Creates a short-lived, single-use code that the target may provide directly outside the app. The API returns the code once; only its hash is stored.

### `POST /connection-requests`

Request:

```json
{ "connection_code": "one-time-code" }
```

The server validates the code, rejects self/duplicate/replayed requests, and enforces the two-existing-mutual-connections rule except for the private seed-user bootstrap allowlist.

### `GET /connection-requests/incoming`

Returns pending requests with requester display name and shared-mutual count only.

### `POST /connection-requests/{id}/accept`

Creates one canonical symmetric connection after re-validating the request.

### `POST /connection-requests/{id}/decline`

Declines the request without exposing a social signal to the requester.

### `DELETE /connections/{id}`

Removes the symmetric connection. Future activity visibility and notification delivery stop immediately. Formerly visible activities are removed from the former connection's feed immediately and are still hard-deleted on their normal schedule.

## Activities

### `POST /activities`

Request:

```json
{
  "activity_type": "run",
  "started_at": "2026-09-11T09:30:00+05:30",
  "ended_at": "2026-09-11T10:00:00+05:30",
  "quantity_type": "distance",
  "quantity_value": 5
}
```

The server derives the unit and computes `created_at` and `expires_at`. The accepted activity types and quantity types are finite, source-controlled enums. The endpoint accepts no caption, title, description, link, location, media, proof, tag, or custom unit.

Validation:

- End time must be after start time.
- Duration must not exceed 24 hours.
- Quantities must be positive and within type-specific limits.
- The request must be authenticated.
- The server applies the global 72-hour expiry window.

There is intentionally no activity edit or delete endpoint; expiry and account deletion are the only removal paths.

### `GET /feed`

Returns unexpired activities authored by current mutual connections, ordered only by `created_at DESC`. It never returns a public feed, recommendations, engagement metadata, or activity URLs.

### `GET /me/activities`

Returns only the authenticated user's unexpired activities. Expired rows must not be returned even if the deletion worker has not run yet.

## Explicitly absent endpoints

There are no endpoints for public profiles, arbitrary user lookup, search, contact import, media, links, activity history, export, sharing, likes, comments, replies, reactions, reposts, saves, views, rankings, recommendations, analytics, moderation feeds, or messaging.
