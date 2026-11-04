#!/usr/bin/env python3
"""Compare GreenPulse outputs without modifying either input file.

Default normalization:
- Remove a leading historical Windows command-prompt line only if it is
  actually a `...>java Scheduler ...` line.
- Ignore the two machine-dependent scheduling timing lines.
Everything else is compared exactly after newline normalization.
"""
from __future__ import annotations
import argparse
import difflib
import re
import sys
from pathlib import Path

COMMAND_LINE = re.compile(r"^[A-Za-z]:\\.*>java\s+Scheduler(?:\s|$)", re.IGNORECASE)
TIMING_PATTERNS = (
    re.compile(r"^\s*Total Scheduling Time\s*:", re.IGNORECASE),
    re.compile(r"^\s*Average Scheduling Latency\s*:", re.IGNORECASE),
)

def normalized_lines(path: Path, strict: bool) -> list[str]:
    text = path.read_text(encoding="utf-8-sig", errors="replace")
    lines = text.replace("\r\n", "\n").replace("\r", "\n").splitlines()
    if not strict and lines and COMMAND_LINE.match(lines[0]):
        lines = lines[1:]
        # Historical console captures have one blank line after the echoed command.
        if lines and not lines[0].strip():
            lines = lines[1:]
    if not strict:
        lines = [line for line in lines if not any(p.search(line) for p in TIMING_PATTERNS)]
    return lines

def main() -> int:
    parser = argparse.ArgumentParser(description="Compare two GreenPulse output files.")
    parser.add_argument("--expected", required=True, type=Path)
    parser.add_argument("--actual", required=True, type=Path)
    parser.add_argument("--strict", action="store_true")
    parser.add_argument("--show-all", action="store_true")
    args = parser.parse_args()
    for label, path in (("expected", args.expected), ("actual", args.actual)):
        if not path.is_file():
            print(f"ERROR: {label} file not found: {path}", file=sys.stderr)
            return 2
    expected = normalized_lines(args.expected, args.strict)
    actual = normalized_lines(args.actual, args.strict)
    if expected == actual:
        print(f"PASS: files match ({'strict' if args.strict else 'normalized'} comparison).")
        if not args.strict:
            print("Ignored only a recognized historical command line and the two runtime timing fields.")
        print("No input files were modified.")
        return 0
    diff = list(difflib.unified_diff(expected, actual, fromfile=str(args.expected),
                                     tofile=str(args.actual), lineterm=""))
    print(f"FAIL: files differ. Expected lines: {len(expected)} | Actual lines: {len(actual)}")
    for line in (diff if args.show_all else diff[:80]):
        print(line)
    if not args.show_all and len(diff) > 80:
        print(f"... {len(diff)-80} additional diff lines omitted. Use --show-all.")
    print("No input files were modified.")
    return 1

if __name__ == "__main__":
    raise SystemExit(main())
