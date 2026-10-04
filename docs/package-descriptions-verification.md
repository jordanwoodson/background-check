# Package-description verification — 2026-10-03

Added local package-purpose descriptions to app rows and details, purpose search, and a read-only Uninstalled view for retained Android package records. Sources and limitations are in [package descriptions](package-descriptions.md).

- Required pre-push gate passed with JDK 21 and Android SDK 36: `./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease`.
- 95 unit tests passed, with zero failures, errors or skips. New regression coverage includes description precedence, metadata normalization, category/unknown fallback, exact package matching, purpose search, installed/disabled/uninstalled filtering, and refusing policy/force-stop commands for uninstalled packages. The initial regression run failed in the expected missing behavior before implementation.
- Release lint: zero errors and 11 existing warnings. The signed APK's SHA-256 certificate fingerprint remains `3d770ebfa7c2eacd7735adc9fadca193b62e59424659aed58afe2243c1879951`.
- Six emulator instrumentation tests passed: `PackageDescriptionsUiTest` and `OverviewAppsSortingUiTest`. These cover descriptions without Shizuku, searching descriptions, ignoring an active-process filter in Uninstalled, absence of controls in removed-package details, sorting and filter retention.
- Manually viewed the production UI on an Android 15 emulator at 720 × 1600. The disposable `dev.bgcheck.fixture` was installed, launched and removed with data retained. It appeared in Uninstalled with a purpose-unavailable fallback after its resources were removed. Its details remained readable without app controls. The retained fixture was then fully uninstalled.
- Code review found one description-accuracy issue: Android Provision was separated from Google's interactive Setup Wizard. That correction is included in the final successful build gate.

The emulator used sample data for the Compose regression tests and actual PackageManager records for the retained-fixture check. Physical-phone installation and exhaustive description coverage for its packages were not verified. Unknown purposes remain explicit rather than inferred from package names.
