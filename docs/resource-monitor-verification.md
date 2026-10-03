# Background Check 1.1.0 verification

Verified October 3, 2026 on Motorola Edge 2025 (`oulu`), Android 16/API 36. This update expands the existing process-state and background-policy app with live resource monitoring, historical power attribution, diagnostics and verified force stop.

## Delivered release

- `artifacts/background-check-1.1.0.apk`: 2,649,984 bytes, version 1.1.0 / code 2, min API 30, target API 36.
- SHA-256: `6d1d207876aa8a1bcc7ca1253829e963d0ffac1d095e54a82bf2b7be84071da3`.
- APK Signature Scheme v2 verified; same signing certificate as 1.0.0 (`3d770ebfa7c2eacd7735adc9fadca193b62e59424659aed58afe2243c1879951`).
- Installed release `base.apk` hash matches the delivered artifact. Package flags confirm the installed release is not debuggable.
- Phone copy: `Download/Background-Check-1.1.0.apk`.
- Disposable fixture and instrumentation packages were uninstalled. Only fixture history entries were removed; other app history was preserved.

## Requirements and evidence

| Requirement | Current evidence |
| --- | --- |
| Identify actual running processes | Live Toybox second-frame sample, including native/system processes: PID, parent PID, numeric UID, user, command, scheduler state, CPU, RSS and virtual memory. Real device test observed 914 processes and distinct activity/worker PIDs for the fixture. |
| Measure CPU and memory | Real Shizuku test observed fixture main CPU 24.0%, RSS 175,928 KiB plus worker RSS 94,220 KiB, and package PSS 160,614 KiB. Whole-device CPU/core count, MemAvailable, total RAM and swap were validated. Values include Android runtime overhead, not just fixture allocations. |
| Attribute power/resource history | Real Shizuku test read Android battery total 849.0 mAh and UID/component records, battery duration, CPU time, traffic and partial wakelocks. Parser tests pin CSV field offsets, large byte counts, units, unavailable values and UID deduplication. Device power flow uses BatteryManager current with battery voltage; signed unit conversion is tested. |
| Search, filter, sort and drill down | Compose device tests search by PID, inspect the full command, open its matching app, filter system/native processes, and verify numeric PID sorting. CPU/RSS/name sorting uses explicit descending/ascending comparators with missing metrics last. |
| Diagnose app processes and services | Device test required two processes sharing the fixture UID and exact service `dev.bgcheck.fixture/.ProbeService` in `dev.bgcheck.fixture:worker`, with non-foreground state. PSS/private dirty/swap PSS and recognized service output were checked. |
| Manage the responsible app | Device test force-stopped only the fixture after identity checks, then verified the package stopped flag through both dumpsys and PackageManager, absence of both processes in a later live sample, and absence of the service. UI tests verify cancel does nothing, confirmation invokes stop, and shared identities cannot be stopped. |
| Keep existing policy controls and Undo | DevicePolicyTest and UiFlowTest still pass: fixture background restriction, Doze exemption, readback, restoration and UI Undo. Existing recovery/history tests remain green. |
| Foreground-only sampling and pause | MonitorLifecycleTest verifies pause keeps the snapshot stable, manual refresh works while paused, automatic sampling stops in the background and resumes on return. A separate regression confirms a pending refresh cannot start new app diagnostics after backgrounding. |
| Safe command and connection handling | Fixed command allowlist, bounded raw/retained output and timeouts. No general shell or PID-kill API. Battery reduction retains only relevant records and never resets/consumes battery history. ShizukuReconnectTest completes three immediate connect/read/close cycles. |

## Automated checks

Final build command:

```sh
JAVA_HOME=/tmp/cana-jdk21 GRADLE_USER_HOME=/tmp/cana-gradle \
PATH=/tmp/cana-jdk21/bin:$PATH ./gradlew \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest \
  :fixture:assembleDebug :app:assembleRelease :app:lintRelease
```

Result: **BUILD SUCCESSFUL**. **76 unit tests pass**; **12 real-device instrumentation tests pass** in one full run. Lint: **0 errors, 11 warnings** (dependency suggestions, Compose conventions and KTX suggestions). Gradle/Kotlin also report pre-existing migration deprecations and two harmless redundant non-null assertions. Logs and JUnit XML are under `artifacts/verification-1.1.0/`.

The device tests caught issues that were corrected and rerun: Android ICU requires escaped literal closing braces; Shizuku 13.1.5 retains a local connection cache when removing a worker with `unbind(..., true)`, so it must first detach with `false`; nested horizontal chip rows were difficult to reach through scrolling, so controls now wrap into their own list items. Independent review also found and verified fixes for incomplete service records and diagnostics starting after backgrounding. Final power inspection caught a plugged-in phone at its charging limit being mislabeled as on battery; a regression test now distinguishes plugged idle, charging, full, discharging and unknown states.

## Release inspection

The installed signed release was inspected on the phone after the full test run. Its battery card correctly displayed **Plugged in · not charging**, 80%, and zero battery flow while Motorola's charging limit held the battery at 80%. Both the live monitor and its wrapped controls were also inspected at font scale 1.5; the original 1.0 setting was restored.

Terminating only Background Check's Shizuku worker changed the release to **Disconnected**, disabled sampling actions, and replaced CPU/RAM/swap readings with **Unavailable**. **Access → Check connection** restored the connection and produced a fresh sample (8 cores, 16.8% CPU and 2.80 GiB available RAM in the captured frame). The app was left connected on Monitor. The check transcript and UI hierarchy captures are under `artifacts/verification-1.1.0/`.

Screenshots: [live monitor](../artifacts/screenshots/monitor-live.png), [process list](../artifacts/screenshots/monitor-processes.png), [process details](../artifacts/screenshots/monitor-process-detail.png), [app diagnostics](../artifacts/screenshots/monitor-app-diagnostics.png), [battery flow](../artifacts/screenshots/monitor-battery.png), [battery ranking](../artifacts/screenshots/monitor-battery-ranking.png), [disconnected](../artifacts/screenshots/monitor-disconnected.png), [large text](../artifacts/screenshots/monitor-large-text.png), [large-text controls](../artifacts/screenshots/monitor-large-text-controls.png).

## Measurement limits

- Process CPU uses one core = 100%; device CPU is normalized across cores and excludes idle/I/O wait. Sampling adds overhead, including the observer and `top` process.
- RSS includes shared resident pages and virtual size includes reservations. Neither is private app memory. PSS apportions shared memory; Android's package query can include other profiles and shared processes that load the package. The UI states this scope.
- Battery values are Android estimates by UID and component over the displayed history window, not direct per-process watts. Shared UIDs cannot be split reliably between packages. Component and UID totals overlap. Unnamed Android power components are combined without dropping their contributions.
- Current/power flow is for the whole battery; hardware support and accuracy vary. Positive is into the battery, negative is out. Missing readings stay unavailable. A plugged-in phone may draw zero battery current while its adapter supplies power; charging state is read separately from Android status and external-power extras.
- App attribution uses installed-package UIDs. Isolated processes, other users and native services can have no matching package in this profile. All shell-visible processes remain inspectable.
- The real-device verification covers this Motorola/API 36 build. Android/OEM output variants that are not recognized produce unavailable readings. Per-process disk I/O was denied to the shell and is not claimed.

## Source contracts

- [Android 16 Toybox ps/top implementation](https://android.googlesource.com/platform/external/toybox/+/refs/tags/android-16.0.0_r1/toys/posix/ps.c): numeric RSS/VSZ units, interval samples, and the TCNT option's implicit thread enumeration (deliberately avoided).
- [Android BatteryStats CSV implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/core/java/android/os/BatteryStats.java): `bt`, `pws`, `pwi`, `cpu`, `awl`, `nt` record fields.
- [BatteryStatsService](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/services/core/java/com/android/server/am/BatteryStatsService.java): read-only `-c` with a package argument suppresses history while retaining UID aggregates; `--checkin` can consume completed history and is not used.
- [BatteryManager](https://developer.android.com/reference/android/os/BatteryManager): current units, sign and unsupported-property sentinel.
