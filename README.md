# Background Check

A native Android app for inspecting running processes, CPU, memory and battery use, and managing background app activity. Version 1.1.1 adds independent sorting choices and both directions throughout the app, alongside the resource monitor, app diagnostics, confirmed force stop, background-access and Doze controls. Designed for the Motorola Edge, with a charcoal and lime Compose interface.

## Use

1. In Obtainium, add `https://github.com/jordanwoodson/background-check` using a GitHub token with access to this private repository, as for Cana. Alternatively install the signed APK from the [latest release](https://github.com/jordanwoodson/background-check/releases/latest). See [release and Obtainium setup](docs/releases.md).
2. Open Shizuku and start it with **Wireless debugging** (or root).
3. Open Background Check → **Access** and authorize Shizuku.
4. Open **Monitor → Live processes** for whole-device CPU, RAM and swap, session trends, and shell-visible processes. Search by name, command, app, PID or UID; filter app versus system/native processes, and choose a sort order. Tap a process for its command, parent, UID and related apps.
5. Open **Monitor → Battery use** for current battery flow, component estimates and Android UID measurements, initially ranked by estimated mAh. Tap a UID for cumulative CPU time, traffic, partial wakelocks and related apps.
6. Browse **Apps** or open a related app from Monitor for its processes, UID battery estimate, package memory and running services. Background-policy changes require confirmation and Android readback. Undo is available in **Overview → Your changes**.

Shizuku may need to be started again after a reboot. Background Check does not run a background monitor, upload data, or require an internet permission. Tailscale is used only to deliver and test the APK from the development machine; normal use is local on the phone. Direct in-app ADB pairing is not implemented; Shizuku is the supported privilege backend.

## Sorting lists

Use each list’s **Sort by** menu to choose a field, then its **Ascending / Descending** button to reverse the order. Lists keep separate choices when you move between pages; changing a sort does not clear the list’s search or filters.

| List | Sort choices | Initial order |
| --- | --- | --- |
| Apps and Overview’s active-app preview | Name, package, UID, process state, standby group | Name, ascending |
| Overview’s change history | Date, app name, package, action, status | Date, newest first |
| Monitor processes | CPU, resident memory, virtual memory, name, PID, UID, scheduler state | CPU, highest first |
| App-detail processes | CPU, resident memory, virtual memory, name, PID, scheduler state | PID, ascending |
| Battery UIDs | Estimated battery use, CPU time, wakelocks, network received, network sent, app name, UID | Estimated battery use, highest first |
| Device power components | Estimated battery use, displayed name | Estimated battery use, highest first |
| Related apps in process and battery details | Name, package, process state | Name, ascending |
| Running services | Component name, process name, foreground status | Component name, ascending |

Selecting a different field starts with its default direction: resource amounts are highest first, dates newest first, and service foreground status puts foreground services first. Names, numeric IDs, states and standby groups start ascending. Selecting the same field leaves the direction unchanged. Unknown or unavailable values stay last in both directions; numeric IDs sort numerically and text ignores letter case. Equal values use stable tie-breakers.

Ascending app process-state order is **Foreground → Service → Background → Cached → Not observed**, followed by **Unknown**. Descending reverses the known states and still leaves Unknown last. Ascending history status is **Applied → Needs recovery → Restored**. Process scheduler-state sorting uses Android’s reported state flag.

The complete filtered list is sorted before Overview selects its first four active apps or first five history entries. **Show all changes / Show fewer changes** changes only the preview size. Time-series charts remain chronological.

## Reading the monitor

Live polling runs only while Monitor is open and the app is in the foreground. Choose **3, 5 or 10 seconds**, use **Pause / Resume**, or take one sample with **Refresh now**, including while paused. A maximum of 60 recent CPU/RAM samples stays in memory. Sampling itself adds overhead. Battery estimates refresh separately at a slower cadence, or with **Refresh battery estimates**. Timestamps identify each source’s sample; app diagnostics are read when you open an app.

| Reading | Meaning |
| --- | --- |
| Device CPU | Interval usage normalized across all CPU cores, from 0–100%. |
| Process CPU | Interval usage where one fully used core is 100%; a multithreaded process can exceed 100%. |
| RSS / resident memory | Physical resident pages, including shared pages. Adding process RSS can count the same memory more than once. |
| Virtual memory | Address space, including reservations; it is not physical RAM consumption. |
| Available RAM / swap | RAM available to Android, including reclaimable pages, and swap usage/capacity. Swap may include compressed RAM (zram). |
| PSS / private dirty / swap PSS | Package memory diagnostics: shared pages apportioned between processes, modified private pages, and apportioned swapped pages. Shared processes can appear in a package report. |
| Estimated mAh | Android’s cumulative battery estimate for its current history window. It is not instantaneous watts or a direct per-process energy measurement. History can reset after charging or a system reset. |
| CPU time / traffic / wakelocks | Cumulative UID measurements over that battery history. Wakelock duration is not energy; CPU time can exceed elapsed time across multiple cores. |
| Current / estimated power | Whole-battery flow: positive means charging, negative means discharging. Power is current × voltage. Zero flow while plugged in can mean the adapter is supplying the phone directly. Hardware support varies, and these figures do not attribute watts to an app. |

Battery rankings count each UID once. Several packages can share a UID, so the app labels that scope explicitly rather than assigning the same total independently to each package. App process lists match the UID; isolated processes can use another UID. Native services, processes from other users, or removed packages may have no matching installed app. The device component and UID battery breakdowns overlap and must not be added together.

Unavailable or unsupported readings remain **Unavailable**, with warnings where available; they are not converted to zero. A sleeping or cached process still occupies memory without necessarily consuming CPU. Battery statistics are read without resetting Android’s history. Detailed commands and processes may be restricted by Android or the device vendor.

## App controls

**Force stop app** is available only for an ordinary unprotected app. It requires confirmation, rechecks the package identity, Android user and shared UID before sending the command, and verifies Android’s stopped flag and process observation afterward. System/core apps, protected apps and shared UIDs are refused. Force stop can discard unsaved work and interrupt notifications or scheduled work until the app is opened again. **Force stop has no Undo**; the separate policy Undo remains available.

Process state is a point-in-time observation, not a battery-drain measurement. Cached processes are kept for quick reopening. The Android standby bucket, background app-op and Doze exemption are separate readings. Changing background access does not change the independent Doze exemption or Motorola-specific battery settings. Doze exemptions apply to the app across Android profiles. System-provided exemptions cannot be removed here. Shared Android identities, core system services, the active home screen/keyboard, and remote-access apps use Android Settings rather than quick controls.

Undo restores the preceding effective policy only when the current policy still matches the saved result. A default app-op may be restored as its equivalent explicit value. History keeps the latest 50 changes on this device. Uninstalling the app removes history but does not reset Android policies.

Before changing a setting, the app first checks for changes made elsewhere, then saves a recovery checkpoint. Interrupted changes remain in history with **Restore**. Recovery refuses unrelated policy changes. Undo operations also save a recovery checkpoint before writing.

## Build

Requirements: JDK 21, Android SDK 36, SDK build tools, network access for the initial Gradle download.

```sh
export JAVA_HOME=/path/to/jdk21
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Set your SDK path in an untracked `local.properties` if needed. The release signing configuration loads `$BGCHECK_SIGNING_PROPERTIES`, or `~/.local/share/background-check/signing.properties` by default:

```properties
storeFile=/absolute/path/to/release.jks
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

Keep that keystore and its credentials private and backed up; updates must use the same signing certificate. Without the configuration, Gradle produces an unsigned release APK. `assembleDebug` can use the usual Android debug key if no signing configuration is available.

## Device verification

The test suite covers shell validation, resource and battery parsing, unavailable-versus-zero readings, sorting fields and directions, policy changes and recovery, and force-stop identity/readback safeguards. Compose tests exercise rendered sort order, search/filter retention, page navigation, preview limits, Undo targeting and force-stop confirmation. Device tests use only `dev.bgcheck.fixture`, a disposable APK with a test activity, a separate worker service and no requested permissions, for mutations. They exercise the Shizuku transport, resource reads, restrictions, Doze exemptions, restoration and force stop.

See the [1.1.1 list-sorting verification record](docs/list-sorting-verification.md) for build, test, device and delivery evidence and outstanding limitations. The historical [1.1.0 resource-monitor verification record](docs/resource-monitor-verification.md) and [1.0.0 verification record](docs/verification.md) document earlier versions and do not establish verification of the new sorting features.

```sh
./gradlew :fixture:assembleDebug :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r fixture/build/outputs/apk/debug/fixture-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Open the app and authorize Shizuku first.
./gradlew :app:connectedDebugAndroidTest
adb uninstall dev.bgcheck.fixture
```

Do not run the fixture mutation test against any other package.

## Structure

- `ui/`: Compose screens and shared visual components
- `data/`: models, inventory, resource/battery collectors and parsers, diagnostics, policy/force-stop operations and local history
- `shell/`: bounded, validated Shizuku UserService transport
- `BackgroundViewModel.kt`: foreground sampling, bounded session history, selection and action lifecycle
- `fixture/`: disposable package used exclusively for tests
- `docs/`: design, plan and verification record

This project is independent of the Cana app in the neighboring repository and uses the same automatic signed GitHub-release and Obtainium update flow. App changes pushed to `main` publish `1.1.1-bgcheck.N` releases; the existing [1.1.1 APK](artifacts/background-check-1.1.1.apk) is the historical local baseline.

[List sorting verification](docs/list-sorting-verification.md) · [1.1.0 resource monitor verification](docs/resource-monitor-verification.md) · [1.0.0 verification](docs/verification.md) · [Live monitor](artifacts/screenshots/monitor-live.png) · [Process details](artifacts/screenshots/monitor-process-detail.png) · [Battery monitor](artifacts/screenshots/monitor-battery.png)

Platform references: [Shizuku API](https://github.com/RikkaApps/Shizuku-API), [Android battery policy](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android15-release/src/com/android/settings/fuelgauge/BatteryOptimizeUtils.java), [Doze implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/apex/jobscheduler/service/java/com/android/server/DeviceIdleController.java).
