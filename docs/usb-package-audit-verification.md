# USB package audit — October 3, 2026

The Motorola Edge 2025 (`oulu`, Android 16 / SDK 36, build `W1VDS36H.50-38-3-12`) was inspected over USB ADB. One Android user is present. This extends the earlier package-description implementation with complete coverage of this phone's recorded apps and package associations.

## Verified coverage

- 675 app records: 360 installed system apps, 173 installed user apps and 142 retained-uninstalled records.
- One hidden Trichrome static library and 42 APEX identities, giving 718 captured identities with a purpose in the catalog and zero missing purposes.
- 774 exact package entries in the bundled guide, including additional same-phone identities from the earlier Cana research.
- Physical-phone instrumentation compared the app inventory against an independent shell package list. All 675 app IDs matched; every app resolved to a sourced guide purpose. The resulting inventory contained 5,903 association records before the final Termux companion additions.
- Current USB package queries matched every bundled query set for recorded apps. Overlay targets were reconciled with the current Overlay Manager. Removed records had no live shared-UID links.
- Seven newly added third-party APKs were pulled transiently and identified from their manifests and application labels with Android build-tools. `aapt2` handled the patched YouTube resources that legacy `aapt` could not fully read. Publisher documentation established their purposes; the Background Check entry uses this repository's source.

The hidden code library and APEX identities are not ordinary application records exposed by the app's PackageManager query. Their purposes exist in the catalog, and the Trichrome role appears in associated-package cards. Android retains only some removed packages; this is not a complete historical uninstall log.

## Validation

- Required pre-push gate passed using JDK 21 and Android SDK 36: `./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease`.
- 103 unit tests passed, zero failures, errors or skips. Coverage includes every APK ID from the independent 676-ID USB snapshot; exact description lookup and source presence; retained-label fallback; overlay/query/library reverse links; current shared-UID groups; and the documented Termux:Tasker hosts after removal. Both new behavior regressions were first observed failing before implementation/correction.
- Release lint reported zero errors and 11 existing warnings.
- Thirteen phone instrumentation tests passed: the full-inventory audit and 12 tests in `PackageDescriptionsUiTest` / `AppDetailSortingUiTest`. They verify descriptions and search, retained-package details without controls, companion navigation without Shizuku, source expansion and existing detail sorting/control behavior.
- The signed release APK contains the exact 774-entry source JSON. Guide SHA-256: `c4e0bdd216a05c876158fdd152adedfed92ed86b88ca0d2b6c275a51d217f952`.
- APK signing certificate remains `3d770ebfa7c2eacd7735adc9fadca193b62e59424659aed58afe2243c1879951`.
- Independent code/data review found a missing Termux:Tasker companion link and an outdated Trichrome caveat. Both were corrected; no Critical or Important findings remain.
- The real phone UI was inspected for a retained Moto AI Core record, its purpose and related installed/uninstalled packages. Raw dumps, APK inspection output, app inventory exports and screenshots remain outside Git under `/tmp`.

The collection script and source/association limitations are documented in [package descriptions](package-descriptions.md). No user or system app was removed, disabled or reconfigured during this audit. The temporary instrumentation package was removed after testing; existing Background Check data and the established signing key were preserved.

## Published release

[Release 1.1.1-bgcheck.6](https://github.com/jordanwoodson/background-check/releases/tag/1.1.1-bgcheck.6), Android version code 9, was published automatically from commit `03da6e05f8c01ec9895ff0a6de3807aed3d97893`. The [release workflow](https://github.com/jordanwoodson/background-check/actions/runs/37171951186) completed successfully.

The published APK was downloaded, checked against the published checksum and established signing certificate, and installed over USB. The APK installed on the phone is byte-identical to the published artifact and is not debuggable. APK SHA-256: `2cb65f6fb6c65f45e323c30936d8e60c99af7e3938699fc472a9350ac282f55f`.

A fresh collection after installation still found 675 app records and 718 total identities, with zero missing purposes. The published UI showed all 675 apps and 142 retained-uninstalled records. The Termux:Tasker detail screen displayed its shared-UID group and the two documented companion links to Tasker and Termux.

Sanitized release and coverage metadata are saved in [installed release](../artifacts/verification-usb-packages/installed-release.json) and [published coverage](../artifacts/verification-usb-packages/published-coverage.json). Raw package dumps and phone screenshots remain outside Git.
