# Private releases, Obtainium and Dracula terminal rebrand

The user requested a private GitHub repository and Obtainium setup like Cana, then added: once Obtainium is set up, rebrand everything with a Dracula terminal theme. Finish and verify both stages before considering the work complete.

## Stage 1 — private updates

- [x] Create `jordanwoodson/background-check` as a private repository and push the complete current project.
- [x] Add automatic signed releases on `main`, following Cana's numbered-version workflow.
- [x] Configure the existing Background Check key in Actions secrets and reject an unexpected signing certificate.
- [x] Verify local build, all 88 unit tests, release lint, workflow syntax and version allocation.
- [x] Verify the first GitHub Actions release and its downloaded APK/checksum.
- [x] Add the repository in Obtainium on the phone and install its release through Obtainium.
- [x] Verify the installed version, APK hash, retained app identity, and a subsequent Obtainium refresh.

## Stage 2 — Dracula terminal rebrand

Stage 1 succeeded: Obtainium installed `1.1.1-bgcheck.1` and refreshed it successfully. Keep **Background Check** as the app name unless the user supplies another name.

- [x] Apply the [Dracula palette](https://draculatheme.com/contribute), monospace typography and compact terminal-shaped surfaces globally.
- [x] Update Overview, Apps, Monitor, Access, sorting controls, detail sheets, confirmations, feedback and launcher/splash assets consistently.
- [x] Use readable text contrast, responsive wrapping and accessible touch targets at normal and enlarged font sizes.
- [x] Update current branding documentation and screenshots; retain clearly identified historical verification evidence.
- [x] Run the existing unit/lint/build checks and relevant device UI tests, then inspect the rendered screens and dialogs.
- [x] Push the rebrand through the same automatic release workflow and verify the new version updates through Obtainium.

Stage 2 succeeded: the signed `1.1.1-bgcheck.2` release (version code 5) was installed through Obtainium, its installed APK hash matches GitHub, existing app data was retained, and a subsequent refresh reports the installed and latest versions correctly. See [final verification](../../dracula-verification.md).

Keep the package ID, signing certificate, Shizuku integration, measurements, sorting, policy confirmation/readback and recovery behavior intact throughout.
