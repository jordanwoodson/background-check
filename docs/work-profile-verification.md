# Work-profile support and verification

Background Check identifies each app by package and Android user, and validates its full UID and profile serial before mutations. Personal and work copies remain separate throughout Overview, Apps, Monitor, details, history, Android Settings, Network shortcuts, package management and saved firewall rules.

Profile filters retain separate selections and labels. Process, battery-UID and service attribution follows the selected profile; device CPU, RAM and battery-component totals are explicitly whole-device readings. Memory is collected from the selected UID's PIDs and rejected if those identities change during the read. Related packages and icons use the selected profile.

Paused, locked, deleted or recreated profiles cannot authorize a stale action or Undo. An unreadable profile produces a warning while readable profiles remain available. Profile and package callbacks refresh inventory and firewall state. History and firewall storage migrate existing records without substituting another profile.

## Android scope

| Feature | Scope |
| --- | --- |
| Background app-ops, standby bucket, force-stop, package actions | Selected Android profile; protected/shared-UID safeguards still apply. |
| Doze exemptions | Shared across copies of the package. Change and Undo confirmations explicitly disclose this scope. |
| ConnectivityManager firewall | Independent full-UID rules on devices exposing the required read/write APIs. Checks rule readback, journals previous state, and preserves unrelated OEM rules. Unsupported devices offer root or a VPN in the target profile. |
| Root iptables | Full-UID enforcement across profiles; separate network and LAN controls remain available. |
| Local VPN | Protects the profile where Background Check is running. Other-profile rows explain that they are unreachable with this backend. |
| NetworkPolicyManager | Full-UID policy across profiles; stock Android only restricts metered background data. It is not presented as complete internet blocking. |
| Rule backups and package lists | Include profile identity. Legacy name-only removal lists require explicit target-profile selection. Recreated/unavailable profiles are rejected before removal. |
| Captive portal, app appearance, tile and widget | Existing device/app settings and firewall-session scope; they do not imply separate per-profile settings. |

Enterprise administrators can restrict profile operations. The app reports a refused or unavailable operation; it cannot bypass device-management policy.

## Managed-profile evidence

Tests use disposable Android 15/API 35 and Android 16/API 36 emulators, genuine managed user 10, and `dev.bgcheck.fixture` installed in personal user 0 and work user 10. Privileged shell tests use Shizuku authorized through its real permission dialog. HTTP probes use the test host at `10.0.2.2:8765`.

- Host runtime: inventory reads both genuine UIDs and serials; work background restriction and persisted Undo leave personal policy unchanged; stopping the work worker preserves the personal worker's PID; work memory/services exclude the personal copy; stopped-profile selections are rejected. Four tests passed on each API.
- Settings navigation: both host and Network shortcuts open the real Android Settings activity as user 10.
- ConnectivityManager: work-blocked/personal-allowed and personal-blocked/work-allowed verified with actual HTTP on both APIs. A newly constructed backend restores both original UID rules and chain state. Existing active OEM denials survive stop; inactive foreign denials prevent activation. Four Network profile tests passed on each API, including retained/work-only metadata and Settings navigation.
- Root profile isolation: actual HTTP work-blocked/personal-allowed and the inverse passed on API 36; a new backend instance removed its kernel rules and restored traffic for both copies.
- Full firewall service: saved-rule block, unblock, re-block and stop passed with actual traffic on API 36 using ConnectivityManager and root iptables. The root run also verified screen-off rules, LAN blocking and automatic repair after deliberate kernel-rule deletion.
- Network persistence and stale actions: the final API 35 run passed eight tests. The production 6→7 Room migration preserves existing flags/timestamps, stamps visible profile serials and leaves departed profiles inaccessible. Transactional replacement rollback survives reopening. Stale UID/serial selections reject disable, uninstall, reinstall, force-stop and firewall writes; valid Work-only disable leaves Personal enabled.
- Package-list imports: seven codec tests and three API 35 runtime/UI tests passed. Actual exported Work-only and mixed-profile lists preserve target UIDs in Settings previews; legacy files show an explicit Work/Personal picker and profile-labeled confirmation. Tests cancel previews without removing apps.
- UI: 39 API 35 tests passed, including seven work-profile regressions and existing sorting, navigation and recommendation coverage. A deliberate package-only association mutation failed the new regression; restoring profile-aware lookup passed.
- Personal runtime regression: policy and resource/force-stop tests passed on API 36. One earlier combined run produced an unrecognized post-stop services snapshot; the fresh APK passed both the standalone resource test and the complete six-test host runtime run. Unknown output remains unavailable rather than being reported as zero.

Unit coverage includes duplicate package identities, UID/serial mismatches, legacy history, profile snapshot isolation, UID/PID memory races, shell validation, profile-aware backup/list formats, and UID firewall recovery. Independent review identified retained-list filtering, stale Network action identity, own-profile mutation fallback, profileless list imports and partial inventory handling; those findings were addressed before delivery.

The signed, minified APK installed successfully as an update on API 36. A UI smoke check verified the real Work fixture row, readable Work policy detail and host shortcut opening the Network control sheet labeled `Work (user 10) · dev.bgcheck.fixture`. Screenshots were visually inspected: [signed Work inventory](../artifacts/verification-work-profiles/signed-work-inventory-api36.png) · [signed Work detail](../artifacts/verification-work-profiles/signed-work-detail-api36.png) · [signed Work Network sheet](../artifacts/verification-work-profiles/signed-network-work-api36.png).

Rendered test fixtures: [Work filter](../artifacts/verification-work-profiles/apps-work-filter.png) · [Work detail](../artifacts/verification-work-profiles/work-app-detail.png).

The existing [Network verification](network-controls-verification.md) records prior backend, reboot, release and hardware-specific evidence. Managed-profile emulator results do not establish every OEM's enterprise restrictions, clone-profile behavior or physical cellular/roaming transitions.

## Reproduction

Provision a disposable managed-profile emulator, install the fixture in users 0 and 10, install the debug app and test APK, authorize Shizuku, and serve the HTTP test fixture on port 8765. Select the device explicitly:

```sh
adb -s EMULATOR_SERIAL shell am instrument -w -r \
  -e class dev.bgcheck.app.WorkProfileRuntimeTest,dev.bgcheck.app.NetworkWorkProfileRuntimeTest \
  dev.bgcheck.app.test/androidx.test.runner.AndroidJUnitRunner
```

The required release gate uses JDK 21 and Android SDK 36:

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

The final gate passed **176 unit tests**, release lint (**0 errors**, 305 warnings and 3 hints), and the signed/minified release build. Lint findings remain visible without a suppression baseline. The APK certificate was verified against the established SHA-256 fingerprint:

`3d770ebfa7c2eacd7735adc9fadca193b62e59424659aed58afe2243c1879951`

Default versioning and the established signing key remain unchanged. The existing workflow assigns the release version and publishes the signed APK when `main` is pushed.
