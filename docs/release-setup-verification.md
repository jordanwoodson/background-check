# Private GitHub and Obtainium verification

Verified on October 3, 2026, before applying the Dracula rebrand.

- GitHub reports [jordanwoodson/background-check](https://github.com/jordanwoodson/background-check) as **private**, with `main` as its default branch. The complete project was pushed at `1ecf7467aac24ed946900981e2537635fb3c9657`.
- The push triggered [Actions run 37159856681](https://github.com/jordanwoodson/background-check/actions/runs/37159856681), which completed successfully: version assignment, signing setup, unit tests, release lint, APK build, signature/version verification and release publication.
- [Release 1.1.1-bgcheck.1](https://github.com/jordanwoodson/background-check/releases/tag/1.1.1-bgcheck.1) contains one APK and `SHA256SUMS.txt`. It is neither a draft nor a prerelease.
- Downloading the release and running `sha256sum -c SHA256SUMS.txt` passed. APK SHA-256: `80fbf5ec34d5db03c5e667915e771ce21bed14906469af51a41cca0433ba1804`.
- `apksigner verify` passed with the established Background Check certificate. Package `dev.bgcheck.app`, version `1.1.1-bgcheck.1`, Android version code 4.
- Obtainium 1.6.17 on the Motorola Edge successfully fetched this private repository and its release through the normal **Add app** flow. Its existing GitHub authentication worked without changing credentials.
- Tapping **Update** in Obtainium installed the APK. Android reports `installerPackageName=dev.imranr.obtainium`. The installed APK's SHA-256 exactly matches the GitHub release.
- The package's original `firstInstallTime` and credential-encrypted data inode were unchanged, confirming an in-place update that kept the existing data directory.
- A subsequent Obtainium refresh recorded both installed and latest versions as `1.1.1-bgcheck.1`. Tracking is enabled with `trackOnly=false`, `versionDetection=true`, and `exemptFromBackgroundUpdates=false`.

Local pre-push verification passed all 88 unit tests, release lint (0 errors, 11 existing warnings), the signed release build, `actionlint`, and version-allocation checks for the initial release, successor and double-digit numbering. Keystore files, passwords and local SDK configuration were excluded from Git; the four signing values were stored in repository Actions secrets.

The Dracula rebrand will be verified separately and delivered through this same update path.
