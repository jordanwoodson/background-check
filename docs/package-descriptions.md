# Package descriptions

The Apps list shows a short purpose beneath each app name, including system apps and packages retained by Android after removal for the current user. Open a package for its full description and description source. Search matches names, package IDs and purpose text. The same descriptions appear in Overview and related-app lists through the shared app row.

Descriptions are resolved locally, in this order:

1. An exact package ID in the bundled `package-guide.json`, reconciled with the Motorola Edge 2025 USB inventory on October 3, 2026. The catalog contains 774 entries, including every recorded app, the hidden Trichrome code library and the phone's APEX modules. Descriptions include per-package publisher/platform sources and purpose-specific uncertainties.
2. An exact package ID in `PackageDescriptions.kt`, covering additional common Android components and user apps.
3. The app's own manifest description, if present and readable.
4. Android's app category, explicitly identified as a broad category rather than a confirmed purpose.
5. An explicit purpose-unavailable message. Unknown packages are not assigned a purpose based on their name or vendor prefix.

The USB-audited phone inventory is fully covered; the guide is not an exhaustive database of every Android app. It describes general functions, not the work a package is doing now, its trustworthiness, or whether it is safe to disable. Available features can vary by device and app version. Descriptions do not change existing protections or authorize controls. Android-provided readable app names take precedence; retained records with unreadable labels use the catalog's package-specific name.

## Associated packages

Package details group associations by the evidence behind them. Tap a recorded package to open its own details, including uninstalled records. Packages absent from the current inventory are explicitly marked; no installation state is invented for them.

- **Shared Android identity:** computed on each inventory load from installed packages' current UIDs. Removed records never enter a live UID group.
- **Resource target / customizations:** exact overlay targets, refreshed from the USB device's Overlay Manager. Overlay purpose text explains the configuration and target instead of calling an overlay a standalone app.
- **Required shared library / clients:** `uses-static-library` declarations from the APK audit. A shared library is code, not an ordinary launcher app.
- **Declared package queries / queried by:** package-visibility declarations, refreshed from current Package Manager metadata for recorded apps. They establish discoverability, not mandatory dependencies or observed communication.
- **Related features:** documented companion relationships, including Morphe/MicroG RE, Termux add-ons, Island provisioning and Shizuku-powered management tools.

The catalog stores declared associations; reverse associations are derived only among current inventory records. UID relationships are live metadata. Other declarations describe the inspected APKs and can change in future versions. Empty associations do not prove independence. Broad framework permissions and library names are not converted into guessed package dependencies.

Normal PackageManager app queries omit the hidden Trichrome static library and APEX module identities. The guide covers them, and a library's role appears in associated-package cards. The Apps filters cover all APK app records returned for the current Android user; they are not an APEX module manager.

## Installed and retained packages

The inventory combines the normal installed-app query with `MATCH_UNINSTALLED_PACKAGES`, preferring metadata from the normal query. `ApplicationInfo.FLAG_INSTALLED` determines installation for the current Android user. Disabled apps remain installed. Your apps and System show installed packages; Uninstalled shows retained records; All apps combines both.

Android does not retain every removed package. Retained records can include kept data, system-image packages removed for this user, or packages installed for another user. This is not a complete uninstall history. Removed entries stay out of live process, UID and battery associations. Their details show purpose and retained-record context, without policy, force-stop or Android Settings controls. Policy and force-stop operations also reject uninstalled entries before using the shell.

## Sources and maintenance

The guide uses concise original summaries. Match exact IDs when adding entries; verify the product or component against its publisher's documentation or source. Do not infer a purpose from a familiar prefix. App-provided descriptions remain attributed to the app, and package IDs are not a signature or identity guarantee.

- [Android ApplicationInfo](https://developer.android.com/reference/android/content/pm/ApplicationInfo): descriptions, category values and installed/system flags.
- [Android PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager#MATCH_UNINSTALLED_PACKAGES): retained-package visibility and metadata limitations.
- [Android platform packages](https://android.googlesource.com/platform/packages/), [Mainline modules](https://source.android.com/docs/core/ota/modular-system), [PermissionController](https://source.android.com/docs/core/ota/modular-system/permissioncontroller), [DocumentsUI](https://source.android.com/docs/core/ota/modular-system/documentsui), and [Network Stack](https://source.android.com/docs/core/ota/modular-system/networking): Android component responsibilities.
- [Google Play services overview](https://developers.google.com/android/guides/overview) and [Google's Play services help](https://support.google.com/googleplay/answer/9037938): shared Google services.
- Publisher-maintained Play listings, addressed by exact ID as `https://play.google.com/store/apps/details?id=<package>`: Google, Motorola and user apps. Examples: [Moto](https://play.google.com/store/apps/details?id=com.motorola.moto), [Moto Actions](https://play.google.com/store/apps/details?id=com.motorola.actions), [Moto Secure](https://play.google.com/store/apps/details?id=com.motorola.securityhub).
- [Shizuku](https://shizuku.rikka.app/), [Termux](https://termux.dev/), [Tailscale for Android](https://tailscale.com/kb/1079/install-android), [Obtainium](https://github.com/ImranR98/Obtainium), and this repository's existing app inventory/protection definitions: local device-management apps.

Descriptions are bundled in the APK. Browsing/searching packages does not send the package inventory to an online service.

## Repeating the USB coverage audit

Connect the authorized phone, obtain its explicit serial from `adb devices -l`, then run:

```sh
python3 scripts/capture-package-audit.py --serial <serial> --output /tmp/bgcheck-phone-audit-refresh
```

The script captures installed, system, retained, hidden-library, APEX and overlay records without modifying the phone. It fails if any captured identity lacks a catalog entry. Keep its raw dumps outside Git. The independent package-ID snapshot in `app/src/test/resources/usb-package-ids.txt` protects all 676 audited APK identities against loss of description coverage.

The catalog reuses original purpose summaries, labels, publisher citations and manifest facts from the same phone's Cana audit, then reconciles them with fresh USB metadata. It does not import Cana's removal ratings, tracker verdicts or inferred safety claims. The input review ledger SHA-256 was `64a40020c9728777768cb963da2cfcd9486944280a214a23d323d1e4b59655fc`. The eight new package IDs were identified from current APK metadata and publisher sources: Morphe YouTube, Morphe Music, MicroG RE, TermuxHub, Island F-Droid, AppManagerNG, Swiff and Background Check.
