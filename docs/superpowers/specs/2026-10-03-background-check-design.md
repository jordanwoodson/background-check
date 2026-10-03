# Background Check

Build a native Android APK for the Motorola Edge reachable over Tailscale. The app focuses on viewing and managing installed apps' background activity and battery policies through Shizuku, started via wireless debugging. Android 11+; local data only, no analytics or network permission.

The native Compose UI uses a charcoal/soft-lime palette, clear typography, large touch targets and bottom navigation: Overview, Apps, Access. Overview shows actual battery level/charging/temperature and observed process counts. Apps supports search/filter, labels/system status, process-state snapshots, standby buckets, and Doze exemptions. Detail presents separate background app-op and Doze controls with explanations, confirmation, readback verification, and undo. The app never claims that a running process proves energy drain or that these controls equal Motorola's battery-mode selector. System/critical app restrictions require protection; Android Settings remains accessible.

Privilege transport is a Shizuku UserService with validated commands, bounded execution, explicit errors, death handling and reconnect. No unauthenticated network service. Missing permission gives useful installed-app browsing and a setup guide, not invented statistics. State refresh occurs only while foregrounded or manually; no continuous poller or battery-consuming service.

Verification: unit tests for parsers/command validation/policy transitions; assemble APK and lint; install on the real Motorola via Tailscale where available; inspect rendered screens and exercise policy change/readback/undo on a disposable test package. Leave unrelated installed apps unchanged. Record any device-access blockers honestly.
