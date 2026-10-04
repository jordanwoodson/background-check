#!/usr/bin/env python3
"""Export the shared vector mark to Android and a standalone SVG preview.

Edit assets/branding/background-check-mark.svg, then run this script.
Uses only the Python standard library; Android renders the vector resources.
"""

from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / "assets/branding"
SOURCE = BRAND / "background-check-mark.svg"
BACKGROUND = "#282A36"
SVG = "{http://www.w3.org/2000/svg}"


def vector(paths, *, monochrome=False, background=False):
    result = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated from assets/branding/background-check-mark.svg. -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="108dp" android:height="108dp"',
        '    android:viewportWidth="108" android:viewportHeight="108">',
    ]
    if background:
        result.append(
            f'    <path android:fillColor="{BACKGROUND}" android:pathData="M0 0H108V108H0Z" />'
        )
    for path in paths:
        color = "#FFFFFF" if monochrome else path.attrib["fill"]
        result.extend([
            f'    <path android:fillColor="{color}" android:fillType="evenOdd"',
            f'        android:pathData="{path.attrib["d"]}" />',
        ])
    return "\n".join([*result, "</vector>", ""])


def main():
    source = SOURCE.read_text()
    paths = ET.fromstring(source).findall(f"{SVG}path")
    app = ROOT / "app/src/main/res/drawable"
    exports = {
        app / "ic_launcher_foreground.xml": vector(paths),
        app / "ic_launcher_monochrome.xml": vector(paths, monochrome=True),
        app / "ic_launcher.xml": vector(paths, background=True),
        ROOT / "network/src/main/res/drawable/bgcheck_app_icon.xml": vector(paths, background=True),
    }
    # The launcher displays the central 72 dp of the 108 dp adaptive layers.
    icon = source.replace('viewBox="0 0 108 108"', 'viewBox="18 18 72 72"')
    icon = icon.replace("</title>", '</title>\n  <rect x="18" y="18" width="72" height="72" rx="17" fill="' + BACKGROUND + '"/>')
    exports[BRAND / "background-check-icon.svg"] = icon
    for path, content in exports.items():
        path.write_text(content)
    print("Exported four Android vectors and the standalone SVG icon.")


if __name__ == "__main__":
    main()
