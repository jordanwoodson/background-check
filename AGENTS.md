# Working on Background Check

- Work on `main` and push it to the private `jordanwoodson/background-check` repository.
- App, Gradle and release-workflow changes pushed to `main` publish a signed GitHub release automatically. Obtainium on the phone tracks this repository, as it does Cana.
- Do not manually create release tags, publish APKs, or bump the default version. CI assigns `1.1.1-bgcheck.N` and version code `3 + N`; the locally installed 1.1.1 baseline was code 3.
- Never replace the Background Check signing key. CI checks its certificate against the established SHA-256 fingerprint before publishing.
- Keep signing files and credentials outside Git. CI uses repository Actions secrets; local builds use `~/.local/share/background-check/signing.properties`.
- Before pushing app changes, run `./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease` with JDK 21 and Android SDK 36.
- Write readable commit subjects: they become release notes. Documentation-only pushes do not publish an app update.
