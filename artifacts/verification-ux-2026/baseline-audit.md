# Background Check UI/UX audit — 2026-10-04

Source: clean main d8b85b7; current Compose and Network Views code; existing screenshot captures (historical, not new runtime verification).

## Findings

- Main header shows the old terminal mark despite the new Cana shield launcher icon. Network contains a dormant old terminal drawable, but setupToolbar replaces it with a back arrow at runtime; the 32 dp back ImageView itself lacks the usual 48 dp touch target.
- Global monospace typography forces ordinary labels and explanatory text into wide, dense rows; technical readings benefit from mono, prose does not need it.
- Repeated outlined 6–8 dp panels, outlined secondary actions, bright filter fills and terminal eyebrow syntax compete with primary information.
- Overview's battery information is useful, but the process cards consume substantial height before active apps and reversible changes.
- Monitor places a large sampling card and profile explanation before CPU/RAM. Primary readings should be visible sooner while retaining pause, refresh, intervals and source timestamps.
- Apps has separate profile, package-type, process and sort control rows. Retain independent filtering/sorting and simplify their visual hierarchy.
- Five destinations in the normal bottom bar and three rows at large text need explicit narrow-width/large-font checks. Network launches a separate workspace; its return path should be obvious.
- Details and confirmations preserve consequences, protected-app rules, scrollable diagnostics, operations feedback and cancellation. However, descriptions, Cana notes and associated packages now precede background controls, adding a long scroll before routine actions. Bring the controls forward and keep explanatory details available on demand.
- Access claims no background monitoring while the bundled Network module has a package-monitoring service and firewall. Clarify that resource sampling runs only in the foreground; INTERNET is absent from the merged app manifest, so that existing claim is verified.
- Network uses uppercase section titles, a tightly packed toolbar, and compact input/toggle layouts; audit large-text clipping, touch targets and tonal consistency.

## Proposed direction pending design approval

Dracula-based Material 3 expressive visual language: Cana shield in both headers, tonal rounded panels, proportional labels and explanations, JetBrains Mono for readings/package identifiers, clear selected navigation and secondary filters. Improve Overview and Monitor hierarchy; align Network, detail sheets, access, empty/loading/error states. Verify normal, 1.5×, 2× text and landscape, reduced-motion behavior, relevant existing UI tests and the mandated unit/lint/release build. Publish only via the existing main workflow after checks.

## Environment evidence

JDK 21 and Android SDK 36 restored under /tmp using checksum-verified official archives. No emulator or physical phone initially connected. Local main and remote main both d8b85b7; latest release workflow 37222168497 succeeded. Signing configuration exists outside Git; default version/key remain untouched.

Baseline offline release gate: passed (145 tasks, 1m 8s). Unit task was up-to-date; fresh unit execution still required after app changes. No product code changed.

Fresh Android 16 baseline: normal-text capture walkthrough passed all 11 tests and produced 27 screenshots from current main with injected sample data. Main header brand mismatch, Apps first row near the navigation bar, and Monitor readings below several control/explanation rows are directly visible. Network toolbar drawable is replaced by a back arrow at runtime (corrected from the earlier XML-only finding).

Baseline capture verification passed all 11 tests per configuration: normal, 150%, 200% and landscape (44 executions, 108 screenshots). The walkthrough establishes displayed labels, reachable retained details and cancellation with injected sample data; it does not establish physical-device resource accuracy or full accessibility compliance. At 150% and 200%, initial Apps has no app row and initial Monitor has no CPU/RAM readings; controls and explanatory text consume the visible content. The navigation expands to three rows at larger text sizes. Source logs: captures-normal.log, captures-large-text.log, captures-largest-text.log, captures-landscape.log. Screenshots: baseline-captures/ux-captures/.

Network runtime review: normal Firewall, Packages and Settings captured by navigating from Background Check. Changing font scale from 1× to 2× re-created Network and repeated the firewall-start prompt after Later, even from Settings. No firewall was enabled: every prompt was dismissed with Later. Source root cause: onCreate calls onPermissionsComplete on recreation; that method re-reads the enabled start-prompt preference and presents it without tracking its dismissal for the current visit. At 2× the dialog title and Start Firewall label ellipsize. The shell cannot directly launch the unexported Network activity; captures use the app's own Network navigation, preserving that boundary.

Additional verified Network findings at 2× text: Firewall toolbar clips its section title; row text wraps inside a narrow column because the three network status icons keep their full width. Settings' about card forces the app name/version into a very narrow column alongside the icon and Donate action, breaking words across many lines. Normal and 2× Firewall/Packages/Settings captures are in baseline-captures/network-*-font*.png, with matching UI trees. These require responsive row/toolbar/about layouts in addition to typography changes.
