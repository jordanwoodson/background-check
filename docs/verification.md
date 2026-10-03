# Background Check 1.0.0 verification

Verified October 3, 2026 on Motorola Edge 2025 (`oulu`), Android 16/API 36, 1220×2712 display, effective density 382. ADB connected over Tailscale at `100.69.155.13:38649`; that wireless-debugging port can change. Shizuku 13.6.0 was already running and Background Check received authorization through its standard permission dialog.

## Delivered artifact

- `artifacts/background-check-1.0.0.apk`, 2,486,144 bytes; R8/resource shrinking enabled.
- Installed package `dev.bgcheck.app`, version 1.0.0 / code 1, min API 30, target API 36.
- Non-debuggable release; APK Signature Scheme v2 verification passed.
- APK SHA-256: `ce264589849d9f38d77e3e0cdce1788da58199d6d9e5bf95c656d20839147d9f`.
- Installed `base.apk` SHA-256 equals the delivered artifact.
- Signing certificate SHA-256: `3d770ebfa7c2eacd7735adc9fadca193b62e59424659aed58afe2243c1879951`.
- Phone copy: `Download/Background-Check-1.0.0.apk`.
- Persistent signing key and private configuration are stored outside the repository in `~/.local/share/background-check/`. Retain them for compatible future updates.

## Requirements and evidence

| Requirement | Evidence |
| --- | --- |
| Native Android APK for the Motorola over Tailscale | Signed artifact, successful ADB installation on named device, matching installed hash and package metadata. |
| Polished UI | Actual release screenshots in `artifacts/screenshots/`: overview, app list, app details, Access, and overview at font scale 1.5. Dark theme, readable system bars, scrollable content, large controls, native app icons, meaningful empty/loading/error states. |
| Display background state | Actual release overview showed foreground, service, background and cached app counts from a current Android process snapshot. Timestamped individual process state and standby group were inspected for a real app. DevicePolicyTest requires a recognized nonempty process map. |
| Display battery and app battery policies | Actual battery level, charging state, temperature and saver displayed. Live per-app background mode, standby bucket and effective Doze exemption read through Shizuku; no fabricated drain estimates. |
| Manage app background/battery settings | DevicePolicyTest changed only the disposable fixture's background app-op and Doze exemption, read back each change, and restored the original policy. UiFlowTest exercised search, cancel confirmation, confirm restriction, and Undo. |
| Shizuku / wireless debugging access | Shizuku UserService on the device, standard authorization, bounded validated commands. The supported backend is Shizuku, which starts via wireless debugging; there is no independent in-app ADB client. |
| Safe changes and recovery | Unit tests cover validation, unknown reads, failed readback, stale changes, partial writes, rollback, UID overrides/system exemptions, and protected identities. Durable checkpoints run after preflight and before both apply and undo writes; interrupted recovery has conflict detection. |
| Disconnect handling | Stopped only `dev.bgcheck.app:bgcheck_shell` in the installed release. Access showed the explicit disconnection; Check connection spawned a new service and returned to Connected. Global Shizuku was left running. |
| Final device cleanup | Disposable fixture and instrumentation APK removed. Only fixture history records removed, preserving other history. Font scale restored from 1.5 to original 1.0. Final app left installed and open on Overview. |

## Automated verification

Final build command (with JDK 21, SDK 36, and existing Gradle cache):

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintRelease
```

Result: **BUILD SUCCESSFUL**. All **34 unit tests pass**: PolicyEngine 13, ShellParsers 10, bounded process execution 5, command validation 2, history 4. Lint: **0 errors, 11 warnings** (dependency update suggestions, Compose parameter conventions, KTX suggestions). Evidence is copied under `artifacts/verification/`.

Final live tests ran through `adb shell am instrument -w -r dev.bgcheck.app.test/androidx.test.runner.AndroidJUnitRunner`. Result: **OK (2 tests)**. Test mutations targeted only `dev.bgcheck.fixture`; shell readback afterward was `RUN_ANY_IN_BACKGROUND: allow` and no user Doze exemption. Fixture was then uninstalled.

The live tests caught an Android ICU regex compatibility issue that JVM tests did not: the package-list pattern needed an escaped closing brace. The corrected parser passed the real-device snapshot test. The first UI rerun also exposed an asynchronous search-result timing issue; the test now waits for the actual result and uses the app's Search keyboard action. The final two-test run passed.

An independent source review found and verified fixes for interrupted-action journaling, checkpoint timing relative to stale-state validation, interrupted undo recovery, selection changes during failed actions, and inconsistent UI/backend guards. No remaining material finding in the final review.

## Limits represented in the UI

Process residency is not an energy-consumption measurement. App-op policy, standby buckets, and Doze exemption are separate Android mechanisms; no exact equivalence to Motorola's battery selector is claimed. Android/system-provided exemptions and protected/shared identities are not modified by quick controls. Other Android versions and OEMs have not been device-tested; unexpected command output produces an unavailable reading and disables unsafe operations. Shizuku may need restarting after reboot.
