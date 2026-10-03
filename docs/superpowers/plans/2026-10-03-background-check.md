# Background Check implementation plan

**Goal:** Deliver and verify an Android APK that displays and manages app background and battery state on the Motorola Edge.

**Architecture:** Compose presentation, lifecycle ViewModel and local repository, isolated Shizuku UserService. Shell protocol is internal and validates all package/user inputs. Operations read before, mutate, read after, and preserve previous values for undo.

**Tech stack:** Kotlin 2.3.21, AGP 9.1.1, Compose, Material 3, Shizuku 13.1.5. Android API 30–36.

**Spec:** ../specs/2026-10-03-background-check-design.md

## Tasks

- [x] Scaffold Gradle/manifest/resources and data contracts; compile on existing local SDK.
- [x] Implement Shizuku transport and command/parsing policy layer with meaningful unit tests for malformed output, shell injection, missing access and unchanged/failed readback.
- [x] Implement repository: installed app inventory, live snapshots, detail reads, safe control mutations and persisted undo history; never display missing measurements as zero.
- [x] Implement polished Compose overview, searchable/filterable app list, detail controls, confirmation/undo, and actionable Access setup/recovery.
- [x] Build/test/lint; inspect screenshots at phone size and larger font; verify lifecycle and offline behavior.
- [x] Install on Motorola over Tailscale, authorize Shizuku, verify real snapshot and reversible action on disposable fixture; retain evidence and signed APK.

Completed evidence: ../../verification.md. The release targets API 36, has persistent recovery checkpoints for interrupted apply and undo operations, and is shrunk with R8.

## Review focus

Unknown/OEM shell output must remain unknown. Service death cannot leave active controls or a forever spinner. Critical connectivity/system packages must not be restricted by quick controls. Failed multi-step mutations must preserve recovery information. Process snapshot and battery policy are distinct and both visibly timestamped.
