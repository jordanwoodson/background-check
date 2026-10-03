# Resource Monitor Implementation Plan

**Goal:** Expand process inspection and management with live CPU/memory, historical battery attribution, detailed diagnostics and verified force-stop.

**Architecture:** Pure resource models and parsers over the existing bounded Shizuku transport; foreground polling owned by the view model; a Monitor page and app-detail resource cards in Compose.

**Tech Stack:** Kotlin, Compose, Shizuku, Android 30–36, JUnit and Android instrumentation.

**Spec:** ../specs/2026-10-03-resource-monitor-design.md

## Constraints and review focus

Preserve existing policy controls/history. Never reset battery statistics. Use one-core CPU semantics per process, distinguish RSS/PSS, aggregate battery by UID. Partial source failures remain explicit. Detect stale identity before force-stop; device test mutations only use dev.bgcheck.fixture. Polling stops when the app leaves the foreground.

## Tasks

- [x] Collect authoritative device/source examples; define ResourceModels.kt contracts.
- [x] Add tested ResourceParsers.kt and ResourceMonitor.kt: live process/CPU/memory snapshot, historical energy snapshot, selected-app PSS/services diagnostics. CommandPolicy permits only the exact required commands. Verify multiple-frame parsing, unknown data, UID and units, service filtering and partial failures.
- [x] Add ProcessManager.kt and ProcessManagerTest.kt: protected/shared/mismatched identity refusal, bounded force-stop and observed-state verification.
- [x] Add MonitorScreen.kt and resource detail components. Include live/power modes, rankings, search, filters, trend, source windows, refresh/pause/rate and drill-down. Integrate UiState, BackgroundViewModel and MainActivity foreground lifecycle; add battery current/flow readings.
- [x] Test real telemetry and force-stop via Shizuku on disposable fixture; verify search/filter/sort/pause/drill-down and existing policy UI. Check unavailable/disconnect and large text behavior.
- [x] Run complete unit suite, build and lint; review changes independently; fix findings and package version 1.1.0 with verification report and screenshots.

## Execution record

Initial worktree was clean at 35635c0. Connected Motorola Android 16 is available. Device/source probe and isolated process-manager implementation run in parallel; root owns shared models, transport integration, view model and final verification. The user authorized ongoing implementation; routine design choices are recorded here without introducing additional approval gates.

Completed version 1.1.0: 76 unit tests and 12 real-device tests pass; release build succeeds and lint has 0 errors. Independent reviews and device runs drove fixes for service parsing, foreground-only diagnostics, Shizuku reconnects, reachable controls and plugged-idle battery state. Signed release installed and copied to Downloads; artifact hash matches installed APK. Live/battery/process/diagnostic, large-text and disconnect/reconnect checks are recorded in docs/resource-monitor-verification.md. Fixture packages and their history records were cleaned up without removing other app history.
