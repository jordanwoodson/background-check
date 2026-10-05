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

The first dedicated software-emulator attempt crashed Android's system process. Later API 35 and 36 attempts hit Android startup ANRs while verifying bytecode, with the same timeouts affecting system apps. Those interrupted runs are not counted as passed app tests. Precompiling the installed debug and test APKs with `cmd package compile -m speed -f` allowed the isolated API 35 runner to start normally.

The four Overview/Monitor UI cases passed at both **100% and 200% font size**: independent access/firewall status and pin interaction, combined history search and export callback, saved preference restoration, and a saved comparison while disconnected. Screenshots were visually inspected. The compact-layout controls also passed their 48dp/full-label assertions for both font sizes, and the independent list preferences passed fresh Activity launches at both sizes. Each font configuration passed these six cases.

The first fixture-row case timed out before its density assertions. Logs show Android returned 246 owner-profile packages, then the existing bundled package-safety asset was still loading when the test ended. This was an incomplete scan, not evidence of a missing inventory permission or a completed empty list. A separate real metadata check passed after the safety loader parsed its 4,983 records in 47.505 seconds on the software emulator; the original UI wait was 20 seconds. Running that metadata check before the compact row case in the same process passed at **both font sizes**, using real data. The row test verifies details, complete titles, 48dp controls, recycled visibility and selection across density changes. All seven UI cases therefore passed at both sizes; the row checks establish behavior after data loads, not fast cold inventory loading on the software emulator.

Rendered test fixtures: [normal Overview](../artifacts/verification-inspection/inspection-captures/overview-protection-font1.0.png), [large-font Overview](../artifacts/verification-inspection/inspection-captures/overview-protection-font2.0.png), [pinned detail](../artifacts/verification-inspection/inspection-captures/pinned-detail-font1.0.png), [history search](../artifacts/verification-inspection/inspection-captures/combined-history-font1.0.png), [saved comparison](../artifacts/verification-inspection/inspection-captures/saved-comparison-disconnected-font1.0.png). These screenshots use explicit test data and do not establish live battery measurements.

Two additional runtime tests passed on API 35 without Shizuku: saved firewall-rule Undo restores its verified snapshot and refuses conflicts, and new Activity/ViewModel instances restore the real fixture pin, monitor interval and saved comparison. The tests restore their original settings afterward. Android package enable/disable Undo is a separate privileged check, not established by the saved-rule test.

Automatic approval review rejected starting Shizuku on the disposable API 35 emulator as an insufficiently authorized privileged service. Explicit user approval was requested; the starter was not retried. Privileged package Undo and fresh battery sampling checks remain pending that approval.

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

[Release 1.1.1-bgcheck.13](https://github.com/jordanwoodson/background-check/releases/tag/1.1.1-bgcheck.13), Android version code **16**, was automatically published from `e23ab6f7c66454f5ba748ab09e67914060bc1380`. [Workflow 37261977933](https://github.com/jordanwoodson/background-check/actions/runs/37261977933) passed its tests, release lint, signed build, certificate/version checks and publication.

The published APK was downloaded independently. `sha256sum -c SHA256SUMS.txt` and `apksigner verify` passed; the certificate matches the established fingerprint above. APK package/version readback reports `dev.bgcheck.app`, `1.1.1-bgcheck.13`, code `16`.

Published APK SHA-256: `b22777c951bfec36fc09e0c6790f930390bab89f280072a1bead356601bd1eee`.

The downloaded release APK installed successfully over the signed debug verification build on isolated API 35 emulator `emulator-5564`. Android's package readback changed from code 3 to code 16 while preserving `firstInstallTime` (`2026-10-04 22:10:04`). The published app launched successfully and showed Shizuku disconnected, firewall off and no active backend, consistent with this emulator's actual state. [Published Overview](../artifacts/verification-inspection/published-overview-font1.0.png).

A separate force-stop/relaunch returned `Status: ok`, `LaunchState: COLD`, and restored the selected Apps tab and the Name/Descending preference set through the published UI. This establishes cold-process persistence for those preferences. [Retained sort](../artifacts/verification-inspection/published-cold-sort-font1.0.png). The published Overview also retained all six saved history records from before installation and displayed the fixture rule's before/after values. [Retained history](../artifacts/verification-inspection/published-retained-history-font1.0.png). These are real persisted emulator records, not the synthetic UI screenshots above. The emulator's original 200% font setting was restored after this smoke check.

No default version or signing key changes were made. Only the existing workflow created the release tag and published the APK. Phone installation and Obtainium delivery remain pending a reachable wireless-debugging address; the emulator installation does not establish them. Privileged package Undo and fresh battery sampling also remain pending the explicit Shizuku approval described above.
