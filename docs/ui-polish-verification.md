# UI polish and release verification

This sweep keeps Background Check’s Dracula palette and JetBrains Mono, while making the everyday actions easier to reach. All existing app controls, resource values, sorting choices, recovery history and setup guidance remain available.

## Flow review

The screenshots were captured during this run on an Android 15 / API 35 emulator at 360 dp width. The capture harness renders the production Compose UI with explicitly supplied sample data; it does not establish hardware accuracy. Separate Shizuku integration tests exercise actual resource reads and fixture-only changes.

| Step | Finding and resulting behavior | Current screenshot |
| --- | --- | --- |
| 1. Overview | Compact battery summary and process counts. Pending access checks have their own status; setup appears only when access is actually needed. | [Overview](../artifacts/verification-ui-polish/after/ux-captures/01-overview-720x1600-font1p0.png) |
| 2. Apps | Persistent search label, trimmed search text, direct reset for no matches, and process filters retained through reconnection. Every sort remains available. | [Apps](../artifacts/verification-ui-polish/after/ux-captures/04-apps-720x1600-font1p0.png) |
| 3. Live Monitor | Core CPU/RAM readings precede optional details. Sampling indicators reserve their space. Mode controls remain reachable, with separate search and scroll state for Live and Battery. | [Live Monitor](../artifacts/verification-ui-polish/after/ux-captures/06-monitor-live-720x1600-font1p0.png) |
| 4. Battery | Sample timestamps remain visible. Secondary measurements and component breakdowns expand on demand. Refreshing battery estimates also updates the local battery reading while paused. | [Battery](../artifacts/verification-ui-polish/after/ux-captures/09-battery-flow-720x1600-font1p0.png) |
| 5. Connected Access | No setup sequence or authorization action appears by default. Connection help retains the complete guide and closes after successful connection. Denied permission retains manual authorization guidance. | [Connected Access](../artifacts/verification-ui-polish/after/ux-captures/12-access-connected-720x1600-font1p0.png) |
| 6. App controls | Background access, Doze and force stop precede diagnostics. Close and operation feedback remain visible while scrolling. Failed setting reads clear stale policy and offer retry. | [App controls](../artifacts/verification-ui-polish/after/ux-captures/14-detail-background-720x1600-font1p0.png) |
| 7. Diagnostics | The expandable section retains process/standby state, CPU, RSS, virtual memory, battery UID data, PSS, services, timestamps and independent sorting. | [Diagnostics](../artifacts/verification-ui-polish/after/ux-captures/17-detail-diagnostics-processes-720x1600-font1p0.png) |
| 8. Confirmation | Consequences remain explicit. Dialog text scrolls at large sizes; Cancel and action buttons remain accessible. Confirmation eligibility is rechecked. | [Policy confirmation](../artifacts/verification-ui-polish/after/ux-captures/19-confirm-restriction-720x1600-font1p0.png) |
| 9. Disconnected Access | The primary next step matches connection state; setup guidance remains available on demand. | [Disconnected Access](../artifacts/verification-ui-polish/after/ux-captures/22-access-disconnected-720x1600-font1p0.png) |
| 10. Disconnected Monitor | One recovery panel replaces unavailable metric stacks. Local battery information remains available in Battery use. | [Disconnected Monitor](../artifacts/verification-ui-polish/after/ux-captures/24-monitor-disconnected-720x1600-font1p0.png) |

Before screenshots are in [the baseline capture set](../artifacts/verification-ui-polish/before/ux-captures). The baseline authorized-access regression [fails as expected](../artifacts/verification-ui-polish/access-regression-before.log) because the old UI still renders Check connection after authorization.

## Motion and accessibility

Page changes use a short fade and vertical entrance; disclosures animate expansion and their arrow; stable list items animate placement. Sampling values do not animate repeatedly, and polling lifecycle follows the selected page independently of outgoing transitions. Compose honors Android’s animation duration scale.

Screen/section headings have accessibility semantics, metric labels and values are grouped, selected policy choices expose their state, loading indicators have descriptions, and connection status uses text alongside color. The sheet close button has a 48 dp target. Confirmation text and all secondary details remain scrollable. On short screens, confirmation dialogs use a wider layout so consequences and both actions remain comfortably visible. Above 1.3× text size, navigation uses two rows of fully labeled tabs, process cards expand to full width, sampling actions stack, and metric values sit below their labels.

Screenshots and semantic tests can establish layout, labels and reachability; they do not constitute a TalkBack usability study or full accessibility certification. The physical Motorola was unavailable over its previous wireless-debugging port for this run; no phone installation or Obtainium-update claim is made here.

## Validation

- Required local gate passed with JDK 21 and Android SDK 36: `:app:testDebugUnitTest :app:lintRelease :app:assembleRelease`. See [build output](../artifacts/verification-ui-polish/build-final.log).
- All 88 unit tests passed. Release lint has no errors and 11 existing warnings (`GradleDependency`, `ModifierParameter`, `UseKtx`); see [check counts](../artifacts/verification-ui-polish/local-checks.json).
- The signed local release APK retains the established certificate SHA-256: `3d770ebfa7c2eacd7735adc9fadca193b62e59424659aed58afe2243c1879951`. See [signature check](../artifacts/verification-ui-polish/signature.txt).

- The full emulator suite passed all 55 tests, including real Shizuku resource reads, connection recovery, polling lifecycle, sorting and restored positions, detail controls, and the confirmed restrict/undo path. The undo test now waits for the specific new history record and verified allowed baseline. See [device results](../artifacts/verification-ui-polish/device-tests-final.log).

- After the final overview adaptation, all 15 capture and overview/app sorting checks passed at normal text size. The 11-test capture walkthrough also passed at 1.5× and 2× text, plus landscape. Each configuration has 27 screenshots, including expanded diagnostics and confirmation dialogs: [normal](../artifacts/verification-ui-polish/captures-normal.log), [1.5×](../artifacts/verification-ui-polish/captures-large-text.log), [2×](../artifacts/verification-ui-polish/captures-largest-text.log), [landscape](../artifacts/verification-ui-polish/captures-landscape.log).

- With animator, transition and window animation scales set to zero, all 18 access, monitor sorting/state and detail restoration tests passed. See [reduced-motion results](../artifacts/verification-ui-polish/reduced-motion-tests.log). Emulator font, orientation and animation settings were restored afterward.
- The final window-aware confirmation adjustment also passed focused [portrait](../artifacts/verification-ui-polish/confirmation-portrait-final.log) and [landscape](../artifacts/verification-ui-polish/confirmation-landscape-final.log) capture tests; both dialogs now show the full consequences and actions together.
- Visual review found no blocking clipping in the refreshed normal, 1.5×, 2× and landscape captures. Scrolling exposes all retained controls and readings. The large-text fixes were prompted by inspecting screenshots, not by assuming passing interaction tests established visual quality.

The app changes are ready for the repository’s automatic signed release workflow. CI release evidence is recorded after publication.
