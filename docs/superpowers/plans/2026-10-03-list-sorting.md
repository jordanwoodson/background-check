# List Sorting Implementation Plan

**Goal:** Add multiple relevant sort choices and both directions to every dynamic data list in Background Check.

**Design:** Reuse a compact Sort by menu plus an explicit ascending/descending button. Each list remembers its own selection through recomposition and page navigation. New keys choose a sensible initial direction. Sort the complete filtered collection before limiting an overview preview. Unavailable metrics remain last in both directions, with stable identity tie-breakers. Preserve search, filters, actions and source semantics.

| List | Keys |
| --- | --- |
| Apps and Overview active-app preview | Name, package, UID, process state, standby group |
| Change history | Date, app, package, action, status |
| Monitor and app-detail processes | CPU, resident memory, virtual memory, name, PID, UID, state |
| Battery UIDs | Estimated battery use, CPU time, wakelocks, received/sent traffic, app name, UID |
| Device power components | Estimated battery use, displayed name |
| Related apps in process and battery details | Name, package, process state |
| Running services | Component name, process name, foreground status |

Charts remain in time order; fixed metric rows, explanatory text and warning messages are not sortable data lists.

- [x] Add pure shared comparators and meaningful tests: every key/direction, unavailable vs zero, numeric IDs, case-insensitive labels, deterministic ties.
- [x] Add a shared accessible, wrapping sort control.
- [x] Integrate Monitor, battery/components and related apps; verify rendered order and independent choices.
- [x] Integrate Apps, Overview preview/history and preserve page state; verify sorting before preview limits and search/filter retention.
- [x] Integrate app-detail processes/services; verify rendered order and existing controls.
- [x] Review full coverage, run unit/device tests, build/lint, inspect normal and large text; install and deliver signed version 1.1.1 with verification evidence.

Root owns shared sorting, controls and final verification. Independent agents own MonitorScreen, Screens, and AppDetail plus separate UI test files. Existing uncommitted 1.1.0 work is preserved. Gradle and device runs are serialized by root.

Completed: 88 unit tests and all 24 device tests pass. Signed 1.1.1 installed with matching artifact hash and copied to Downloads. Sort menus, numeric UID rows, virtual-memory rows and 1.5× text were visually inspected; font scale and temporary stay-awake setting restored. Verification: docs/list-sorting-verification.md.
