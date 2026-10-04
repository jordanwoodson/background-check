#!/usr/bin/env python3
"""Read a device over ADB and check every package against the bundled purpose guide.

Raw inventory stays in the caller's output directory. This script never installs,
removes, disables, launches or changes a package or a phone setting.
"""

import argparse
import json
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit ADB device serial")
    parser.add_argument("--output", type=Path, required=True, help="Private local evidence directory")
    parser.add_argument("--user", type=int, default=0)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    def shell(*command):
        result = subprocess.run(["adb", "-s", args.serial, "shell", *command],
                                check=True, capture_output=True, text=True)
        if result.stderr.strip():
            raise RuntimeError(result.stderr.strip())
        return result.stdout

    def packages(name, *flags):
        text = shell("pm", "list", "packages", *flags, "--user", str(args.user))
        (args.output / f"{name}.txt").write_text(text)
        lines = text.splitlines()
        if not lines or any(not line.startswith("package:") for line in lines):
            raise RuntimeError(f"Unrecognized package list: {name}")
        return {line.removeprefix("package:") for line in lines}

    apps = packages("apps", "-u")
    installed = packages("installed")
    system = packages("system", "-u", "-s")
    with_libraries = packages("with-libraries", "-u", "--match-libraries")
    apex_text = shell("pm", "list", "packages", "--apex-only")
    (args.output / "apex.txt").write_text(apex_text)
    apex = {line.removeprefix("package:") for line in apex_text.splitlines()}
    for name, command in {
        "overlays": ("cmd", "overlay", "list", "--user", str(args.user)),
        "packages": ("dumpsys", "package"),
        "build": ("getprop", "ro.build.fingerprint"),
    }.items():
        (args.output / f"{name}.txt").write_text(shell(*command))

    guide_path = Path(__file__).resolve().parents[1] / "app/src/main/resources/package-guide.json"
    guide = json.loads(guide_path.read_text())["packages"]
    identities = with_libraries | apex
    missing = sorted(identities - guide.keys())
    summary = {
        "recordedApps": len(apps),
        "installedSystemApps": len(installed & system),
        "installedUserApps": len(installed - system),
        "uninstalledRecords": len(apps - installed),
        "hiddenLibraries": sorted(with_libraries - apps),
        "apexModules": len(apex),
        "describedIdentities": len(identities & guide.keys()),
        "missingPurposes": missing,
    }
    (args.output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))
    if missing:
        raise SystemExit("Package guide coverage is incomplete")


if __name__ == "__main__":
    main()
