# Network and internet controls

Requested outcome: Background Check includes the complete feature set in
https://github.com/dorumrr/de1984, integrated into its existing Android app.
Reference revision: deff1691a1ee9a57baab8047885464b7d3a20209 (MIT).

## Implementation

Import the source as the `network` Android library, namespaced `dev.bgcheck.network`.
Keep the implementation and complete firewall/package/settings flows together;
Background Check opens them through a Network navigation item and app details.
Use the existing application ID, signing key, release numbering, Dracula colors
and JetBrains Mono. Keep upstream license and provenance in source and APK.
No second launcher and no separate app installation. Android back returns to
Background Check. All state remains local. Existing monitoring and controls stay usable.

The module owns firewall lifetime, Room rules, permission acquisition, root/Shizuku
backends, VPN service, package notifications, boot restore, widget and tile. Device-wide
iptables chains and boot scripts must use Background Check names to avoid collisions
with an installed copy of de1984. Self protection must use the host application ID.
Release CI must build/test the module and trigger for its changes.

## Feature coverage to preserve and verify

- Automatic and manual selection of iptables, ConnectivityManager, NetworkPolicyManager,
  and local drop-only VPN, including capability restrictions and fallback/recovery.
- Wi-Fi, mobile, roaming, LAN (iptables), screen-off rules; per-app and batch controls;
  default allow/block policies; shared UID and multi-profile semantics; protected apps.
- Live network and screen monitoring, enabled intent versus actual enforcement health,
  permission loss, VPN conflicts/consent/lockdown warnings, start/stop and error recovery.
- Persistent rules, backup/restore, defaults and reset; installed-app changes and alerts.
- Package search/filter/sort, enable/disable, force stop, uninstall and restore system
  apps, batch operations, app details and package list export.
- Boot restore, rooted boot protection, captive portal settings, Quick Settings tile,
  home-screen widget, notification controls, local diagnostics and export.
- Appearance, language, display and notification preferences, help and attribution.

## Verification

Unit tests cover rule decisions, shared UIDs, profile boundaries, backend selection,
backup roundtrips and host identity. Runtime tests cover navigation, permission denial,
VPN blocking and unblocking with a disposable traffic fixture, persistence and services.
Exercise available root/Shizuku paths on test devices, never mutate unrelated phone apps.
Audit merged release manifest, signing certificate, artifact, CI and feature evidence.
The required JDK 21 / SDK 36 test/lint/release command remains the push gate.
Unverified device-dependent behavior remains explicitly outstanding, not completion.
