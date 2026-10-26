# kNupdate

An Android app for public, live activity broadcasts with local sent and received histories. This prototype has no accounts or connection graph. Every phone using the same relay and actively receiving is eligible for new updates.

## Behavior

1. Choose an activity, its detail, and a time or time range for today. `Run / 5k` is included.
2. Sending commits a copy to the phone's SQLite history before posting the activity to the relay. The local display name is never included.
3. Receiving uses a user-started foreground service with a persistent notification and 25-second HTTP long polls. A response returns immediately when an update arrives.
4. The relay snapshots active random listener sessions when the activity arrives. A five-second lease covers the gap between polls. New listeners are not added to an existing update.
5. Each recipient commits its copy to local SQLite, then acknowledges it. Repeated delivery after a lost acknowledgement does not create another local entry or notification.
6. The last required acknowledgement removes the relay's copy. A 30-second expiry removes unacknowledged copies; no listeners means immediate disposal. Concurrent activities have independent lifetimes.
7. Sent history stays in **Sent**; incoming activity stays in **Inbox**. No offline catch-up or automatic delayed sending is implemented. A failed POST is marked unconfirmed because a network timeout cannot prove whether the server accepted it.

A listener is a random, temporary session handle. It is regenerated when receiving starts or Android reports loss of connectivity. It is not an account, device ID, display name, or profile. Online/offline detection is approximate: a dropped TCP connection cannot always be detected immediately, and the lease deliberately allows a small gap between requests. A failed delivery can remain pending until expiry.

## Storage and privacy

The cloud relay holds plaintext activity JSON **only in process memory**. It does not write activity data to a file, SQL, KV, git, or a durable queue. The Durable Object uses a SQLite-backed namespace because Cloudflare's free plan requires that namespace type; the application does not use its database. A restart or deployment discards in-flight activities and listeners.

This choice avoids database recovery history: Cloudflare documents that SQLite-backed Durable Object storage can be recovered to points in the previous 30 days, so deleting database rows would not satisfy the intended short retention. API responses use `Cache-Control: no-store`, application observability is disabled, and the backend never logs activity bodies. Expiry removes application references; it is not a claim about secure erasure of provider RAM, network buffers, or provider infrastructure logs. HTTPS protects transport. Content is not end-to-end encrypted, and ordinary network metadata such as IP addresses still exists at the hosting provider.

`GET /v1/updates` publicly exposes currently pending activities, without recipient session tokens. An arbitrary HTTP client can read or copy them while they are pending, and anyone can publish. The app's normal receive path offers an update only to sessions eligible when it was sent. No connection filtering, authentication, or abuse-proof public service is claimed for this prototype.

Histories are stored in the app's private SQLite database without the former 50-entry cap. Existing local entries migrate without being uploaded; because the old format had no date, migrated entries explicitly say the date is unavailable. Android cloud backup and device-transfer backup are excluded for both history and preferences. Uninstalling or clearing app storage removes local history.

## Why Cloudflare

Cloudflare Workers plus one Durable Object coordinate all listeners at a stable HTTPS endpoint without a server or a separate database subscription. Hugging Face Spaces can sleep and are designed for demos, making them a poor live relay.

Use the **Workers Free plan**. Current documented free limits include 100,000 Worker requests/day, 100,000 Durable Object requests/day, and 13,000 GB-seconds of Durable Object duration/day. A phone doing 25-second long polls continuously uses about 3,456 polls/day, before sends and acknowledgements. This is suitable for a small prototype, not an unlimited free service. Free-tier exhaustion causes failures; do not upgrade to a paid plan if zero spending is a requirement. The relay caps concurrent listener sessions and pending activities at 100 each, and request bodies at 4 KiB.

Research sources:

- [Cloudflare Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/)
- [Durable Objects pricing](https://developers.cloudflare.com/durable-objects/platform/pricing/)
- [Durable Object SQLite recovery](https://developers.cloudflare.com/durable-objects/api/sqlite-storage-api/)
- [Hugging Face Spaces lifecycle](https://huggingface.co/docs/hub/spaces-overview)
- [Android foreground service limits](https://developer.android.com/develop/background-work/services/fgs/timeout)
- [Android Doze and standby](https://developer.android.com/training/monitoring-device-state/doze-standby)

## Deploy the free relay

Prerequisites: Node.js 22.12+ and a Cloudflare account using the Workers Free plan.

```powershell
cd backend
npm ci
npm test
npx wrangler login
npm run deploy
```

Wrangler prints an HTTPS URL such as `https://knupdate-relay.<your-subdomain>.workers.dev`. All phones must use the same endpoint. No token or Cloudflare credentials go into the APK. Source and migrations are in `backend/wrangler.jsonc`; there are no secrets or external database dependencies.

Cloudflare also supports `npx wrangler deploy --temporary` for unauthenticated previews. These expire after 60 minutes unless the intended user completes the account claim. A preview is not permanent hosting. See [Cloudflare claim deployments](https://developers.cloudflare.com/workers/platform/claim-deployments/).

The initial deployed preview endpoint is `https://knupdate-relay.quiet-airport.workers.dev`. The provided APK uses it. This endpoint must be claimed using the private link supplied in the chat before the preview expires; otherwise deploy a permanent relay and update **Relay** on each phone. The claim token is deliberately not stored in this repository.

The initial live HTTP tests passed from Node.js, but subsequent checks encountered Cloudflare browser challenges: consistently from the desktop Java runtime used by Robolectric, and intermittently on Node long polls. The app detects and reports `cf-mitigated: challenge` (HTTP 403). This temporary endpoint is therefore not yet a verified native-phone endpoint. Claim the account, check supported API hosting policies, and verify actual phones before relying on live delivery. If the provider continues challenging app requests, use a supported API hosting endpoint and update **Relay**. The complete Android client/storage round trip has been verified separately against the local relay.

## Build and use the Android app

Prerequisites: Android SDK 36 and JDK 17. The app supports Android 8+.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. A refreshed copy is provided at `kNupdate-debug.apk`.

To bake the deployed public endpoint into a build:

```powershell
.\gradlew.bat assembleDebug "-PrelayUrl=https://knupdate-relay.<your-subdomain>.workers.dev"
```

Alternatively set `KNUPDATE_RELAY_URL` when building, or enter the endpoint on the app's **Relay** page. **Save & check relay** verifies the protocol before saving. Stop receiving before changing endpoints. Production builds require HTTPS; debug builds also allow HTTP to local development addresses.

On each phone:

1. Install the APK and enter a local display name.
2. If needed, enter the relay URL in **Relay**, then choose **Save & check relay**.
3. Enable notifications and choose **Start receiving**. The persistent notification includes a stop action.
4. Send an activity from another phone. Check **Inbox** and the notification on recipients, and **Sent** on the sender.
5. Turn a recipient's internet off, send another activity, and reconnect. That activity should not be offered to its new listener session.

Android does not permit a guarantee of constant background polling. Android 15+ limits a background `dataSync` foreground service to six hours per 24-hour window, with resets when the user brings the app to the foreground. Doze, force-stop, process termination and manufacturer battery policies can also interrupt receiving. The service stops cleanly on a platform timeout, does not secretly restart at boot, and must be started again from the app. Internet connectivity alone is not enough to guarantee reception. Notifications can be denied while received activities still save to Inbox.

## Local relay and verification

Start the local Cloudflare runtime:

```powershell
cd backend
npm ci
npm run dev
```

In another terminal:

```powershell
cd backend
npm run test:integration
```

These HTTP integration tests create real concurrent long polls, test independent recipient acknowledgements and deletion, verify that late listeners miss updates, wait through the 30-second expiry, and reject identity fields and oversized bodies. They can also run against a dedicated deployed test relay by setting `RELAY_TEST_URL`. Run them when no real phones are using that relay because they assert the listener count and pending activities.

For a debug emulator, use `http://10.0.2.2:8787`. For a USB-connected phone, run `adb reverse tcp:8787 tcp:8787` and use `http://127.0.0.1:8787`. To test phones on a trusted LAN, start Wrangler with `npx wrangler dev --ip 0.0.0.0`, allow the development port through the local firewall, and use the computer's local IP address.

Android unit tests use Robolectric to verify SQLite persistence across reopening, receive deduplication, interrupted-send status, legacy migration, uncapped history, network field allowlisting, timestamp validation, and relay URL validation. An opt-in Robolectric round-trip test also uses the actual Android HTTP client and three separate SQLite databases against a real relay. Run it against an otherwise unused endpoint:

```powershell
$env:RELAY_TEST_URL = "http://127.0.0.1:8787"
.\gradlew.bat testDebugUnitTest
```

It verifies a sender's local copy, two concurrent recipients, persistence before acknowledgement, duplicate suppression after reopening, and deletion after the second acknowledgement. Physical-device screen-off and battery-policy behavior still requires testing on real devices.

## Relay API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/health` | Service name, protocol version, expiry and poll interval |
| GET | `/v1/poll` | Long poll; random UUID in `X-Relay-Session` header |
| DELETE | `/v1/poll` | Close a listening session |
| POST | `/v1/updates` | Publish activity JSON; optional sender session excludes its own receiving service |
| GET | `/v1/updates` | Public in-flight activity JSON; no recipient tokens |
| POST | `/v1/ack` | `{ "ids": ["activity-uuid"] }`; recipient session in header |

The publish body contains exactly `id`, `title`, `body`, `startAt`, `endAt`, and `timeZone`. Times are Unix milliseconds; `endAt` is null for one timestamp. Responses to polling/public reads add `expiresAt`. POST responses report how many listeners were eligible, not proof of notification or completed delivery.

## Deferred

Connection-only routing, encryption, shared-folder storage, accounts, permanent user identifiers, and guaranteed background push delivery remain future work. See `roadmap.md`.

MIT. See [LICENSE](LICENSE).
