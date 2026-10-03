# Background Check 1.1.1 — list sorting verification

## Scope

Every dynamic data list now has a Sort by menu and ascending/descending control: Apps, Overview active-app preview, change history, Monitor processes, battery UIDs, device power components, related apps in both detail sheets, app-detail processes, and running services. Fixed metric rows and chronological charts are unchanged.

Selection is separate for each list. Page state is retained across navigation, and app-detail choices are retained per package when details are reopened. Changing the sort key selects its natural initial direction. Comparators keep unavailable values last in both directions, compare IDs numerically, compare names without case sensitivity, and use stable identity tie-breakers. Overview previews sort the full collection before truncation. App rows display the selected package/UID/standby value; process and battery rows display the selected resource metric.

## Current evidence

- Complete unit suite: **88 tests, 0 failures**. Twelve sorting tests exercise all 29 comparator keys in both directions, numeric IDs, large byte counts, case-insensitive/shared labels, unavailable versus zero, ties and empty lists. The initial sort-stub run failed 11 assertions before implementation; all 12 sorting tests passed afterward.
- Debug, instrumentation and signed release APKs build successfully. Release lint reports **0 errors, 11 warnings**.
- Independent source reviews checked shared comparators, controls and all screen integrations. Review and a failing regression test identified sort state being lost on app-detail dismissal: saving inside the modal's separate composition did not preserve it. Sort choices now register above the modal under a per-package state holder. Reopening the same app restores its choices, and another app keeps separate choices.
- **24 real-device tests pass** in one full run on Motorola Edge 2025, Android 16/API 36 (99.804 seconds). This includes 12 new sorting tests plus the existing 12 resource, lifecycle, transport and action tests. Coverage includes actual rendered ordering in both directions, each app-detail process/service key, component names, UID battery metrics, related apps in both sheets, numeric IDs, search/filter retention, independent page choices, full sorting before preview limits, Undo targeting and per-app reopening.

The initial locked-screen run is retained for diagnosis: the secure keyguard was showing, and Android immediately paused each Activity with reason `sleep`, so it could not verify the UI. After the phone was unlocked, a valid run exposed the modal-state issue and a test that needed to scroll the second component row into view before comparing positions. Both were corrected before the final passing full run. The phone was kept awake during testing and its prior `stay_on_while_plugged_in=0` setting was restored.

Logs and unit JUnit XML are in `artifacts/verification-1.1.1/`. The invalid locked-screen run is retained as `device-tests-locked.log`.

## Visual inspection

Inspected the installed release's [Apps sort menu](../artifacts/screenshots/sort-apps-menu.png), [descending numeric UID order with values in rows](../artifacts/screenshots/sort-apps-uid.png), [process sort menu](../artifacts/screenshots/sort-process-menu.png), and [virtual-memory order and readings](../artifacts/screenshots/sort-process-virtual.png). At [font scale 1.5](../artifacts/screenshots/sort-large-text.png), the sort key and direction wrap onto separate lines and remain readable and operable. The original font scale of 1.0 was restored. UI hierarchy captures and the visual-check transcript accompany the screenshots.

## Delivered release

- `artifacts/background-check-1.1.1.apk`: version 1.1.1 / code 3, 2,682,752 bytes.
- SHA-256: `9d5bc032b52e4a908ed85b3c239c85eacfc6beaf6777163f0b46f0f1c132c86e`.
- Signed with the existing Background Check certificate; APK Signature Scheme v2 verified.
- Installed signed release is not debuggable; its installed APK hash matches the artifact above.
- Phone copy: `Download/Background-Check-1.1.1.apk`.
- Fixture and instrumentation packages were uninstalled. Only the two fixture history records were removed; other history was preserved.

The [1.1.0 verification record](resource-monitor-verification.md) covers the underlying monitoring features and their measurement limits.
