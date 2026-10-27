# App versions

`version.properties` at the repository root is the single source of truth. Its `version` becomes Android's `versionName` and generated `BuildConfig.VERSION_NAME`; the header renders that generated value beside `kN`. Its `versionCode` is Android's separate increasing install/update counter. There is no separately maintained UI version string.

## How the existing APK was calculated

The baseline requested by the owner is `c9060f3ed82d01ab4e3f53639eb2be0a51f2cef9` = **1.0.0**. We apply product-oriented semantic versioning: fixes and UI refinements are patches; compatible feature additions are minor releases; incompatible replacements are major releases. The custom time picker corrects/enforces the existing no-future-times behavior, so it is classified as a patch. The relay adds compatible functionality and migrates local history, so it is classified as minor.

| Commit | Change | Calculated version |
| --- | --- | --- |
| `c9060f3` | Revamped local activity recorder; baseline | 1.0.0 |
| `fff2157` | Fix safe areas and launcher icon | 1.0.1 |
| `c6e1424` | Inset launcher wordmark safely | 1.0.2 |
| `5284971` | Clarify launcher mark | 1.0.3 |
| `12524c3` | Refine block wordmark | 1.0.4 |
| `bbd1c72` | Simplify activity screen | 1.0.5 |
| `36b65cb` | Enforce future-time prevention in the picker | 1.0.6 |
| `944cffd` | Open picker at the selected value | 1.0.7 |
| `8920956` | Refine update confirmation | 1.0.8 |
| `c258c35` | Planning notes; no app change | 1.0.8 |
| `46953a6` | Add relay, receiving, notifications and local histories | 1.1.0 |
| `2948bcc` | Commit the same release's APK | 1.1.0 |
| `c3b8511` | Deployment documentation | 1.1.0 |
| `5c2699b` | Device audit tests and documentation | 1.1.0 |
| `d068150` | Fix Stop receiving and stale callbacks | 1.1.1 |
| `ad2675c` | Commit the same release's APK | 1.1.1 |

Thus the APK before versioning was introduced maps to **1.1.1**. The next APK, introducing version metadata and the header label, starts at **1.1.2**. These historical labels are a calculation, not changes to old commits, tags, manifests or APKs. Older APKs actually used Android versionCode 1; the first newly numbered APK advances that to 2.

## Future builds

The activity catalog/grid and exact-time controls were first built as **1.2.0 (code 7)**. The subsequent build fixing the Now button's padding is **1.2.1 (code 8)**, the APK published with this activity revamp. These consumed build numbers are retained even though only the final artifact is committed.

**1.2.2 (code 9)** adds square UI controls, distinct activity icons, row-level lazy scrolling and an optimized preview build. Its published artifact is `kNupdate.apk`; the former root development APK was removed. Debug and optimized preview were built together under this single version reservation.

**1.3.0 (code 12)** adds optional immediate or scheduled location coordinate snapshots, Android location permission, and local coordinate history. Intermediate validation builds consumed 1.2.3 (code 10) and 1.2.4 (code 11); those numbers are not reused.

**1.3.1 (code 13)** explicitly marks location hardware optional, so devices without location hardware remain eligible to install the app.

**1.3.2 (code 14)** requires Alarms & reminders access for future snapshots and uses an exact idle-allowed alarm instead of an inexact fallback. Its optimized APK was published as `kNupdate.apk`.

**1.3.3 (code 15)** points the app to the owner's deployed `aniruddhballaldeeksha.workers.dev` relay. The previous `quiet-airport` relay still rejected coordinate payloads. Sender and listener phones need this updated APK; upgrading preserves local history.

Every normal app APK/AAB build reserves the next patch version and increments versionCode once per Gradle invocation. Debug, preview and release variants in the same invocation share that reservation. This includes full `build`, `assemble`, `bundle`, `package` and `install` artifact tasks, including Android Studio's normal `:app:assembleDebug` invocation. Use complete task names rather than abbreviations.

```powershell
# Next patch build (the default)
.\gradlew.bat assembleDebug "-PrelayUrl=https://knupdate-relay.aniruddhballaldeeksha.workers.dev"

# Next feature release; resets PATCH to zero
.\gradlew.bat assembleDebug -PversionBump=minor "-PrelayUrl=https://knupdate-relay.aniruddhballaldeeksha.workers.dev"

# Next incompatible release; resets MINOR and PATCH to zero
.\gradlew.bat assembleDebug -PversionBump=major "-PrelayUrl=https://knupdate-relay.aniruddhballaldeeksha.workers.dev"
```

The build cannot infer the significance of a future change. The person making it chooses `minor` or `major`; omitted means `patch`. No future numbering reads Git history or commit messages.

IDE sync, `help`, tests, lint, test-APK assembly and dry runs read the current version without advancing it. APK version reservation happens before Android captures metadata, so failed artifact builds may consume a version. Do not roll back/reuse consumed numbers. Artifact builds use normal Gradle configuration; if configuration caching was explicitly enabled, pass `--no-configuration-cache` so each invocation reserves a new version. Concurrent reservations use a file lock and replace the version file atomically when supported.

After a successful build, commit `version.properties` with the code changes and copy the resulting optimized preview APK to `kNupdate.apk` before committing the artifact. A later build intentionally advances the number again, even if source changes are identical. The build logic itself is verified with:

```powershell
.\gradlew.bat -p buildSrc test
```
