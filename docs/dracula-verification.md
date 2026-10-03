# Dracula terminal verification

The Dracula rebrand follows the completed [private repository and Obtainium setup](release-setup-verification.md). The app name remains **Background Check**.

## Scope

The shared Compose theme defines Dracula colors, all 15 monospace typography roles, and compact corner shapes. The screens, list cards, sort menus, detail sheets, confirmations and feedback use that theme. Overview and Access use dark panels instead of the earlier lime fills. Live metrics, status, warnings and chart lines have separate palette colors. The launcher has terminal-window artwork, adaptive and monochrome icons, and a matching Android splash screen. Package identity and signing remain unchanged.

## Local checks

- 88 unit tests, zero failures/errors/skips.
- Signed release, debug and instrumentation APK builds pass.
- Release lint: zero errors, 11 existing warnings. The new icon resources introduce no remaining lint warnings.
- The signed release verifies against the established Background Check certificate.
- The final complete real-device run passed **24/24 tests in 121.83 seconds** after the control-border and launcher-resource refinements. It covers rendered sorting, navigation, resource reads, Shizuku lifecycle, fixture-only policy changes, recovery and force stop. See `artifacts/verification-dracula/device-tests-final.log`. The initial theme run also passed 24/24 in 122.563 seconds.

## Visual checks

Inspected the actual Motorola Edge screens at normal text size: [Overview](../artifacts/screenshots/dracula-overview.png), [Apps](../artifacts/screenshots/dracula-apps.png), [live Monitor](../artifacts/screenshots/dracula-monitor.png), [battery estimates](../artifacts/screenshots/dracula-battery.png), [Access](../artifacts/screenshots/dracula-access.png), [app details](../artifacts/screenshots/dracula-app-detail.png), [sort dropdown](../artifacts/screenshots/dracula-sort-menu.png), [policy confirmation](../artifacts/screenshots/dracula-confirmation.png), and the [installed adaptive icon](../artifacts/screenshots/dracula-icon.png). The manual policy confirmation was cancelled.

At font scale 1.5, [Overview](../artifacts/screenshots/dracula-large-text-overview.png), [Apps](../artifacts/screenshots/dracula-large-text-apps.png) and [Monitor](../artifacts/screenshots/dracula-large-text-monitor.png) retain readable labels and controls. Sampling intervals wrap onto another line. The Apps filter row remains horizontally scrollable; screen contents remain vertically scrollable. The phone's original font scale of 1.0 was restored after inspection.

During resource reorganization, Android's incremental merger omitted the moved mipmap file from its output. Regenerating debug and release resource outputs fixed the local linker error; the final build and lint then passed.

Release publication and Obtainium update evidence will be appended after CI completes.
