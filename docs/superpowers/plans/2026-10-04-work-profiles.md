# Work Profile Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans and superpowers:test-driven-development. Independent domains use superpowers:dispatching-parallel-agents after shared contracts are established.

**Goal:** Add full work-profile support to every existing Background Check feature.
**Architecture:** Explicit profile identity flows from inventory to UI, commands and persistence. Reuse Network's privileged discovery, preserving actual platform restrictions and verified identity at mutation time.
**Tech Stack:** Kotlin, Compose, Android user APIs, Shizuku, existing Network library.
**Spec:** `docs/superpowers/specs/2026-10-04-work-profiles-design.md`

## Global constraints

- Work on main; preserve signing and default version; use the existing release workflow.
- Android 11+; build/test with JDK 21 and SDK 36.
- Package plus user identifies app copies; full UID and profile serial protect mutations.
- Never substitute a different profile or claim unavailable backend enforcement.

## Shared interfaces

- `AndroidProfile(userId: Int, name: String, isWork: Boolean = false, serialNumber: Long? = null, available: Boolean = true, unavailableReason: String? = null)`.
- `AppEntry.profile: AndroidProfile` defaults from UID; `AppEntry.userId` derives from UID; `AppEntry.key: String` is `userId:packageName`.
- `PolicyState.userId: Int = 0`, `uid: Int? = null`, `userSerial: Long? = null`.
- `ChangeRecord.userId: Int = before.userId`, `userSerial: Long? = before.userSerial`, `profileName: String`.
- `UiState.profiles: List<AndroidProfile>`; `AppDiagnostics.userId: Int = 0`.
- Host callbacks for app settings and Network take `AppEntry` and `AppEntry?` respectively.
- PolicyEngine/ProcessManager retain their selected-user constructor; host creates them for each target user. History decode accepts an optional legacy user ID.

## Review focus

- Same package in two profiles must not share selection, remembered state, Undo or settings routing.
- Paused/locked/deleted/recreated profiles must fail safely without switching identities.
- Work-only apps and retained-uninstalled packages need genuine UIDs and metadata.
- Async reads after selection/profile changes must not overwrite the current detail.
- Backend restrictions and whole-device readings must remain visible and accurate.

## Tasks

- [x] 1. Establish profile models and inventory; test duplicate package identity, per-profile associations, unknown/paused state and stable sorting. Implement profile discovery and retained package reads with clear warnings.
- [x] 2. Add profile/UID identity to policy/history and mutation validation. Test two profiles, wrong-user policies, stale UID and legacy history migration; preserve rollback and shared Doze semantics.
- [x] 3. Add filters, labels, correct keys/icons and navigation throughout host UI; write UI regressions for identical packages and same-profile associations. Cover Overview, Apps, Monitor, details and history.
- [x] 4. Audit and implement missing Network profile handling: settings/shortcuts, retained packages, profile lifecycle, notifications, backup and backend coverage. Test actual behavior and explain unsupported backend scope.
- [x] 5. Wire profile-specific host engines, state refresh and monitored diagnostics; test UID/PID isolation and asynchronous selection safety.
- [x] 6. Provision a disposable managed-profile emulator and run end-to-end policy/Undo, stop, inventory, monitor, navigation and firewall verification. Inspect rendered UI.
- [ ] 7. Run `./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease`, obtain independent code review, fix findings, update docs and push main. Verify the signed automatic release.

## Execution notes

The user authorized implementation and delivery; routine design decisions are resolved here without redundant approval. Independent domains have disjoint file ownership. Shared interfaces above are the binding contract; additions are coordinated with the primary agent. Verification logs are retained outside Git until summarized in the verification document.

### Integration evidence

- Final required release gate passed with JDK 21/SDK 36: 176 unit tests, release lint with no errors, and signed/minified release build. Signing certificate matches the established fingerprint.
- Host UI: 39 tests passed on API 35. The new association regression fails under a deliberate package-only lookup mutation and passes with profile-aware lookup.
- Host runtime: genuine managed profile tests passed on both API 35 and 36, including independent restrictions/Undo, selected-UID diagnostics, force-stop preserving Personal, stopped-profile rejection and actual Work Settings navigation. Personal policy/resource regressions passed together with the Work tests on API 36.
- Network: two-profile CM traffic passed on both APIs; root iptables traffic separation and full service recovery/LAN/screen-off checks passed on API 36. Eight final API35 runtime checks cover database migration/rollback, stale action refusal, retained/work-only metadata, CM state preservation and Work navigation. The final profile-labeled shortcut also passed.
- Package lists: seven codec and three runtime/UI tests passed for Work-only/mixed-profile roundtrips and explicit legacy profile selection.
- Independent review findings were fixed: retained-state filtering, captured action UID/serial, removal of own-profile fallbacks, profile-bound text imports and partial inventory warnings. Final mutation revalidation preserves the selected identity across asynchronous reads.
- Full evidence, platform limits and screenshots: `docs/work-profile-verification.md`.
- Commit, push and automatic release verification are the remaining delivery step. Do not create tags or publish APKs manually.
