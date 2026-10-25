# kNupdate

kNupdate is a local-first activity update app for one phone. It is intentionally small: the user chooses a display name once, selects a structured activity, records where/what they were doing and when, then saves the update locally.

There is currently no account, internet connection, backend, cloud database, notification service, analytics, sharing, or connection graph. The `kNupdate` action shows a temporary simulated delivery confirmation because connections are not implemented yet.

## Current app flow

1. Enter a display name. It is stored only in Android local app storage.
2. Choose a fixed activity title, such as `Current location`, `Working`, `Studying`, `Eating`, `Exercising`, `Travelling`, or `Sleeping`.
3. Choose a fixed detail/body, such as `Home`, `Work`, `Office`, or `Library`.
4. Choose either one time or a from/to time range. The default is the current phone time, and future times are rejected.
5. Press `kNupdate`. The structured update is saved locally and the simulated confirmation explains that no connections exist yet.

## Design

The Android UI follows the local `mp3fy` visual language: white background, black `kN` wordmark, thin rules, compact uppercase labels, square buttons, and simple flat list rows. The launcher mark is a black `kN` on white.

## Build and install

Prerequisites:

- Android Studio with the Android SDK and JDK.
- An Android device or emulator.

Build and test:

```sh
./gradlew test
./gradlew assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. A copy of the latest local-only debug APK is kept at the repository root as `kNupdate-debug.apk` for direct installation.

## Deliberately deferred

Connections, cloud sync, notifications, accounts, profiles, authentication, activity expiry, and remote persistence are intentionally deferred. They should only be added after the local interaction model is stable.

## Repository layout

- `app/` — the Android application.
- `roadmap.md` — the short implementation plan for the local-first version.
- `gradle/` — Gradle wrapper and dependency catalog.

## License

MIT. See [LICENSE](LICENSE).
