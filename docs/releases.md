# Releases and Obtainium

The source and APK releases live in the private [jordanwoodson/background-check repository](https://github.com/jordanwoodson/background-check).

## Phone setup

In Obtainium, add `https://github.com/jordanwoodson/background-check`, or open this link on the phone:

`obtainium://add/https://github.com/jordanwoodson/background-check`

Obtainium needs a GitHub token that can read this private repository, just as for Cana. An existing token works if its repository access includes Background Check. A fine-grained token needs this repository selected and **Contents: read-only** permission. Keep the token in Obtainium's GitHub settings, never in the source URL, this repository or an exported app configuration.

The [app configuration](../obtainium.json) contains no credentials. Obtainium's normal **Add app** flow downloads the release APK, identifies `dev.bgcheck.app`, and tracks updates. Leave **Track-only** off and **Version detection** on. There is one universal APK per release. Updates retain the app's data and Shizuku authorization because the package and signing key are unchanged.

See Obtainium's [source documentation](https://wiki.obtainium.imranr.dev/sources/) and [deep-link documentation](https://wiki.obtainium.imranr.dev/deep_links/).

## Automatic publishing

The [release workflow](../.github/workflows/release.yml) follows Cana's setup:

1. Push app, Gradle or release-workflow changes to `main`, or manually dispatch the workflow.
2. CI assigns the next `1.1.1-bgcheck.N` release and Android version code `3 + N`. Version 1.1.1 installed before GitHub publishing had code 3.
3. CI runs unit tests and release lint, then builds with the existing Background Check signing key.
4. CI verifies the APK package, version and signing certificate, then publishes the APK and `SHA256SUMS.txt` in a private GitHub release.
5. Obtainium checks this repository and offers or installs updates according to the phone's existing update settings.

Release jobs run sequentially. Version allocation fetches current tags so later jobs see prior releases. Failed builds do not publish a tag. Release notes use commit subjects since the prior release. Source defaults stay at the local baseline; Gradle's `releaseVersionName` and `releaseVersionCode` properties provide the assigned CI version.

The release signing certificate SHA-256 is:

```text
3d770ebfa7c2eacd7735adc9fadca193b62e59424659aed58afe2243c1879951
```

Actions uses four repository secrets: `KEYSTORE_FILE_BASE64`, `KEYSTORE_KEY_ALIAS`, `KEYSTORE_PASSWORD`, and `KEYSTORE_KEY_PASSWORD`. The private key and its properties remain in `~/.local/share/background-check/` locally. Preserve an independent backup; changing the key would prevent in-place updates.

To verify a downloaded release, download its APK and `SHA256SUMS.txt` into the same directory, then run `sha256sum -c SHA256SUMS.txt`.
