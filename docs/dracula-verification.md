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

The disposable fixture and instrumentation packages were uninstalled after testing. Two fixture-only history records were removed; other records were preserved. The previously published release was restored over the debug preview before verifying the new release as a normal upgrade through Obtainium. The original font scale and stay-awake setting were restored, and temporary device UI dumps were removed; see [cleanup evidence](../artifacts/verification-dracula/cleanup.json).

## Published release and Obtainium update

Verified on October 3, 2026:

- [Actions run 37161206453](https://github.com/jordanwoodson/background-check/actions/runs/37161206453) completed successfully for theme commit `aa113c5145962d9a7032f0b5b54ed3eebe642482`, including unit tests, release lint, build, signing/version checks and publication.
- [Release 1.1.1-bgcheck.2](https://github.com/jordanwoodson/background-check/releases/tag/1.1.1-bgcheck.2) contains the signed APK and `SHA256SUMS.txt`; it is neither a draft nor a prerelease. The version code is **5** and the package remains `dev.bgcheck.app`.
- The downloaded APK passed its checksum and signature checks with the established signing certificate. Its SHA-256 is `9b63dbf22eec922410ab7478f6ae3a3c354cc30c694bf8588d84fa3b2b9cf380`.
- Obtainium fetched the new private release and its **Update** action installed it. Android reports `dev.imranr.obtainium` as installer, version `1.1.1-bgcheck.2`, code 5, and a non-debuggable package. The installed APK hash exactly matches the published release; see [installed release evidence](../artifacts/verification-dracula/installed-release.json).
- The original first-install time and credential-encrypted data inode were unchanged, confirming an in-place update that retained the existing data directory.
- A subsequent Obtainium refresh recorded **1.1.1-bgcheck.2** as both installed and latest. Tracking remains enabled with `trackOnly=false`, `versionDetection=true`, and `exemptFromBackgroundUpdates=false`; see [sanitized tracking evidence](../artifacts/verification-dracula/obtainium.json).

The private repository and automatic signed-release workflow now deliver the Dracula theme through the same update path as the initial setup.
