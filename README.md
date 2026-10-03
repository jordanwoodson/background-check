# Background Check

A native Android app for understanding background app activity and controlling background access and Doze exemptions. Designed for the Motorola Edge, with a charcoal and lime Compose interface.

## Use

1. Install [the signed APK](artifacts/background-check-1.0.0.apk). Version 1.0.0 is already installed on the Motorola Edge 2025 and saved in its Downloads folder.
2. Open Shizuku and start it with **Wireless debugging** (or root).
3. Open Background Check → **Access** and authorize Shizuku.
4. Browse **Apps**, open an app, and inspect its background policy. Changes require confirmation and are read back from Android. Undo is available in **Overview → Your changes**.

Shizuku may need to be started again after a reboot. Background Check does not run a background monitor, upload data, or require an internet permission. Tailscale is used only to deliver and test the APK from the development machine; normal use is local on the phone. Direct in-app ADB pairing is not implemented; Shizuku is the supported privilege backend.

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

Unit tests cover shell command validation, process and policy parsing, stale-state refusal, failed readback, rollback, and history serialization. The instrumentation test uses only `dev.bgcheck.fixture`, a disposable APK with no permissions or components. It checks the actual Shizuku transport, snapshot, restrictions, Doze exemptions and restoration.

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
- `data/`: models, inventory, parsing, policy operations and local history
- `shell/`: bounded, validated Shizuku UserService transport
- `BackgroundViewModel.kt`: foreground refresh, selection and action lifecycle
- `fixture/`: disposable package used exclusively for tests
- `docs/`: design, plan and verification record

This project is independent of the Cana app in the neighboring repository.

[Verification record](docs/verification.md) · [Overview screenshot](artifacts/screenshots/overview.png) · [App detail screenshot](artifacts/screenshots/app-details.png)

Platform references: [Shizuku API](https://github.com/RikkaApps/Shizuku-API), [Android battery policy](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android15-release/src/com/android/settings/fuelgauge/BatteryOptimizeUtils.java), [Doze implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/apex/jobscheduler/service/java/com/android/server/DeviceIdleController.java).
