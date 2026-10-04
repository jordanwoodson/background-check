# 2026 UI and UX verification

The approved redesign keeps the Dracula palette and Cana-inspired shield/battery identity. Android sans-serif makes labels and prose easier to scan; bundled JetBrains Mono remains on measurements and identifiers. Rounded tonal surfaces, an adaptive navigation dock/rail, and a header shortcut connect the main app with Network.

Apps keeps search and inventory ahead of its expandable **Filters** panel, which contains sorting, profiles, package types and process states. Monitor brings CPU/RAM readings and their sample time forward; failed samples keep their status prominent. **Profiles and scope** always identifies the selected scope. Connected app details lead with background controls, followed by battery optimization, force stop and Network; **Package information** preserves purpose, Cana guidance, sources and related packages. Browsing without access and retained-package details keep that information expanded.

Network uses a two-row toolbar with a 48 dp back target, full-width app text above its network/status controls, growing search fields, and settings controls ahead of the about card. Backend and language selections grow with their text instead of clipping inside fixed-height fields. Dialog titles and actions wrap at large text sizes. Declining the startup prompt survives recreation; a genuine new visit still honors the startup-prompt preference. VPN permission context and action confirmation semantics stay intact. Theme attributes preserve the existing dynamic-color option. App shortcuts retain the cleared All apps filter during asynchronous loading and open the matching control sheet.

The direction follows Android's [Material 3 guidance](https://developer.android.com/develop/ui/compose/designsystems/material3), including expressive surfaces and typography. No dependency upgrade, enforcement change, version bump, signing-key replacement, or manually published release is part of this change.

## Evidence

Screenshots use the production UI on a disposable Android 16 / API 36 emulator, 360 dp wide in portrait. Compose captures inject explicit sample data and establish layout behavior. Separate Shizuku tests exercise actual resource reads, lifecycle and fixture-only policy changes. Network captures show the actual installed app inventory with the firewall off.

Before: [Apps at 150%](../artifacts/verification-ux-2026/before/04-apps-720x1600-font1p5.png), [Monitor at 150%](../artifacts/verification-ux-2026/before/06-monitor-live-720x1600-font1p5.png), [Network at 200%](../artifacts/verification-ux-2026/before/network-firewall-font2p0.png). The [baseline audit](../artifacts/verification-ux-2026/baseline-audit.md) records the observed problems.

After: [Overview](../artifacts/verification-ux-2026/after/01-overview-720x1600-font1p0.png), [Apps](../artifacts/verification-ux-2026/after/04-apps-720x1600-font1p0.png), [Monitor](../artifacts/verification-ux-2026/after/06-monitor-live-720x1600-font1p0.png), [app controls](../artifacts/verification-ux-2026/after/28-detail-opening-720x1600-font1p0.png), [Apps at 200%](../artifacts/verification-ux-2026/after/04-apps-720x1600-font2p0.png), [Monitor at 200%](../artifacts/verification-ux-2026/after/06-monitor-live-720x1600-font2p0.png), [landscape navigation](../artifacts/verification-ux-2026/after/04-apps-1600x720-font1p0.png).

Network: [Firewall at 200%](../artifacts/verification-ux-2026/network/firewall-largest.png), [Packages at 200%](../artifacts/verification-ux-2026/network/packages-largest.png), [Settings at 200%](../artifacts/verification-ux-2026/network/settings-largest.png), [startup dialog](../artifacts/verification-ux-2026/network/startup-largest.png), [landscape](../artifacts/verification-ux-2026/network/firewall-landscape-largest.png).

## Checks

Required pre-push command, with JDK 21.0.12.1 and Android SDK 36:

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

- **176 unit tests passed**, with no failures or errors.
- **Release lint passed with 0 errors**, 311 warnings and 3 hints; no suppression baseline was introduced.
- **Signed release assembly passed** and the certificate matches the established SHA-256 fingerprint. [Build log](../artifacts/verification-ux-2026/release-gate.log), [signature](../artifacts/verification-ux-2026/local-signature.txt), [machine-readable results and APK checksum](../artifacts/verification-ux-2026/checks.json).
- **Full device suite: 83 tests, 82 passed, 1 assumption skip, 0 failures.** This includes real Shizuku snapshots, reversible fixture background/Doze changes, UI confirmation and Undo, verified fixture force stop, sampling lifecycle, sorting restoration, Network shortcut identity and startup-dialog recreation. [Device log](../artifacts/verification-ux-2026/device-tests.log).
- **Capture runs at 100%, 150% and 200%: 14 tests passed each.** Landscape: 11 passed and 3 portrait-only assumptions skipped. The after directory contains 111 Compose screenshots. [Normal](../artifacts/verification-ux-2026/captures-normal.log), [150%](../artifacts/verification-ux-2026/captures-large.log), [200%](../artifacts/verification-ux-2026/captures-largest.log), [landscape](../artifacts/verification-ux-2026/captures-landscape.log).
- **Both Network text-layout regressions passed at 200%.** The title and Settings selection checks failed before their fixes. [Title before](../artifacts/verification-ux-2026/dialog-before.log), [title after](../artifacts/verification-ux-2026/dialog-after.log), [Settings before](../artifacts/verification-ux-2026/settings-before.log), [final 200% checks](../artifacts/verification-ux-2026/settings-after.log). The shortcut filter regression also failed before its fix and all ten Network tests then passed. [Shortcut before](../artifacts/verification-ux-2026/shortcut-before.log), [Network after](../artifacts/verification-ux-2026/shortcut-after.log).

The read-only review found no blocking code issues. It identified the selected-profile summary and remaining monospace identifiers, both corrected before final verification. Runtime capture review also caught version-qualified themes overriding the new typography and single-line title scrolling; those overrides are consolidated and a failing layout regression verifies the title fix. A second 200% text regression reproduced clipping in the backend selector before both Settings selection fields were allowed to grow. The shortcut regression now waits for and verifies the matching control sheet before dismissing it to inspect the inventory; dismissal waits for the main window to regain focus and the sheet to disappear. This exposed and corrected an asynchronous All-to-User filter change. The first full runtime run also caught a failed-sample timestamp pushed below the viewport; Monitor now keeps failed-sampling status ahead of empty metrics.

The existing work-profile UI tests verify injected personal/work identities, unavailable profiles, cross-profile Doze confirmation and independent package selection. The current emulator has no managed user 10, so the real managed-user Settings shortcut is skipped; earlier [managed-profile verification](work-profile-verification.md) remains historical evidence. Physical-phone installation and Obtainium delivery are outside this emulator verification.
