# Inspection improvements verification

This change adds independent access/firewall status, saved battery comparisons, a unified change history with Network Undo, persistent list preferences and pins, and compact Network lists.

## Automated evidence

- JDK 21 / Android SDK 36 unit suite: **244 tests**, 33 suites, no failures, errors or skips.
- Comparison tests cover missing counters, battery-history resets, backwards counters, shared UIDs, profile/install identity changes, persistence, wall-clock corrections and reboot boundaries.
- Network history tests cover exact raw Android enabled overrides, conflicting current settings, stale identities, interrupted changes, durable recovery records and failure to verify a command's result. A successful shell command with an unverified result now produces a warning and refreshes the package list.
- Protection summary tests distinguish Shizuku access from actual firewall state and use the actual active backend after a failed switch. Pin tests bind a pin to its profile serial, UID and installation time.
- The required `:app:testDebugUnitTest :app:lintRelease :app:assembleRelease` gate passed on the final source, along with matching debug and instrumentation APK builds. Release lint reports **zero errors and 319 warnings**, without a suppression baseline.
- The local signed/minified APK passes `apksigner verify`; its certificate SHA-256 is the established `3d770ebfa7c2eacd7735adc9fadca193b62e59424659aed58afe2243c1879951`. The default local version remains `1.1.1` / code 3.

## Device scope

Only `dev.bgcheck.fixture` is used for package or saved-rule mutation tests. Tests restore its original rule and exact Android enabled override. The package cleanup uses an independent identity-guarded restore even when the change is left in recovery.

The first dedicated software-emulator attempt crashed Android's system process. That interrupted run is not counted as an application test result. An inflation-only compact-layout regression was reproduced on API 35 without launching an Activity or changing its foreground app; full interaction tests require an isolated device.

The Motorola was reachable through Tailscale, but its previous wireless ADB endpoint refused connections. A current wireless-debugging address was requested. This change does not claim new physical-device, Obtainium, OEM reboot, Tailscale coexistence or work-profile pause/resume evidence until those checks run. Earlier release and profile evidence remains in the linked verification documents and is not counted as a fresh check of this release.

## Reproduction

Run the repository's required release gate with JDK 21 and SDK 36:

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

After installing the fixture and app/test APKs on an isolated emulator, authorize Shizuku for the runtime tests. Select the device explicitly:

```sh
adb -s EMULATOR_SERIAL shell am instrument -w -r \
  -e class dev.bgcheck.app.InspectionUiTest,dev.bgcheck.app.CompactNetworkUiTest \
  dev.bgcheck.app.test/androidx.test.runner.AndroidJUnitRunner
adb -s EMULATOR_SERIAL shell am instrument -w -r \
  -e class dev.bgcheck.app.InspectionHistoryRuntimeTest,dev.bgcheck.app.InspectionPersistenceRuntimeTest \
  dev.bgcheck.app.test/androidx.test.runner.AndroidJUnitRunner
```

`InspectionPersistenceRuntimeTest` verifies a new Activity and ViewModel in the same process. A separate force-stop/relaunch check is required to establish cold-process persistence. UI tests capture screenshots under the app's external `inspection-captures` directory for normal and large-font inspection.

## Release evidence

The existing main-branch workflow assigns the version, verifies the established signing certificate, and publishes the private release. No default version or signing key changes are included. Release URL, artifact checksum, certificate and installation evidence will be recorded after publication.
