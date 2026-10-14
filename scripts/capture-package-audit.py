#!/usr/bin/env python3
"""Read a device over ADB and report which packages the bundled guide and debloat list describe.

Raw inventory stays in the caller's output directory. This script never installs,
removes, disables, launches or changes a package or a phone setting.
"""

import argparse
import json
from pathlib import Path
import subprocess
import zipfile


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

    resources = Path(__file__).resolve().parents[1] / "app/src/main/resources"
    guide = json.loads((resources / "package-guide.json").read_text())["packages"]
    with zipfile.ZipFile(resources / "cana-debloat-list.zip") as archive:
        listed = {record["id"] for name in archive.namelist() if name.count("/") == 1 and name.endswith(".json")
                  for record in json.loads(archive.read(name))}
    identities = with_libraries | apex
    described = identities & (guide.keys() | listed)
    summary = {
        "recordedApps": len(apps),
        "installedSystemApps": len(installed & system),
        "installedUserApps": len(installed - system),
        "uninstalledRecords": len(apps - installed),
        "hiddenLibraries": sorted(with_libraries - apps),
        "apexModules": len(apex),
        "describedIdentities": len(described),
        "unlistedIdentities": len(identities - described),
    }
    (args.output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
