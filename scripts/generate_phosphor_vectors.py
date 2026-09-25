#!/usr/bin/env python3
"""Convert the selected Phosphor 2.1.1 SVG glyphs to Android vectors."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET
from xml.sax.saxutils import quoteattr


REGULAR_ICONS = (
    "archive",
    "arrow-square-out",
    "bell-ringing",
    "camera",
    "caret-right",
    "check-circle",
    "clipboard-text",
    "clock-counter-clockwise",
    "file-text",
    "gear",
    "info",
    "link",
    "list-plus",
    "magnifying-glass",
    "map-pin",
    "microphone",
    "note-pencil",
    "pencil-simple",
    "plus",
    "selection",
    "shield",
    "squares-four",
    "translate",
    "user",
    "warning-circle",
    "x",
)
FILLED_ICONS = (
    "clock-counter-clockwise",
    "file-text",
    "gear",
    "squares-four",
)


def convert(source: Path, target: Path) -> None:
    root = ET.parse(source).getroot()
    if root.attrib.get("viewBox") != "0 0 256 256":
        raise ValueError(f"unexpected viewBox: {source}")
    paths = list(root)
    if not paths or any(path.tag.rsplit("}", 1)[-1] != "path" for path in paths):
        raise ValueError(f"unexpected SVG elements: {source}")
    if any(set(path.attrib) != {"d"} for path in paths):
        raise ValueError(f"unexpected path attributes: {source}")

    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp"',
        '    android:height="24dp"',
        '    android:viewportWidth="256"',
        '    android:viewportHeight="256">',
    ]
    for path in paths:
        lines.extend((
            '    <path',
            '        android:fillColor="#FF000000"',
            f'        android:pathData={quoteattr(path.attrib["d"])} />',
        ))
    lines.append('</vector>')
    with target.open("w", encoding="utf-8", newline="\n") as output:
        output.write("\n".join(lines) + "\n")


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: generate_phosphor_vectors.py <core-package-assets>")
    assets = Path(sys.argv[1])
    drawable = Path(__file__).resolve().parents[1] / "app/src/main/res/drawable"
    for name in REGULAR_ICONS:
        convert(
            assets / "regular" / f"{name}.svg",
            drawable / f"ic_ph_{name.replace('-', '_')}.xml",
        )
    for name in FILLED_ICONS:
        convert(
            assets / "fill" / f"{name}-fill.svg",
            drawable / f"ic_ph_{name.replace('-', '_')}_fill.xml",
        )


if __name__ == "__main__":
    main()
