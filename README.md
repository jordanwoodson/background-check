# Background Check

A native Android app for inspecting running processes, CPU, memory and battery use, and managing background app activity. A Dracula terminal interface brings monospace typography, purple controls, cyan readings, green live status and dark bordered panels to every screen. Includes independent list sorting, resource monitoring, app diagnostics, confirmed force stop, background-access and Doze controls, plus a complete firewall and package-management workspace adapted from De1984. Designed for the Motorola Edge, with automatic signed updates through Obtainium.

## Use

1. In Obtainium, add `https://github.com/jordanwoodson/background-check` using a GitHub token with access to this private repository, as for Cana. Alternatively install the signed APK from the [latest release](https://github.com/jordanwoodson/background-check/releases/latest). See [release and Obtainium setup](docs/releases.md).
2. Open Shizuku and start it with **Wireless debugging** (or root).
3. Open Background Check → **Access** and authorize Shizuku. Once connected, setup instructions stay tucked inside **Connection help**. If permission was denied, use **Open Shizuku** to allow Background Check under Authorized applications.
4. Open **Monitor → Live processes** for whole-device CPU, RAM and swap, session trends, and shell-visible processes. Search by name, command, app, PID or UID; filter app versus system/native processes, and choose a sort order. Tap a process for its command, parent, UID and related apps.
5. Open **Monitor → Battery use** for current battery flow, component estimates and Android UID measurements, initially ranked by estimated mAh. Tap a UID for cumulative CPU time, traffic, partial wakelocks and related apps.
6. Browse **Apps** or open a related app from Monitor. App controls appear first; expand **Resource diagnostics** for processes, UID battery estimates, package memory and running services. Background-policy changes require confirmation and Android readback. Undo is available in **Overview → Your changes**.

7. Open **Network** for firewall rules, package management and network settings. An app’s **Network and internet access** button opens its matching firewall entry.

Use **All profiles**, **Personal**, or **Work** in Overview, Apps and Monitor to choose the Android profile to inspect. App rows, details and history identify the profile; two copies of one package remain separate. Work-only apps, retained-uninstalled packages, descriptions, recommendations, related apps, sorting and Android Settings shortcuts use the selected profile. Paused or locked profiles remain visible with an explanation; resume or unlock them before using controls.

Background-access changes, force stop and Undo target the selected installation. History records the Android user, UID and profile serial so a deleted/recreated profile cannot inherit an old change. Doze exemptions still apply across profiles, and both the change and Undo confirmation explain that scope. Device CPU, memory, battery flow and component totals describe the whole device even when Monitor's process or UID list is filtered.

Shizuku may need to be started again after a reboot. Resource monitoring runs only while its screen is open. When enabled, the firewall runs a foreground service with a status notification and restores its saved configuration after reboot. Background Check does not upload data or require an internet permission. Tailscale is used only to deliver and test the APK from the development machine; normal use is local on the phone. Direct in-app ADB pairing is not implemented; Shizuku is the supported privilege backend.

See the [Dracula terminal identity and screenshots](docs/dracula-theme.md), [Dracula verification](docs/dracula-verification.md), and [verified private GitHub / Obtainium setup](docs/release-setup-verification.md).

## Network and internet controls

**Network** owns internet firewall recommendations. Its firewall rows and control sheets explain why to allow, consider blocking or review access, accounting for shared/core UIDs and INTERNET permission. Network's retained-package sheets show reinstall guidance. Recommendations do not change rules automatically. See [internet recommendations and their sources](docs/internet-recommendations.md).

The **Network** workspace retains De1984’s four firewall backends, per-app and batch rules, Wi-Fi/mobile/roaming and screen-off controls, LAN blocking with root, allow-by-default/block-by-default policies, profile-aware app lists, package enable/disable/force-stop/uninstall/restore, profile-bound rule backup and package-list export/import, captive-portal settings, Quick Settings tile, widget, installation notifications, logs, appearance and language settings. The MIT source and attribution are included; see [provenance](network/UPSTREAM.md) and the [feature and verification matrix](docs/network-controls-verification.md).

**AUTO** selects iptables with real root, then ConnectivityManager with Shizuku on supported Android versions, then the local VPN. NetworkPolicyManager is manually selectable. On standard Android it restricts metered background data only; the app labels that limitation and does not claim full internet blocking. Full NPM protection depends on the ROM.

VPN mode occupies Android’s single VPN slot. It drops traffic from blocked apps locally; allowed apps bypass the tunnel. Keep Android’s **Block connections without VPN** disabled for this mode. Other VPNs require a privileged backend or an explicit switch. ConnectivityManager applies independent per-profile UID rules where the device exposes the required read/write APIs. Unsupported devices offer root or a VPN running in the target profile. VPN protects only its own Android profile. ConnectivityManager and NPM offer one combined internet control; independent network controls are available where the backend supports them. Shared-UID apps share enforcement, and the UI explains protected apps and profile/backend limits.

Saved rules persist across restarts. Ordinary boot restoration happens after unlock; optional early-boot protection requires a supported root environment. Some AUTO privilege-change transitions retain upstream’s short stop-before-start gap. No gap-free protection is promised during those transitions.

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

Live polling runs only while Monitor is open and the app is in the foreground. Expand **Sampling options** to choose **3, 5 or 10 seconds**, use **Pause / Resume**, or take one sample with **Refresh now**, including while paused. A maximum of 60 recent CPU/RAM samples stays in memory. Sampling itself adds overhead. Battery estimates refresh separately at a slower cadence, or with **Refresh battery estimates**, which also updates the local battery reading. Expand **Device readings**, **Session trend**, **Battery measurements** or **Device components** for the full detail. Live and battery views retain separate searches and scroll positions. Timestamps identify each source’s sample; app diagnostics are read when you open an app.

| Reading | Meaning |
| --- | --- |
| Device CPU | Interval usage normalized across all CPU cores, from 0–100%. |
| Process CPU | Interval usage where one fully used core is 100%; a multithreaded process can exceed 100%. |
| RSS / resident memory | Physical resident pages, including shared pages. Adding process RSS can count the same memory more than once. |
| Virtual memory | Address space, including reservations; it is not physical RAM consumption. |
| Available RAM / swap | RAM available to Android, including reclaimable pages, and swap usage/capacity. Swap may include compressed RAM (zram). |
| PSS / private dirty / swap PSS | Memory for observed processes belonging to the selected profile's UID: shared pages apportioned between processes, modified private pages, and apportioned swapped pages. Apps sharing that UID share these readings. A process change during collection makes the reading unavailable until refreshed. |
| Estimated mAh | Android’s cumulative battery estimate for its current history window. It is not instantaneous watts or a direct per-process energy measurement. History can reset after charging or a system reset. |
| CPU time / traffic / wakelocks | Cumulative UID measurements over that battery history. Wakelock duration is not energy; CPU time can exceed elapsed time across multiple cores. |
| Current / estimated power | Whole-battery flow: positive means charging, negative means discharging. Power is current × voltage. Zero flow while plugged in can mean the adapter is supplying the phone directly. Hardware support varies, and these figures do not attribute watts to an app. |

Battery rankings count each UID once. Several packages can share a UID, so the app labels that scope explicitly rather than assigning the same total independently to each package. App process lists match the full UID, including its profile; isolated processes can use another UID. Native services, inaccessible profiles, or removed packages may have no matching installed app, but still appear under their UID's profile filter. The device component and UID battery breakdowns overlap and must not be added together.

Unavailable or unsupported readings remain **Unavailable**, with warnings where available; they are not converted to zero. A sleeping or cached process still occupies memory without necessarily consuming CPU. Battery statistics are read without resetting Android’s history. Detailed commands and processes may be restricted by Android or the device vendor.

## App controls

**Apps** shows **Cana debloat recommendations** for system, user and retained-uninstalled packages. The same 4,801-entry Android Debloat List snapshot bundled with Cana supplies Recommended, Advanced, Expert and Unsafe ratings, package warnings and suggested replacements. Packages outside that list explicitly say **Not rated by Cana**. Removed packages retain their rating for review before restoring them. See [Cana recommendations and source provenance](docs/cana-debloat-recommendations.md).

Package rows explain what each package does. The bundled guide covers every app in the USB-audited Motorola inventory, including system, user and retained-uninstalled packages. Open a package for its purpose, publisher sources and associated packages: current shared UIDs, overlay targets, shared libraries, declared package queries and documented companions. Tap a recorded related package to inspect it. Search matches purpose text, names and package IDs. **Uninstalled** shows removed packages Android still retains for the selected profiles, with read-only details. Unidentified future packages remain explicit. See [package descriptions](docs/package-descriptions.md) for sources and coverage.

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

Work-profile behavior and managed-profile test evidence are recorded in [work-profile verification](docs/work-profile-verification.md).

The test suite covers shell validation, resource and battery parsing, unavailable-versus-zero readings, sorting fields and directions, policy changes and recovery, and force-stop identity/readback safeguards. Compose tests exercise rendered sort order, search/filter retention, page navigation, preview limits, Undo targeting and force-stop confirmation. Device tests use only `dev.bgcheck.fixture`, a disposable APK with a test activity, a separate worker service and a bounded HTTP traffic probe with INTERNET permission, for mutations. They exercise the Shizuku transport, resource reads, restrictions, Doze exemptions, restoration and force stop.

See the [UI polish verification record](docs/ui-polish-verification.md) for the current release sweep and the [1.1.1 list-sorting verification record](docs/list-sorting-verification.md) for build, test, device and delivery evidence and outstanding limitations. The historical [1.1.0 resource-monitor verification record](docs/resource-monitor-verification.md) and [1.0.0 verification record](docs/verification.md) document earlier versions and do not establish verification of the new sorting features.

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
- `network/`: MIT-attributed firewall, package management and network settings library
- `fixture/`: disposable package used exclusively for tests
- `docs/`: design, plan and verification record

This project is independent of the Cana app in the neighboring repository and uses the same automatic signed GitHub-release and Obtainium update flow. App changes pushed to `main` publish `1.1.1-bgcheck.N` releases; the existing [1.1.1 APK](artifacts/background-check-1.1.1.apk) is the historical local baseline.

[Dracula screens](docs/dracula-theme.md) · [Current overview](artifacts/verification-ui-polish/after/ux-captures/01-overview-720x1600-font1p0.png) · [Current monitor](artifacts/verification-ui-polish/after/ux-captures/06-monitor-live-720x1600-font1p0.png) · [Current battery view](artifacts/verification-ui-polish/after/ux-captures/09-battery-flow-720x1600-font1p0.png) · [Historical list sorting verification](docs/list-sorting-verification.md) · [1.1.0 resource monitor verification](docs/resource-monitor-verification.md) · [1.0.0 verification](docs/verification.md)

Platform references: [Shizuku API](https://github.com/RikkaApps/Shizuku-API), [Android battery policy](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android15-release/src/com/android/settings/fuelgauge/BatteryOptimizeUtils.java), [Doze implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/apex/jobscheduler/service/java/com/android/server/DeviceIdleController.java).
