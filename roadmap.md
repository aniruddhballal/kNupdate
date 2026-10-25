# kNupdate Roadmap

## Current goal

Build a small, local-only Android activity recorder with a restrained `mp3fy`-inspired interface.

The current version should do only this:

1. Ask for a display name on first launch.
2. Save that name locally on the phone.
3. Offer a finite list of activity titles.
4. Offer a finite list of details/bodies for the selected title.
5. Offer either a single time or a from/to time range.
6. Default time inputs to the current phone time.
7. Reject future times.
8. Save the structured update locally.
9. Show a temporary simulated confirmation when `kNupdate` is pressed.

## Completed local core

- [x] Local display-name setup using Android app storage.
- [x] No `INTERNET` permission in the Android app.
- [x] Fixed activity title and body choices.
- [x] Single-time and from/to time flows.
- [x] Current-time defaults.
- [x] Future-time rejection.
- [x] Local update persistence.
- [x] Local update list.
- [x] Simulated zero-connections confirmation.
- [x] White/black `mp3fy`-style visual system.
- [x] `kN` launcher mark and wordmark.
- [x] Debug APK build and test command.

## Next, in order

1. Test the complete local flow on a physical phone.
2. Decide whether the display name needs a local edit/reset action.
3. Add focused unit/UI tests for future-time rejection, range validation, and persistence.
4. Tighten the activity taxonomy and wording after using the app personally.
5. Only then decide whether connections or cloud sync are worth reintroducing.

## Explicitly out of scope for now

Cloud accounts, backend services, databases, OTP, connections, notifications, analytics, public profiles, search, media, sharing, comments, likes, messages, and remote activity history.
