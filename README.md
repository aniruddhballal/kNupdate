# kNupdate

An Android app for public, live activity broadcasts with local sent and received histories. This prototype has no accounts or connection graph. Every phone using the same relay and actively receiving is eligible for new updates.

The app displays its version beside `kN`. Android metadata and the UI share `version.properties`; each app artifact build advances the patch version by default. See [VERSIONING.md](VERSIONING.md) for the historical calculation and major/minor build options.

## Behavior

1. Choose a group, an activity from its icon grid, and a predefined detail. Time defaults to **Live**, captured when **kNupdate** is pressed; **Custom** optionally selects a timestamp or range. `Run / 5k` is included.
2. Sending commits a copy to the phone's SQLite history before posting the activity to the relay. The local display name is never included.
3. Receiving uses a user-started foreground service with a persistent notification and 25-second HTTP long polls. A response returns immediately when an update arrives.
   Turning the header switch off, or choosing **Stop receiving** in the notification, immediately clears the local receiving state and invalidates callbacks from that receiver run. Closing the old network connection and notifying the relay happen separately, so a failed or pending poll cannot change the stopped state back to retrying.
4. The relay snapshots active random listener sessions when the activity arrives. A five-second lease covers the gap between polls. New listeners are not added to an existing update.
5. Each recipient commits its copy to local SQLite, then acknowledges it. Repeated delivery after a lost acknowledgement does not create another local entry or notification.
6. The last required acknowledgement removes the relay's copy. A 30-second expiry removes unacknowledged copies; no listeners means immediate disposal. Concurrent activities have independent lifetimes.
7. Sent history stays in **Sent**; incoming activity stays in **Inbox**. No offline catch-up is implemented. Only the optional coordinate snapshot supports an explicit scheduled send (see below). A failed POST is marked unconfirmed because a network timeout cannot prove whether the server accepted it.

## Optional location coordinates

Choose **Everyday → Location → Share coordinates**, grant Android location permission, keep the phone's location switched on, then tap **kNupdate**. The default **Live** mode starts a fresh snapshot request when kNupdate is pressed. **Custom** optionally selects a future time today. Description-only location updates and all other activities allow past custom times. Coordinate snapshots use one timestamp, not a range.

The phone waits locally and requests a fresh location fix only when the selected time arrives. It sends latitude, longitude, reported accuracy in meters, and capture time to the listeners active when the POST reaches the relay. Android's approximate permission is supported; coordinates may then be less precise. There is no continuous location subscription while waiting, no last-known-location fallback, and no map or map service dependency.

One location send can be pending per phone. A user-started location foreground service keeps a notification visible while waiting; **Cancel location send** is available in the app and notification. Notification permission is requested separately on Android 13+; denying notifications does not grant or revoke location access. The service starts while the activity is visible and does not request background location permission. See [Android's foreground location rules](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

For future sends on Android 12+, the app opens **Alarms & reminders** settings if access is missing. Allow it, return, and tap **kNupdate** again. Immediate snapshots do not need this access. An awake timer plus `setExactAndAllowWhileIdle` dispatch the request; there is no silent fallback to an inexact alarm. See [Android's exact-alarm permission and timing rules](https://developer.android.com/develop/background-work/services/alarms). Timing still depends on Android and device battery policies, and acquiring a fresh fix can take up to 30 seconds. Denied permission, disabled location, or lack of a fresh fix fails the send instead of sending old coordinates. A stopped service, reboot, force-stop, process termination, or revoked alarm access does not silently restart a schedule. On reopening, interrupted schedules show as not sent. A failed POST remains unconfirmed and is never retried automatically.

Both Sent and Inbox retain coordinates and capture time in private local SQLite, and the expanded incoming notification includes the coordinates. Existing local databases migrate without losing history. Maps remain deferred. Coordinates follow the existing relay lifetime: plaintext in process memory until all eligible listeners acknowledge, or at most 30 seconds; no Cloudflare database, KV, durable queue, or payload logging is added. The relay and its public pending-update endpoint remain public, as described below.

Deploy the updated `backend` before using coordinate sends against an existing relay: the earlier relay rejects the additional field. Local validation uses synthetic coordinates, including a real HTTP/Android SQLite round trip. Physical-device GPS acquisition and screen-off timing still need acceptance testing.

A listener is a random, temporary session handle. It is regenerated when receiving starts or Android reports loss of connectivity. It is not an account, device ID, display name, or profile. Online/offline detection is approximate: a dropped TCP connection cannot always be detected immediately, and the lease deliberately allows a small gap between requests. A failed delivery can remain pending until expiry.

## Activity catalog and times

The app includes 100 predefined activities across ten groups. Both activity and detail are selected from fixed choices; there is no custom activity or free-text detail field. The catalog is bundled with the app in `ActivityCatalog.kt`, with no remote catalog service or dependency on another app. Swipe the group strip horizontally to browse all groups. Selected activities appear as white icons on black tiles; taps compress the square tile without a ripple overlay. Every activity has its own distinct icon; icon curves are retained while all UI containers use square corners. Sent and Inbox history rows keep their existing layout.

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

Every activity starts in **Live** mode, with no timestamp or range stored in the draft. A ticking clock appears just above kNupdate with a **Custom** button beside it. Pressing kNupdate captures the device clock at that instant, including seconds and milliseconds, and sends one timestamp. This remains true if the composer has been open for minutes or across midnight. The displayed clock is only a preview; it is never reused as the send time. Only the visible clock updates each second, and ticking pauses when the activity is no longer started.

Tap **Custom** or the live clock to open the time editor. **At** means one timestamp; **From–to** requires both a start and an end. Location, Wake up, Drink, Arrived, Departed, Collect, Delivery, Medicine and Check-in default to At inside this editor; remaining activities default to From–to. Either mode can be selected for ordinary activities. Coordinate sharing supports At only, for future scheduling. Ordinary custom times must be in the past, and range end must be after start. Opening and cancelling the editor leaves the original draft unchanged; times apply only after **Use time**.

Custom times remain fixed until edited or cleared using **Live**, which restores the ticking clock and clears both custom fields. Changing activity also resets time to Live. There are no Now buttons. Manual input uses minute precision, setting seconds and milliseconds to zero, and retains the date captured when that field's picker opened. There is no general date picker. Custom drafts survive recreation and scrolling. Exact values pass through the relay and local histories unchanged; history times with sub-minute precision display seconds, while whole-minute times display hours and minutes.

## Scrolling and visual consistency

The composer uses one `LazyColumn` with separate keyed rows for activity tiles, details and time controls, plus a horizontal `LazyRow` for the group strip. Off-screen rows can leave the layout rather than keeping the whole form active. All activity, detail, mode and navigation containers, dialogs, buttons, fields and the animated receiving switch use square corners. The theme also defaults Material shapes to zero corner radius. Icon geometry keeps natural curves and a consistent stroke weight.

There are exactly 100 different activity icon resources, checked by the catalog test. Most are adapted from [Lucide](https://lucide.dev/) at pinned revision `6657faa96ae10efe1509203c62b60d913006ab82`; Run uses an original running figure. The upstream ISC/MIT notices ship in [the APK and source](app/src/main/assets/licenses/lucide.txt). `python scripts/import_activity_icons.py` reproducibly imports just the chosen vectors, verifies distinct geometry, and updates the resource mapping. The app never downloads icons or a catalog at runtime.

On 26 October 2026, a short comparison on the same Android 15 phone used three warm-up swipes, reset `dumpsys gfxinfo`, then six alternating 500 ms vertical swipes. The previous debug APK recorded a 14 ms median, 21 ms 95th percentile and 4 janky frames out of 245. The optimized 1.2.2 preview recorded 10 ms, 14 ms and 3 out of 238 respectively. This is a small sample under normal phone conditions; it does not isolate individual optimizations. The new preview is 1,203,582 bytes versus 9,750,750 bytes for its debug counterpart. Three draft-only UI tests passed on debug; the installed optimized preview also passed a foreground receiver start/stop smoke check with its debuggable flag absent.

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

The current deployed endpoint is `https://knupdate-relay.aniruddhballaldeeksha.workers.dev`, returned by the owner's successful deployment on 27 October 2026. The provided APK uses this address. The previous `quiet-airport` endpoint is a separate, older deployment: live checks confirmed it still rejects the coordinates field. All sender and listener phones must use the updated APK to share this relay. Future deployments require authentication to the owner's Cloudflare account with `wrangler login`; temporary deployment credentials do not grant permanent access. No private claim token is stored in this repository.

Live validation on 27 October confirmed that the current endpoint recognizes valid coordinate fields and rejects invalid latitude values. Deliberately invalid test sessions stopped these probes before publication, so no synthetic update was broadcast to phones. This is a schema check, not a physical-device location or delivery acceptance test.

After the original account claim on 26 October, the live HTTP integration suite and the Android HTTP client/SQLite round-trip test passed against the previous `quiet-airport` HTTPS endpoint. These checks cover two recipients, storage before acknowledgement, duplicate suppression after reopening, deletion after the final acknowledgement, late-listener exclusion, and the 30-second expiry. The round-trip test runs under Robolectric on the desktop; physical-phone notifications, screen-off receiving, and battery policies still require device acceptance testing. The app reports hosting browser challenges (`cf-mitigated: challenge`, HTTP 403) if they occur.

## Build and use the Android app

Prerequisites: Android SDK 36 and JDK 17. The app supports Android 8+.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug lintPreview assemblePreview
```

For normal phone use, build the optimized preview:

```powershell
.\gradlew.bat assemblePreview
adb install -r app/build/outputs/apk/preview/app-preview.apk
```

The recommended APK is at `app/build/outputs/apk/preview/app-preview.apk`; its committed copy is `kNupdate.apk` at the repository root, replacing the old `kNupdate-debug.apk`. Preview is non-debuggable, with R8 code optimization and resource shrinking enabled. It uses the same local Android debug signing certificate as previous installs so upgrading on this machine preserves local data. This is a personal testing certificate, not a production distribution key. Release builds also optimize and shrink, and need production signing configured separately. The ordinary development APK remains available at `app/build/outputs/apk/debug/app-debug.apk` after `assembleDebug`.

Debug and preview APKs built in one Gradle invocation share one version reservation. Test the UI using the debug APK and instrumentation APK, then install the preview APK to judge normal scrolling performance. See [Android Compose performance guidance](https://developer.android.com/develop/ui/compose/performance).

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

The app shows activity content and three icon tabs: plus (new), paper plane (sent), and postbox (Inbox). The selected icon has a black background. Taps animate the icon's scale and opacity rather than drawing a rectangular ripple. The receiving switch sits at the right of the header on all three screens: white track with a black thumb on the left is off; black track with a white thumb on the right is on. Its thumb moves with a damped spring, gently widens while pressed, and transitions with the track colors inside the rectangular track. Both the track and thumb have square corners. On means the receiving service is enabled; network access and delivery remain best effort.

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

Android unit tests verify the fixed catalog, custom event/duration defaults, exact button-press time capture after waiting across midnight, fixed custom timestamps/ranges, manual minute precision, midnight and range validation, plus Robolectric checks for SQLite persistence across reopening, receive deduplication, interrupted-send status, legacy migration, uncapped history, network field allowlisting, timestamp validation, and relay URL validation. An opt-in Robolectric round-trip test also uses the actual Android HTTP client and three separate SQLite databases against a real relay. Run it against an otherwise unused endpoint:

```powershell
$env:RELAY_TEST_URL = "http://127.0.0.1:8787"
.\gradlew.bat testDebugUnitTest
```

It verifies a sender's local copy, two concurrent recipients, persistence before acknowledgement, duplicate suppression after reopening, and deletion after the second acknowledgement. Physical-device screen-off and battery-policy behavior still requires testing on real devices.

The composer also has physical-device UI tests. They edit drafts without posting: checking the ticking Live clock, custom mode defaults and overrides, both range fields being required, cancellation without changing Live, return to Live, and custom draft preservation through recreation and repeated scrolling. After building/installing the app and test APK:

```powershell
adb shell am instrument -w -r -e class com.knupdate.app.ActivityComposerTest com.knupdate.app.test/androidx.test.runner.AndroidJUnitRunner
```

On 27 October 2026, all three composer UI tests passed on the connected phone with the 1.3.4 debug build. The Live clock ticked, custom values survived recreation and scrolling, incomplete ranges could not be applied, and cancelling or returning to Live left no frozen default timestamp. These checks did not publish activities.

On 26 October 2026, an ADB-connected Android 15 phone running the committed APK passed a short background-receiving check against the live relay. It received a labelled synthetic update and posted a notification with the app in the background. A temporary second listener withheld acknowledgement; the relay retained the update until its acknowledgement, then the public pending list became empty within approximately 0.4 seconds. This does not establish long-term screen-off or Doze reliability.

`DevicePrivacyTest` provides a read-only audit of an installed app's backup flag and local SQLite receipt counts. It reports aggregate counts without exporting the database, display name, or activity rows. Android instrumentation restarts the app process, so run it outside an active receiving test and restart receiving afterwards if needed:

```powershell
.\gradlew.bat assembleDebugAndroidTest "-PrelayUrl=https://knupdate-relay.aniruddhballaldeeksha.workers.dev"
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

The publish body contains `id`, `title`, `body`, `startAt`, `endAt`, and `timeZone`, plus an optional `coordinates` object with exactly `latitude`, `longitude`, `accuracyMeters`, and `capturedAt`. Coordinates require `endAt: null`. Latitude/longitude must be finite and within their geographic ranges, accuracy must be nonnegative, and capture time must be at or after `startAt` and no more than a minute ahead of the relay clock. Times are Unix milliseconds. Ordinary activities omit `coordinates` entirely, preserving compatibility with older clients. Responses to polling/public reads add `expiresAt`. POST responses report how many listeners were eligible, not proof of notification or completed delivery.

## Deferred

Connection-only routing, encryption, shared-folder storage, accounts, permanent user identifiers, and guaranteed background push delivery remain future work. See `roadmap.md`.

MIT. See [LICENSE](LICENSE).
