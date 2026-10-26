# kNupdate

An Android app for public, live activity broadcasts with local sent and received histories. This prototype has no accounts or connection graph. Every phone using the same relay and actively receiving is eligible for new updates.

The app displays its version beside `kN`. Android metadata and the UI share `version.properties`; each app artifact build advances the patch version by default. See [VERSIONING.md](VERSIONING.md) for the historical calculation and major/minor build options.

## Behavior

1. Choose a group, an activity from its icon grid, a predefined detail, and a time or time range. `Run / 5k` is included.
2. Sending commits a copy to the phone's SQLite history before posting the activity to the relay. The local display name is never included.
3. Receiving uses a user-started foreground service with a persistent notification and 25-second HTTP long polls. A response returns immediately when an update arrives.
   Turning the header switch off, or choosing **Stop receiving** in the notification, immediately clears the local receiving state and invalidates callbacks from that receiver run. Closing the old network connection and notifying the relay happen separately, so a failed or pending poll cannot change the stopped state back to retrying.
4. The relay snapshots active random listener sessions when the activity arrives. A five-second lease covers the gap between polls. New listeners are not added to an existing update.
5. Each recipient commits its copy to local SQLite, then acknowledges it. Repeated delivery after a lost acknowledgement does not create another local entry or notification.
6. The last required acknowledgement removes the relay's copy. A 30-second expiry removes unacknowledged copies; no listeners means immediate disposal. Concurrent activities have independent lifetimes.
7. Sent history stays in **Sent**; incoming activity stays in **Inbox**. No offline catch-up or automatic delayed sending is implemented. A failed POST is marked unconfirmed because a network timeout cannot prove whether the server accepted it.

A listener is a random, temporary session handle. It is regenerated when receiving starts or Android reports loss of connectivity. It is not an account, device ID, display name, or profile. Online/offline detection is approximate: a dropped TCP connection cannot always be detected immediately, and the lease deliberately allows a small gap between requests. A failed delivery can remain pending until expiry.

## Activity catalog and times

The app includes 100 predefined activities across ten groups. Both activity and detail are selected from fixed choices; there is no custom activity or free-text detail field. The catalog is bundled with the app in `ActivityCatalog.kt`, with no remote catalog service or dependency on another app. Swipe the group strip horizontally to browse all groups. Selected activities appear as white icons on black tiles; taps compress the rounded tile without a rectangular ripple. Sent and Inbox history rows keep their existing layout.

| Group | Activities |
| --- | --- |
| Everyday | Location, Wake up, Sleep, Rest, Eat, Drink, Cook, Bathe, Groom, Get ready |
| Work | Work, Meeting, Video call, Phone call, Email, Plan, Focus, Present, Interview, Break |
| Learning | Study, Read, Class, Lecture, Homework, Revision, Exam, Research, Practice, Course |
| Movement | Exercise, Run, Walk, Cycle, Gym, Yoga, Stretch, Swim, Hike, Sport |
| Travel | Travel, Commute, Drive, Ride, Bus, Train, Flight, Arrived, Departed, Wait |
| Leisure | Music, Watch, Game, Create, Write, Photos, Garden, Explore, Event, Relax |
| Social | Meet, Family, Friends, Date, Visit, Chat, Celebrate, Volunteer, Pet time, Community |
| Home | Clean, Laundry, Dishes, Tidy, Repair, Move home, Childcare, Pet care, Meal prep, Home admin |
| Errands | Shop, Groceries, Collect, Delivery, Appointment, Bank, Post office, Fuel / charge, Queue, Haircut |
| Wellbeing | Meditate, Breathe, Journal, Pray, Health visit, Therapy, Self-care, Recover, Medicine, Check-in |

**At** means one timestamp. Location, Wake up, Drink, Arrived, Departed, Collect, Delivery, Medicine and Check-in default to this. All remaining activities default to **From–to**, including meals, work, study, exercise, travel and sleep. Either mode can be selected for any activity: for example, use At to announce that a run is starting, or From–to to report the completed run. The app rejects future timestamps and ranges whose end is not after the start.

Each time field has its own **Now** button. It captures the device clock at the instant of the tap, including seconds and milliseconds; the value stays fixed until changed. Initial time values also capture the clock when the composer opens or a different activity is chosen. Opening the manual picker and confirming a time instead sets seconds and milliseconds to zero. Exact values pass through the relay and both local histories unchanged; times with sub-minute precision display seconds, while whole-minute times display hours and minutes.

Times retain their selected date, even if midnight passes before sending. The manual picker edits that field's existing date, displayed in its heading; use Now to switch a field to the current date. A previous day's field shows its date in the composer. These controls can represent an overnight range by setting the start before midnight and the end after it, without silently moving the start to another day. There is no general date picker.

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

The deployed endpoint is `https://knupdate-relay.quiet-airport.workers.dev`. The owner completed its Cloudflare account claim on 26 October 2026, and the provided APK uses this endpoint. The private claim token is not stored in this repository. Future deployments require authentication to the claimed account with `wrangler login`; the original temporary credentials do not grant permanent access.

After the account claim, the live HTTP integration suite and the Android HTTP client/SQLite round-trip test passed against the deployed HTTPS endpoint. These checks cover two recipients, storage before acknowledgement, duplicate suppression after reopening, deletion after the final acknowledgement, late-listener exclusion, and the 30-second expiry. The round-trip test runs under Robolectric on the desktop; physical-phone notifications, screen-off receiving, and battery policies still require device acceptance testing. The app reports hosting browser challenges (`cf-mitigated: challenge`, HTTP 403) if they occur.

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

The deployed relay is the default in `app/build.gradle.kts`, so a normal build already points to it. Developers can override it with `-PrelayUrl` or `KNUPDATE_RELAY_URL` when building. The address is compiled into the APK; there is no URL editor, Save control, or Advanced page in the app. Changing the relay requires rebuilding and installing an updated APK. Old relay preferences saved by earlier versions are ignored. Production builds require HTTPS; debug builds also allow HTTP to local development addresses.

On each phone:

1. Install the APK and enter a local display name.
2. The provided APK already contains the relay address; no server setup is needed on the phone.
3. Turn on the black-and-white switch at the right of the header. On Android 13+, the app requests notification permission if needed; receiving still starts if permission is declined. The persistent notification includes a stop action. Notification alerts can also be enabled later in Android's app notification settings.
4. Use the **plus** icon to compose an activity, the **paper-plane** icon to view sent activities, and the **postbox** icon for Inbox. Send an activity from another phone and check the recipient's Inbox and notification.
5. Turn a recipient's internet off, send another activity, and reconnect. That activity should not be offered to its new listener session.

Android does not permit a guarantee of constant background polling. Android 15+ limits a background `dataSync` foreground service to six hours per 24-hour window, with resets when the user brings the app to the foreground. Doze, force-stop, process termination and manufacturer battery policies can also interrupt receiving. The service stops cleanly on a platform timeout, does not secretly restart at boot, and must be started again from the app. Internet connectivity alone is not enough to guarantee reception. Notifications can be denied while received activities still save to Inbox.

## App controls and status labels

The app shows activity content and three icon tabs: plus (new), paper plane (sent), and postbox (Inbox). The selected icon has a black background. Taps animate the icon's scale and opacity rather than drawing a rectangular ripple. The receiving switch sits at the right of the header on all three screens: white track with a black thumb on the left is off; black track with a white thumb on the right is on. Its thumb moves with a damped spring, gently widens while pressed, and transitions with the track colors inside the pill shape. On means the receiving service is enabled; network access and delivery remain best effort.

Screen-reader labels identify all three tabs and the receiving switch. Its accessible state includes short receiving status such as Off, Listening, Offline or Retrying. There is no separate Relay tab. Setup explanations, delivery semantics, and privacy details live in this README; there are no server configuration controls, Advanced page, help button, or information panel.

| Label | Meaning |
| --- | --- |
| Off | Receiving is disabled on this phone. |
| Connecting | The receiving service is starting. |
| Listening | The service is polling for live updates. Receipt remains best effort. |
| Offline | The phone has no validated internet connection. |
| Retrying | A relay request failed; the receiver is retrying with backoff. |
| Unavailable | Hosting requires browser verification, which the app cannot complete. |
| Paused | Android stopped receiving at its background-service time limit. Reopen the app and start receiving again. |
| Sending | The activity was saved locally and is being submitted to the relay. |
| No listeners | The relay accepted the send with zero eligible recipients, so it did not retain the activity. |
| Sent · N listening | N listener sessions were eligible at acceptance. This is not confirmation that N people received a notification. |
| Unconfirmed | The send result could not be confirmed. Its local copy remains, and it will not resend automatically. |
| Local | This history entry is stored only on this phone, such as a migrated entry. |

Inbox rows contain the activity and timestamp; they omit a repeated receipt explanation. A permission denial leaves incoming activities visible in Inbox without notification alerts.

Short errors stay in the app when an action cannot proceed. History unavailable or Could not save indicates a local-storage problem; check free storage and restart the app. Time validation requires past times and an end after the start. No history is uploaded for troubleshooting.

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

Android unit tests verify the fixed catalog, event/duration defaults, exact current-time capture, manual minute precision, midnight and range validation, plus Robolectric checks for SQLite persistence across reopening, receive deduplication, interrupted-send status, legacy migration, uncapped history, network field allowlisting, timestamp validation, and relay URL validation. An opt-in Robolectric round-trip test also uses the actual Android HTTP client and three separate SQLite databases against a real relay. Run it against an otherwise unused endpoint:

```powershell
$env:RELAY_TEST_URL = "http://127.0.0.1:8787"
.\gradlew.bat testDebugUnitTest
```

It verifies a sender's local copy, two concurrent recipients, persistence before acknowledgement, duplicate suppression after reopening, and deletion after the second acknowledgement. Physical-device screen-off and battery-policy behavior still requires testing on real devices.

The composer also has physical-device UI tests. They edit drafts without posting: checking category/activity selection, mode overrides, both Now labels fitting on one line, manual versus exact precision, and draft preservation through activity recreation. After building/installing the app and test APK:

```powershell
adb shell am instrument -w -r -e class com.knupdate.app.ActivityComposerTest com.knupdate.app.test/androidx.test.runner.AndroidJUnitRunner
```

On 26 October 2026, an ADB-connected Android 15 phone running the committed APK passed a short background-receiving check against the live relay. It received a labelled synthetic update and posted a notification with the app in the background. A temporary second listener withheld acknowledgement; the relay retained the update until its acknowledgement, then the public pending list became empty within approximately 0.4 seconds. This does not establish long-term screen-off or Doze reliability.

`DevicePrivacyTest` provides a read-only audit of an installed app's backup flag and local SQLite receipt counts. It reports aggregate counts without exporting the database, display name, or activity rows. Android instrumentation restarts the app process, so run it outside an active receiving test and restart receiving afterwards if needed:

```powershell
.\gradlew.bat assembleDebugAndroidTest "-PrelayUrl=https://knupdate-relay.quiet-airport.workers.dev"
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.knupdate.app.DevicePrivacyTest com.knupdate.app.test/androidx.test.runner.AndroidJUnitRunner
adb uninstall com.knupdate.app.test
```

The device audit confirmed backup disabled in the installed APK, one locally retained send accepted with zero listeners, and the synthetic received activity persisted after the receiving process restarted. It does not inspect or prove erasure within Cloudflare's internal infrastructure. After an instrumentation run, the header switch can be used to restart receiving.

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
