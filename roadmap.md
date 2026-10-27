# kNupdate roadmap

## Implemented

- Local display name and structured activity composer, including detail choices and Run / 5k.
- Real timestamps and local sent/received SQLite histories.
- Ticking Live time by default, resolved at the send-button press, with optional custom timestamp/range editing.
- Audited custom-time capabilities for all 100 activities, with At-only events/snapshots and both-mode ongoing activities; shared editor/send validation.
- Migration of the previous local history without uploading it.
- Authenticated relay using Cloudflare Workers and one Durable Object.
- Anonymous persistent device identities with short-lived pairing invites and mutual pairing metadata.
- Recipient selection and server-side targeted delivery to selected paired devices.
- Activity payloads held in memory only; no cloud database or file history.
- Long polling, ephemeral listener sessions, multiple simultaneous recipients and updates.
- Local commit before acknowledgement; duplicate suppression.
- Explicitly shared listener names, a live directory, and local per-update eligible-recipient and confirmed-Inbox logs.
- Private, temporary receipt reports in relay memory; confirmation is distinct from eligibility and reading.
- Deletion after all eligible acknowledgements, with a 30-second fallback expiry.
- Live-only delivery; no offline backlog or automatic retries of failed sends.
- Optional immediate or scheduled coordinate snapshots with location permission, cancellation, and local coordinate history.
- Android foreground receiving service, incoming notifications and stop action.
- Timeout handling, offline status and retry backoff.
- Relay configuration and health check from the app.
- Disabled cloud/device-transfer backups for local data.
- Relay unit/HTTP tests and Android SQLite/model tests.

## Deployment and device acceptance

- [x] Claim the deployed Cloudflare relay account (26 October 2026).
- [x] Pass HTTP integration and Android client/SQLite round-trip tests against the claimed live endpoint.
- [x] Verify installed backup flag and local receipt counts on an Android 15 phone without exporting its database.
- [x] Verify a synthetic live background delivery, notification, local persistence, and deletion after the last of two listener acknowledgements using one phone and a temporary test listener.
- [x] Deploy the coordinate relay update to the owner's `aniruddhballaldeeksha.workers.dev` endpoint (27 October 2026); verify live coordinate validation without publishing an update.
- [ ] Verify physical-device location permission, fresh fixes, scheduled screen-off sending and cancellation.
- [ ] Verify pairing and selected-recipient delivery on at least two physical phones.
- Install the configured APK on at least two phones.
- Verify notification permissions, screen-off receiving, internet toggling and OEM battery behavior.
- Verify Android foreground-service timeout and explicit restart on a physical device.
- Monitor free-tier quotas during real use; retain the Free plan to avoid spending.

## Later

1. Design and review end-to-end encryption and any shared-folder transport.
2. Decide whether best-effort background receiving meets the product need or requires a push transport.
3. Add abuse controls before treating the relay as a broad public service.
4. Display locally stored coordinates on an open-source map.

No accounts, phone numbers, email addresses, offline catch-up, cloud activity history, or end-to-end encryption are implemented in this phase.
