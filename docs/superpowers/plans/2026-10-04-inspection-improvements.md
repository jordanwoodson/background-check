# Inspection Improvements Implementation Plan

> **For agentic workers:** Use test-driven development and parallel isolated file ownership for independent subsystems; root integrates and verifies the whole application.

**Goal:** Implement all six accepted improvements and deploy a verified signed release.

**Architecture:** Retain the app/network modules and existing privilege, identity and recovery safeguards. Add focused persistence and presentation components; read Network's live state rather than inferring it from preferences.

**Tech Stack:** Kotlin, Compose, Android Views, existing Room/preferences, JDK 21, SDK 36.

**Spec:** `docs/superpowers/specs/2026-10-04-inspection-improvements.md`

## Global Constraints

- Work on main; never replace signing keys, bump the default version, or manually publish/tag a release.
- App data remains local, with explicit document export only.
- Mutating device verification uses `dev.bgcheck.fixture` only.
- Run `./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease` before pushing.

## Review Focus

- Same package in personal and work profiles must remain separate through pins, comparisons, history and Undo.
- Battery resets, missing counters and newly appearing UIDs must never produce fabricated interval measurements.
- Concurrent rule changes and missing privileges must refuse unsafe Undo without claiming success.
- Cold launches, invalid persisted enum values and stale selections must recover with usable defaults.
- Large fonts and compact lists must preserve accessible controls and complete detail access.

### Task 1: Battery comparisons
- [x] Add failing comparison and persistence tests, then implement focused comparison models/store and Compose panel.
- [x] Integrate explicit fresh sampling in Monitor; verify reset handling, missing values, profile attribution and persistence.

### Task 2: Network history and guarded Undo
- [x] Add failing history/identity/conflict tests, then implement persisted network change records and mutation hooks.
- [x] Provide observable records and guarded Undo service for root's combined Overview list.

### Task 3: Compact Network lists
- [x] Add persisted compact preference and controls to firewall/package lists; adapt row binding while retaining complete sheets.
- [x] Verify normal/large fonts, recycling and preference restoration.

### Task 4: Overview, preferences and pins
- [x] Test live firewall state/profile presentation and pinned identity matching.
- [x] Persist independent app/monitor/detail sort/filter choices and sampling interval; expose pins in detail and Overview.
- [x] Integrate searchable unified change list, before/after rendering and JSON document export; retain existing policy Undo safeguards.

### Task 5: Integration and verification
- [x] Run unit/lint/release gate and available focused instrumented/regression tests; inspect screenshots at normal and large font.
- [ ] Verify privileged package Undo and fresh battery sampling after explicit Shizuku approval.
- [x] Review full change, fix material findings and document exact evidence.
- [x] Check Motorola reachability and run available physical-device checks; record unavailable checks explicitly.

### Task 6: Deployment
- [x] Commit with readable release-note subjects and push main.
- [x] Inspect automatic release success, downloaded artifact certificate/checksum and installed release behavior.
- [ ] Verify Obtainium delivery on reachable phone; mark the overall goal complete only with requirement-by-requirement evidence.

## Execution record

- Initial tree clean at `6a5d0c0` on main. User explicitly requested implementing all suggestions and deployment, so no additional design/deployment approval is needed.
- Emulator `emulator-5560` reachable through host ADB. Motorola not listed initially; historical wireless endpoint `100.69.155.13:38649` may have changed.
- Full initial implementation unit run passed 221 tests (32 suites), with debug app/test APKs built. Subsequent review fixes require a fresh complete gate.
- Review fixes: genuine per-profile install metadata (no synthetic personal timestamps), cancellation-safe comparison save/publication, actual active-backend flow, monotonic comparison durations, raw package enabled-state restoration, and interrupted Network mutation checkpoints.
- Device isolation: emulator5560 is actively used by Cana instrumentation. Dedicated5562 first-boot attempts run in software because KVM is unavailable; do not disturb Cana's running tests.
- Motorola is online through Tailscale but historical ADB port refuses connection. Asked user asynchronously for current Wireless debugging IP/port; no response yet.

- Final source gate passed: 244 unit tests in 33 suites, zero release lint errors (319 warnings), signed release and debug/test APK builds. Local certificate matches the established fingerprint. Static review has no outstanding must-fix findings. Device evidence remains pending; do not count emulator startup failures as app-test results.
- Subsequent isolated API 35 verification passed all seven new UI cases at 100% and 200% font sizes, plus saved-rule Undo/conflict and Activity/ViewModel persistence runtime checks. Actual row checks required the existing safety metadata to finish loading first. Exact scope and qualifications are in `docs/inspection-improvements-verification.md`.
- Pushed app commit `e23ab6f7c66454f5ba748ab09e67914060bc1380` to main. Workflow `37261977933` automatically published `1.1.1-bgcheck.13` / code 16. Independently downloaded checksum, signature and version checks passed.
- Published APK installed in place on the isolated API 35 emulator, preserving installation time and all six history records. A force-stop/cold launch restored the Apps tab and descending sort. Published UI screenshots are included in the verification record.
- Automatic approval review rejected starting Shizuku on the disposable emulator as insufficiently authorized. Explicit approval was requested; no retry was made. Privileged live checks and phone/Obtainium checks remain pending approval and a current phone ADB endpoint, respectively.
