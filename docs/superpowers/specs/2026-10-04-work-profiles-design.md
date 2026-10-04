# Work profiles throughout Background Check

The requested outcome is full work-profile support across every existing feature. A personal and work installation of the same package must stay separate in app lists, policies, selections, diagnostics, history, shortcuts and package management. Existing signing, default version and automatic releases remain unchanged; work stays on main.

## Architecture and behavior

Use package name plus Android user ID as the app key, with UID and profile serial captured for mutation/recovery validation. Enumerate the current user and visible managed profiles using Android user APIs and the existing privileged Network discovery helpers. Read installed and retained-uninstalled apps per profile. Preserve profile type, name, serial and paused/locked availability. Incomplete reads are visible warnings, never empty success or a fallback to another profile.

Each list offers All and individual profile filters, with profile labels on app and history rows. Overview counts and active previews honor the selected profile. Monitor process and battery UID attribution uses the full UID, and profile filters include otherwise-unmapped processes. Device CPU, memory, battery flow and component totals remain explicitly whole-device readings. Related packages resolve within the selected app's profile. Icons, Android Settings and Network shortcuts target that profile.

Policy engines and force-stop operations use the selected profile. Before a mutation, revalidate current package/UID, profile serial/availability and existing protected/shared-UID safeguards. History persists profile identity and migrates older history to the profile owning the app's storage. Undo must never act on another installation or a recreated profile. Doze is device-wide per app identity: confirmations and history explain this, and successful changes refresh affected copies.

Collect package memory by the selected UID's observed PIDs, verify process identity around reads and mark incomplete/racing samples unavailable. Services are filtered to the selected user. Async results and remembered detail state use the full app key.

Network retains per-user package management and saved rules. Audit profile-only apps, shortcuts, notifications, backup/restore, lifecycle invalidation and settings navigation. Honor the actual Android backend scope: root iptables supports independent profiles; VPN is local to its running profile; ConnectivityManager uses independently verified full-UID rules where Android exposes the required APIs; NPM is limited on stock Android. Offer actionable routes for unavailable modes, never label unsupported protection active. Full support cannot override enterprise administrator restrictions or Android backend scope.

## Verification and delivery

Unit tests must distinguish identical packages in users 0 and 10, reject wrong-profile and stale identities, migrate history, exercise filtering/associations and validate shell commands. Managed-profile emulator tests must exercise actual inventory, independent background policy and Undo, force stop, profile-aware navigation, monitored UIDs, retained packages and profile lifecycle; firewall tests establish reach and separation for available backends. Render and inspect profile screens. Run the full required JDK 21/SDK 36 gate, review all changes, push main and inspect automatic release success. Record precise evidence and remaining platform restrictions.
