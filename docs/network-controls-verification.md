# Network controls: feature coverage and verification

The original integration evidence below predates the full work-profile update. See [work-profile support and verification](work-profile-verification.md) for current profile scope, independent ConnectivityManager enforcement and managed-profile tests on Android 15 and 16.

Implemented 2026-10-03 from [De1984](https://github.com/dorumrr/de1984/tree/deff1691a1ee9a57baab8047885464b7d3a20209), pinned at `deff1691a1ee9a57baab8047885464b7d3a20209`. All 87 upstream Kotlin files are retained in the `network` library, together with resources, package-safety data, seven languages, and the MIT license. The host remains Android 11+; this does not extend Background Check to upstream’s Android 8 minimum.

## Features and evidence

| Feature | Integration and verification |
| --- | --- |
| iptables, ConnectivityManager, NetworkPolicyManager, VPN | All four backends imported. Actual HTTP allow → block → allow → stop traffic verified for VPN (API 35 and 36), shell-Shizuku ConnectivityManager (API 35), and root-Shizuku iptables (API 35). On stock Android, NPM’s metered-background policy was read back and foreground traffic correctly remained allowed. |
| AUTO/manual selection and privilege changes | Upstream selection, monitoring, fallback, VPN conflict detection and health UI retained. AUTO excludes limited NPM. Manual backend traffic tests exercise each implementation. |
| Wi-Fi, mobile, roaming, LAN and screen-off rules | Rule aggregation, offline network handling, roaming inheritance and screen conditions covered by unit tests. Live screen-off blocking passed with VPN on Android 16 and iptables on Android 15; root LAN blocking and removal passed against a private test-host address. |
| Individual/batch controls and default allow/block | Upstream individual, multi-selection and smart default-policy switching retained. Unit tests cover default policy, protected UIDs, strict shared-UID aggregation and all-network actions. |
| Kernel enforcement and recovery | Both IPv4 and IPv6 writes/readbacks are checked. Unit tests reject missing, duplicate and stale rules. Live test deletes a fixture DROP rule, observes failed verification, then confirms automatic health-check repair restores blocking. |
| Profiles, shared UIDs, critical apps and reinstall identity | All upstream handling retained. Unit tests cover profile/backend reach, critical defaults and mapping saved rules to the current UID. Live host self-disable is refused. Real managed-profile/shared-UID traffic remains a hardware test. |
| Package management | Enable, disable, force-stop, per-user uninstall, install-existing restoration, multi-select operations, search/filter/sort, safety labels and text export retained. Live Shizuku disable/enable/force-stop verified on the disposable fixture. Uninstall/system-package restoration was source-reviewed, not exercised against system apps. |
| Backup, restore and reset | Original JSON format retained with version/count/size/identity validation before privileged work. Merge/replace use a Room transaction. Unit tests validate format and reject hostile identities. On-device test reopens a saved database, then forces an insertion failure and proves replacement rolls back to the old rules. |
| Boot restoration and root boot protection | Receivers, WorkManager recovery and root boot scripts retained, with isolated `bgcheck` names. Actual Android 16 reboot restored a saved VPN block before the app was reopened. Magisk/KernelSU/Sui early-boot script execution is not established by an emulator. |
| New-app notifications and package changes | Upstream installation notifications, action receiver, rule cleanup and cross-profile package watcher retained. Manifest and source reviewed. |
| Quick Settings tile and widget | Both retained. Android system preferences entry opens a sanitized navigation activity; instrumentation proves an external injected start action cannot enable the firewall. |
| Captive portal controls | Presets, custom URLs, detection mode and original-setting restoration retained. Source reviewed; this task did not change a physical phone’s captive-portal settings. |
| Diagnostics, appearance and language | Logs/export, settings and seven languages retained. New NPM warnings translated into all inherited locales. Android 13+ app-language metadata restored. Dracula defaults and JetBrains Mono match the host; upstream theme controls remain available. |
| Host navigation | Fifth Network destination and per-app shortcut. Instrumentation verifies the shortcut clears conflicting filters, finds the actual package row by package ID, and opens Firewall, Packages and Settings. The new package-row assertion failed before the search fix and passed afterward. Back returns to the host. Normal and 1.3× font screenshots reviewed. |
| Releases and identity | Single launcher, `dev.bgcheck.app` application ID, host version inputs, existing signing configuration, and `network/**` automatic-release trigger. Kernel chains, boot files and intent actions are isolated from a separately installed De1984. |

## Verification performed

JDK 21 and Android SDK 36:

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

115 unit tests passed. The signed, minified APK was also installed as an update on API 36: its UI found the fixture by package ID, started the VPN, applied a Wi-Fi block verified by actual traffic, and restored traffic on stop. Rule application is asynchronous. Release lint includes library dependencies; the existing/imported lint warnings remain visible rather than hidden with a baseline. The signed, minified release uses the established certificate SHA-256:

`3d770ebfa7c2eacd7735adc9fadca193b62e59424659aed58afe2243c1879951`

The API 35 emulator regression run passed 53 tests covering existing monitoring, sorting, policy/Undo, force-stop, Shizuku reconnection, and new firewall/storage/navigation tests. The phone-specific inventory audit and screenshot-generation suite were excluded from that emulator run. The boot test requires an explicit phase and does no boot mutation in the ordinary suite.

Additional targeted runs passed root iptables recovery/LAN/screen-off and package/navigation tests (4), Android 16 VPN/screen-off/navigation (3), ConnectivityManager live traffic (1), final NPM limited-mode traffic/readback (1), storage rollback/reopen (1), and separate boot preparation/cleanup. Runtime traffic checks use only `dev.bgcheck.fixture` against the local host at `10.0.2.2:8765`.

To repeat the traffic test, serve a small nonempty file at port 8765 and install the fixture, debug app and instrumentation APK. Use a disposable emulator; backend-specific tests require the corresponding privileges:

```sh
python3 -m http.server 8765 --bind 127.0.0.1 --directory /path/to/test-content
adb shell am instrument -w -r \
  -e class dev.bgcheck.app.NetworkRuntimeTest -e backend VPN \
  dev.bgcheck.app.test/androidx.test.runner.AndroidJUnitRunner
```

Other backend values: `CONNECTIVITY_MANAGER`, `IPTABLES`, `NETWORK_POLICY_MANAGER`. The fixture mutation test never targets an ordinary installed app. Boot verification uses `NetworkBootRuntimeTest` with `boot_phase=prepare`, an external emulator reboot, a fixture probe before opening Background Check, and `boot_phase=cleanup`.

## Practical limits

VPN mode occupies Android’s VPN slot and requires “Block connections without VPN” to be off: allowed apps intentionally bypass the drop-only tunnel. NPM on standard Android limits metered background data, not Wi-Fi or foreground traffic; the banner and individual/batch controls describe that limit. ConnectivityManager/NPM expose a combined control, and profile/system-UID restrictions depend on the backend.

Some automatic privilege-change transitions retain upstream’s stop-before-start protection gap (approximately 1–2 seconds). Boot restoration without root happens after unlock, not before credential storage is available. Real cellular/roaming transitions, managed/clone-profile and shared-UID traffic, early-root boot protection, and full NPM blocking on a supporting custom ROM require those environments. Emulator results do not establish those hardware/ROM behaviors.

[App rules](../artifacts/verification-network/app-rules-release-api36.png) · [Firewall](../artifacts/verification-network/firewall-api36.png) · [Large text](../artifacts/verification-network/firewall-api36-large-text.png) · [Packages](../artifacts/verification-network/packages-api36.png) · [Settings](../artifacts/verification-network/settings-api36.png)

## Published release

[1.1.1-bgcheck.7](https://github.com/jordanwoodson/background-check/releases/tag/1.1.1-bgcheck.7), Android version code **10**, was automatically published from `f18554d8959c9c59263ccd8f1ba618eec2f882c2` on 2026-10-04. [Release workflow 37178685881](https://github.com/jordanwoodson/background-check/actions/runs/37178685881) passed test, lint, signed build, certificate and version checks. The published APK was downloaded separately and its signature, version and published SHA-256 checksum verified.

APK SHA-256: `40c2b657f0a93f6fccb13ad10690fc5940988b584b83ef66beb7fa97e5b13522`.

Obtainium can install this update from the existing private repository configuration. The default local version and established signing key were preserved. Tags and APK publication were performed only by the existing release workflow.
