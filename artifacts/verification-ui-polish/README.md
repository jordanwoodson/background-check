# UI polish evidence

Final results are summarized in [the verification record](../../docs/ui-polish-verification.md).

- `build-final.log`, `local-checks.json`, `toolchain.txt`, `signature.txt`: required local release gate.
- `device-tests-final.log`: 55 passing emulator tests, including real Shizuku operations on the dedicated fixture.
- `captures-normal.log`: 15 passing final capture and overview/app sorting checks.
- `captures-large-text.log`, `captures-largest-text.log`, `captures-landscape.log`: 11 passing capture checks each.
- `reduced-motion-tests.log`: 18 passing checks with Android animation scales at zero.
- `fixture-restored.txt`: fixture background app-op after the undo flow.
- `after/ux-captures`: 27 current screenshots for each of normal, 1.5×, 2× and landscape configurations, using supplied sample data.
- `before/ux-captures`: historical baseline screenshots. `access-regression-before.log` intentionally demonstrates the old authorized-access failure.

Other build/test logs and monitor diagnosis captures preserve intermediate investigation evidence; they are not the final acceptance results. Physical-phone delivery was not verified in this run.
