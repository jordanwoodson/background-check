# Network controls implementation plan

1. Import pinned MIT source into a library, retain license/provenance and all capabilities.
   Wire Room/KSP, serialization and Android dependencies into the existing build.
2. Adapt application lifecycle, host identity, manifest components, device-wide names,
   BuildConfig and release workflow. Add rule/identity unit tests before adapting behavior.
3. Integrate Network navigation and package deep links; apply host branding and theme.
4. Build and fix Android 36 integration issues. Test decisions and backup data; inspect
   merged manifest and minified artifact. Add runtime integration/traffic tests.
5. Run emulator/device verification, inspect screens, fix observed problems and document
   a requirement-by-requirement evidence matrix. Run required release gate, review changes,
   commit readable release notes, push main and verify CI release and certificate.

Review focus: wrong self UID, collisions with de1984 kernel state, hidden API/minification,
VPN conflict takeover, stale kernel rules on stop, Android 36 background startup,
shared-UID leakage, profile limitations, returning to host navigation, large text.

Execution: continue in the existing session on main as instructed. No signing/version changes.
