# Package descriptions

The Apps list shows a short purpose beneath each app name, including system apps and packages retained by Android after removal for the current user. Open a package for its full description and description source. Search matches names, package IDs and purpose text. The same descriptions appear in Overview and related-app lists through the shared app row.

Descriptions are resolved locally, in this order:

1. An exact package ID in `PackageDescriptions.kt`, covering common Android components, Google and Motorola apps, and common user apps.
2. The app's own manifest description, if present and readable.
3. Android's app category, explicitly identified as a broad category rather than a confirmed purpose.
4. An explicit purpose-unavailable message. Unknown packages are not assigned a purpose based on their name or vendor prefix.

This guide is not exhaustive. It describes general functions, not the work a package is doing now, its trustworthiness, or whether it is safe to disable. Available features can vary by device and app version. Descriptions do not change existing protections or authorize controls.

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
