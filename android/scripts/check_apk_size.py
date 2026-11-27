#!/usr/bin/env python3
"""Checks that a built APK is inside the size budget and says where its bytes are.

    check_apk_size.py APK [--budget BYTES] [--summary FILE]

The budget is one number, here, so the release workflow and the Android workflow cannot disagree about it. R8 took the APK
from 30 MB to 9.8 MB (v0.58.0); v1.13.0 is 12.56 MB and v1.13.11 13.16 MB (the budget went from 13 to 14 MiB in v1.13.12), of which 10.7 MB is the two dex files (stored uncompressed, which is the
Android Gradle Plugin default for minSdk 28 and newer), 1.15 MB is resources.arsc (four languages) and 0.3 MB is fonts.
docs/PERFORMANCE.md has the history and the reasons.

Exit status: 0 inside the budget (a GitHub warning is printed from 95 percent), 1 over the budget, 2 when the file is missing.
Only the Python standard library is needed.
"""

import argparse
import sys
import zipfile
from pathlib import Path

MIB = 1024 * 1024
BUDGET_BYTES = 14 * MIB
WARN_FROM = 0.95


def groups(apk: Path) -> dict:
    """Bytes in the APK per kind of content (the compressed size of each entry, which is what the file costs)."""
    totals = {
        "code (dex)": 0,
        "resources.arsc": 0,
        "res/ (fonts, images, layouts)": 0,
        "lib/ (native)": 0,
        "everything else": 0,
    }
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.infolist():
            name = entry.filename
            if name.endswith(".dex") and "/" not in name:
                kind = "code (dex)"
            elif name == "resources.arsc":
                kind = "resources.arsc"
            elif name.startswith("res/"):
                kind = "res/ (fonts, images, layouts)"
            elif name.startswith("lib/"):
                kind = "lib/ (native)"
            else:
                kind = "everything else"
            totals[kind] += entry.compress_size
    return totals


def largest(apk: Path, count: int = 5) -> list:
    with zipfile.ZipFile(apk) as archive:
        entries = sorted(archive.infolist(), key=lambda entry: -entry.compress_size)
    return [(entry.filename, entry.compress_size) for entry in entries[:count]]


def report(apk: Path, budget: int) -> list:
    size = apk.stat().st_size
    lines = [
        f"APK size: {size:,} bytes ({size / MIB:.2f} MiB) of a budget of {budget:,} bytes ({budget / MIB:.2f} MiB); "
        f"headroom {budget - size:,} bytes ({(budget - size) / budget:.1%})",
        "Where the bytes are:",
    ]
    for kind, total in groups(apk).items():
        lines.append(f"  {kind:<32} {total:>12,} bytes")
    lines.append("Largest entries:")
    for name, total in largest(apk):
        lines.append(f"  {name:<32} {total:>12,} bytes")
    return lines


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("apk")
    parser.add_argument("--budget", type=int, default=BUDGET_BYTES, help="the limit in bytes (default: the project budget)")
    parser.add_argument("--summary", help="also append the report, as a code block, to this file (GITHUB_STEP_SUMMARY)")
    args = parser.parse_args(argv)

    apk = Path(args.apk)
    if not apk.is_file():
        print(f"APK not found: {apk}")
        return 2

    lines = report(apk, args.budget)
    print("\n".join(lines))
    if args.summary:
        with open(args.summary, "a", encoding="utf-8") as summary:
            summary.write("### APK size\n\n```\n" + "\n".join(lines) + "\n```\n")

    size = apk.stat().st_size
    if size > args.budget:
        print(f"The APK is larger than the budget of {args.budget:,} bytes ({args.budget / MIB:.0f} MiB).")
        return 1
    if size > args.budget * WARN_FROM:
        print(f"::warning::The APK uses {size / args.budget:.0%} of its size budget: look at what is growing before the next release.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
