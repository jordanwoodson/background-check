# Extensive process and resource monitoring

The user wants to identify exactly what is running and consuming CPU, memory and battery, then manage the responsible apps. The existing Android/Compose app uses Shizuku and already supports reversible background policy controls. Expand that existing flow with a dedicated Monitor page and richer app details.

## Required behavior

- Observe all shell-visible processes, including native/system processes, with PID, parent PID, UID, process name, command, scheduler state, sampled CPU and resident/virtual memory. Search and sort by CPU, RSS, name or PID; filter app processes versus system/native processes. Never treat a missing metric as zero.
- Show normalized whole-device CPU, available/total RAM and swap. Retain a bounded foreground session history for a trend. Offer manual refresh, pause, and adjustable 3/5/10 second foreground polling. No service monitors in the background; no overlapping polls.
- Show Android's per-UID estimated battery usage in mAh, total/component estimates, time window, cumulative CPU time, network traffic and partial wakelock time when present. Keep the historical battery sample separate from the fast live sample and refresh it manually or at a slower cadence. UID sharing must be visible and must not be double counted.
- Show device battery current and estimated instantaneous power flow when the hardware exposes current and voltage. Explain direction and scope; this is whole-device battery flow, not per-process watts.
- App details connect process measurements and UID battery usage to existing controls and show detailed PSS memory and running services on demand. Unknown/OEM unsupported data must explain unavailability without hiding successful readings.
- Add confirmed force-stop for ordinary unprotected apps. Revalidate package identity/user/shared UID before sending a bounded allowlisted command, then read back stopped state and process observation. Refuse system, core, shared or protected apps. Explain loss of work/notifications and that force-stop has no Undo; preserve existing policy Undo.

## Architecture and interpretation

Use pure models/parsers and suspend telemetry collectors over the existing validated Shizuku transport. Only fixed read commands and strictly validated package/user arguments are accepted; no general shell. Bounded output, timeouts and cancellation remain. CPU is an interval sample (one core = 100% per process, normalized to 100% for device); RSS counts shared resident pages and is not additive app-private memory; PSS apportions shared memory. Power attribution is an Android UID estimate, not direct per-process power measurement. Battery CSV uses read-only `-c`, never `--checkin` or reset.

## Verification

Use hand-checked parser and policy fixtures for unknown data, zero versus unavailable, multiple samples, large values, UID sharing and malformed formats. Validate actual shell formats on the connected Motorola Android 16 device, then exercise collectors through Shizuku and UI with instrumentation. Mutating tests target only dev.bgcheck.fixture. Build debug/release, run unit tests/lint/device tests, inspect screenshots including large text, deliver signed APK and record evidence and limitations.
