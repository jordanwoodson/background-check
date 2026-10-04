# Internet firewall recommendations

Every package in **Apps → Your apps, System, All apps, or Uninstalled** has a firewall recommendation beneath its description. Open the package for a reason, the effects of blocking, and **Recommendation details** (permission metadata, Android identity and source). Installed packages have a **Network and internet access** shortcut to the matching firewall entry. The Network workspace also shows the guidance in both firewall control sheets and package action sheets; tap **Show effects and details** to expand the explanation.

Recommendations are advice, not the current firewall state. They never apply rules automatically. Use the existing Network controls to choose a rule and check the active backend's limitations. In particular, NetworkPolicyManager may only restrict metered background data; the advice does not turn that into full internet blocking.

| Recommendation | Meaning |
| --- | --- |
| Keep internet allowed | Connectivity, shared services, remote access, core/shared UIDs or a system component whose network role needs investigation. |
| Allow if you use online features | Blocking affects features such as online messages, updates, sync, websites or streaming. The detail explains the tradeoff. |
| Consider blocking internet | An identified local tool or optional reporting component. Check the stated feature losses first. |
| No direct internet permission | A successful permission read found no INTERNET declaration. It can still delegate work to another app or service. |
| Review before blocking | No exact package guidance is available. Unknown apps are not inferred to be safe from their names. |
| No rule needed while uninstalled | The package is absent for this user. Reinstall advice is shown separately, without a live firewall shortcut. |

The curated guide matches exact package IDs and describes known roles. All remaining packages receive advice based on their current metadata: verified absence of INTERNET, a system-component precaution, or explicit review for an unknown user app. These are editorial recommendations, not measurements of traffic, malware verdicts or proof that a package is trustworthy. The [package-purpose guide](package-descriptions.md) supplies additional publisher context.

Installed core and shared UIDs take priority over optional-block advice. A firewall can affect multiple packages sharing the same Android identity. Disabled apps remain installed and retain guidance for when they are enabled. A failed permission read stays unknown. Removed packages ignore stale permission and UID metadata; after reinstalling, refresh before deciding on a rule. Other Android users/profiles may still have the same package installed.

## Sources and maintenance

Guidance is bundled locally and does not upload the app inventory. Exact-ID role groups live in `network/.../domain/model/InternetRecommendationGuide.kt`; metadata precedence lives in `InternetRecommendation.kt`. Sources explain the underlying capabilities; the allow/block decision is Background Check's recommendation, not an instruction issued by the publisher.

- [Android INTERNET permission](https://developer.android.com/reference/android/Manifest.permission#INTERNET) distinguishes direct sockets from network-state permissions.
- [Android shared user IDs](https://developer.android.com/guide/topics/manifest/manifest-element#uid) explains why package identities can be shared.
- [Android Network Stack](https://source.android.com/docs/core/ota/modular-system/networking) describes connectivity services and captive-portal support.
- [Firebase network requirements](https://firebase.google.com/docs/cloud-messaging/network-configuration) describes connectivity needed for push delivery.
- [Package descriptions and publisher sources](package-descriptions.md) document the inventory's app roles.

When adding an exact-ID group, document the feature loss and use a relevant platform or publisher source. Do not convert uninstall-safety ratings, name prefixes or package queries into firewall recommendations. Add a behavioral test when changing precedence or metadata handling.

## Verification — October 4, 2026

- JDK 21.0.12.1 and Android SDK 36: `./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease` passed. All 124 unit tests passed; lint reported zero errors and 287 warnings.
- API 35 emulator: 26 instrumented tests passed across `InternetRecommendationsUiTest`, `NetworkRecommendationsUiTest`, `PackageDescriptionsUiTest`, `AppDetailSortingUiTest`, `OverviewAppsSortingUiTest` and `SortRestorationUiTest`. They cover recommendation visibility/navigation without Shizuku, source disclosure, removed packages, stale UIDs, installed packages without a version name, unknown permission reads, real Android repository mappings, both firewall sheet layouts and list/detail regressions.
- The initial eight recommendation tests failed against the placeholder resolver, then passed with the engine. The two metadata regressions failed before adding explicit installation and permission-read flags, then passed afterward.
- The signed release APK verifies against the established certificate SHA-256 `3d770ebfa7c2eacd7735adc9fadca193b62e59424659aed58afe2243c1879951`. Version defaults and signing configuration are unchanged.
- [Rendered Network recommendation](../artifacts/verification-internet-recommendations/network.png) shows the collapsed advice with firewall controls still visible. The recommendation controls were exercised without changing firewall rules.

The Motorola phone's advertised wireless-debugging endpoint refused connections over both LAN and Tailscale, so this change has emulator verification rather than a fresh physical-phone run. This feature does not change enforcement backends; their prior verification remains documented in [network controls verification](network-controls-verification.md).
