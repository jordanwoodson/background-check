# JetBrains Mono verification

Verified October 3, 2026. All 15 Compose typography roles and the Android base theme use bundled JetBrains Mono 2.304. Regular, Medium, SemiBold and Bold are unmodified upstream font files; the APK includes their license and provenance.

- Local verification passed 88 unit tests, release lint (0 errors, 11 warnings), and the signed release build.
- [Release workflow 37164639194](https://github.com/jordanwoodson/background-check/actions/runs/37164639194) succeeded for `9d5b95408aa49d51f981a8b5161b0d35c0a303ae`.
- [Release 1.1.1-bgcheck.3](https://github.com/jordanwoodson/background-check/releases/tag/1.1.1-bgcheck.3) has version code 6. The downloaded APK passes its published checksum and verifies with the existing signing certificate.
- APK SHA-256: `805f5bff633703e152aac6e82837814a0341c3dec275af3bc2cfa82276ea75fb`.
- All four font files in the published, minified APK match the source files byte for byte. The font license is also present and unchanged.

Phone installation and visual checks were deferred at the user's request after wireless debugging disconnected. The temporary Developer options **Stay awake** setting was enabled before the disconnection; restoring its original off setting remains a device-session cleanup item.
