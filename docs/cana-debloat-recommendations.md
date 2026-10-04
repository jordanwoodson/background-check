# Cana debloat recommendations in Apps

**Apps → Your apps, System, All apps and Uninstalled** shows a **Cana** rating on every package row. Details show the rating's meaning and any package-specific warning. Expand **Cana list details** for the original upstream notes, list-declared dependencies, packages listed as needing this component, references and suggested replacements with their restrictions.

Internet-access advice belongs to **Network**, where firewall rows now show **Internet advice** and the existing sheets provide the explanation. Apps retains an installed-package shortcut to Network, but no longer displays firewall recommendations or reads INTERNET permission for its own inventory.

## The same source as Cana

The bundled `app/src/main/resources/cana-debloat-list.zip` is byte-for-byte identical to Cana's `android-debloat-list.zip` at Cana commit `98409037237f8c392efdda89f7955d7d35e736f1`, verified against Cana's current remote `master` on October 4, 2026. Both use [Android Debloat List revision 10c8249](https://github.com/MuntashirAkon/android-debloat-list/tree/10c82498154523d29ba0f268e4ab8316ab19c16b).

Archive SHA-256: `e815de9f46a65d619a7476d811ed2d95c19f8f721c204f585d28e01fd748bc01`.

All 4,801 records are preserved across the source's five categories. The reader uses Cana's exact mapping:

| Source value | Cana rating | Entries |
| --- | --- | ---: |
| `delete` | Recommended | 2,732 |
| `replace` | Advanced | 913 |
| `caution` | Expert | 888 |
| `unsafe` | Unsafe | 268 |

This is the current Cana source, not the older Cana package-audit verdicts or the Network workspace's separate package-safety catalog. No local verdict overrides are applied. The source's descriptions are labeled **Upstream package notes**; they reflect the source's assessments. Warnings, references, dependency declarations and replacement restrictions are retained, including Chrome's passkey warning and Vanadium's GrapheneOS-only restriction. Empty upstream notes remain empty.

Of the independent 676-package USB audit snapshot, 392 have a source rating and 284 are explicitly **Not rated by Cana**. Unlisted packages still get review guidance; they are not assigned an invented Cana rating based on their name, system flag or installation state.

## State and scope

- System and user packages use the same exact-ID lookup. Being preinstalled is not a removal rating.
- Disabled apps remain installed. Removed records retain Cana's source rating and are labeled already uninstalled; their details provide review context before restoring and no live app actions.
- The source is a bundled snapshot. It does not read Cana's private cache or custom-list settings and may differ from a subsequently refreshed Cana installation.
- Ratings are guidance, not proof that removing a package is safe on this device. List-declared dependencies are distinct from live Android associations.
- Viewing recommendations does not uninstall, disable, restore or change firewall rules. Existing controls retain their protections.

The original archive includes the upstream source and COPYING. The APK also ships `CANA-DEBLOAT-NOTICE.txt` and `ADL-LICENSE.txt` in assets, preserving the list's AGPL-3.0-or-later attribution and license. The archive is read in memory with an expanded-size limit; no archive paths are extracted. Loading occurs on the inventory's IO dispatcher before rows are rendered.

## Verification — October 4, 2026

- Required release gate passed with JDK 21 and Android SDK 36: `:app:testDebugUnitTest :app:lintRelease :app:assembleRelease`. All 130 unit tests passed, including source fidelity, archive validation, exact-ID fallback, all four ratings and the independent 676-package audit's coverage. Final lint reported zero errors and 287 warnings.
- On the API 35 emulator, 28 instrumentation tests passed across Apps recommendations (6), package descriptions (3), Network recommendations (5), app-detail sorting (9), Apps sorting (4), and sorting restoration (1). The recommendation tests exercise user, system and uninstalled filters, an unrated package, preserved Chrome warnings and replacement restrictions, the Network shortcut, Network row advice and its detail sheet.
- After moving the Network row's text format into a string resource, the five Network recommendation tests passed again against the rebuilt app.
- Inspected the Apps and Network screenshots below. The Apps detail screenshot shows Chrome's Advanced rating and its full source warning in a scrollable detail panel; Network retains the firewall recommendation above the access controls.
- The signed release APK contains the unchanged archive, notice and license. Its signing certificate SHA-256 remains `3d770ebfa7c2eacd7735adc9fadca193b62e59424659aed58afe2243c1879951`.
- A code review found no blocking issues. Physical-phone testing was unavailable because the wireless debugging connection refused the connection; the UI results above are from the emulator.

Evidence: [release gate](../artifacts/verification-cana-recommendations/release-gate.log), [UI test results](../artifacts/verification-cana-recommendations/ui-tests.log), [final Network checks](../artifacts/verification-cana-recommendations/network-final-tests.log), [Apps screenshot](../artifacts/verification-cana-recommendations/apps.png), [Network screenshot](../artifacts/verification-cana-recommendations/network.png).
