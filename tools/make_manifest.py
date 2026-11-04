#!/usr/bin/env python3
"""Create a read-only baseline provenance manifest and SHA256SUMS.

Run from any directory with: python tools/make_manifest.py
The script writes only two new files and refuses to overwrite either one.
It does not modify source files, datasets, or existing experiment outputs.
"""
from __future__ import annotations

import hashlib
import json
import os
import platform
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "provenance" / "baseline_manifest.json"
SUMS_PATH = ROOT / "experiments" / "SHA256SUMS"
EXCLUDED_DIRS = {
    ".git", ".idea", ".vscode", "node_modules", "target", "build",
    "dist", "__pycache__", ".venv", "venv", "provenance",
}
SOURCE_SUFFIXES = {
    ".java", ".py", ".js", ".jsx", ".ts", ".tsx", ".html", ".css",
    ".xml", ".yml", ".yaml", ".properties", ".toml", ".gradle", ".md",
    ".sh", ".ps1", ".bat", ".json",
}
DATA_SUFFIXES = {".csv", ".tsv", ".parquet", ".jsonl"}
TEXT_SUFFIXES = SOURCE_SUFFIXES | DATA_SUFFIXES | {".txt", ".sum"}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def normalized_text_sha256(path: Path) -> str | None:
    if path.suffix.lower() not in TEXT_SUFFIXES:
        return None
    try:
        raw = path.read_bytes()
        text = raw.decode("utf-8-sig")
    except (UnicodeDecodeError, OSError):
        return None
    normalized = text.replace("\r\n", "\n").replace("\r", "\n")
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def run_command(args: list[str]) -> dict[str, Any]:
    try:
        p = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, timeout=15)
        return {
            "command": args,
            "returncode": p.returncode,
            "stdout": p.stdout.strip(),
            "stderr": p.stderr.strip(),
            "available": p.returncode == 0,
        }
    except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
        return {"command": args, "available": False, "error": str(exc)}


def git_info() -> dict[str, Any]:
    inside = run_command(["git", "rev-parse", "--is-inside-work-tree"])
    if not inside.get("available"):
        return {"available": False, "reason": "Git metadata unavailable in this working copy."}
    commit = run_command(["git", "rev-parse", "HEAD"])
    branch = run_command(["git", "branch", "--show-current"])
    status = run_command(["git", "status", "--porcelain", "--untracked-files=all"])
    normalized_diff = run_command(["git", "diff", "--ignore-cr-at-eol", "--quiet"])
    staged_diff = run_command(["git", "diff", "--cached", "--quiet"])
    return {
        "available": True,
        "commit": commit.get("stdout") if commit.get("available") else None,
        "branch": branch.get("stdout") if branch.get("available") else None,
        "working_tree_status_porcelain": status.get("stdout", ""),
        "working_tree_has_changes": bool(status.get("stdout", "").strip()),
        "tracked_diff_ignoring_cr_at_eol_is_empty": normalized_diff.get("returncode") == 0,
        "staged_diff_is_empty": staged_diff.get("returncode") == 0,
        "note": "Raw Git status is retained. A CRLF/LF-only difference can appear dirty while the CR-at-EOL-normalized diff is empty.",
    }


def collect_files() -> list[Path]:
    found: list[Path] = []
    for current, dirs, files in os.walk(ROOT):
        dirs[:] = sorted(d for d in dirs if d not in EXCLUDED_DIRS)
        for name in sorted(files):
            p = Path(current) / name
            rel = p.relative_to(ROOT)
            if rel == Path("experiments/SHA256SUMS"):
                continue
            if rel.parts and rel.parts[0] == "provenance":
                continue
            # Avoid transient editor and OS metadata.
            if name in {".DS_Store", "Thumbs.db"} or name.endswith((".pyc", ".class", ".log")):
                continue
            if p.is_file():
                found.append(p)
    return found


def category(rel: Path) -> str:
    parts = rel.parts
    suffix = rel.suffix.lower()
    if parts[0] == "experiments":
        return "experiment_output_or_record"
    if "dataset" in "/".join(parts).lower() or suffix in DATA_SUFFIXES:
        return "dataset_or_data"
    if suffix in SOURCE_SUFFIXES:
        return "source_or_configuration"
    return "other_project_file"


def extract_constants() -> dict[str, Any]:
    candidates = [
        ROOT / "simulation/src/Scheduler.java",
        ROOT / "simulation/src/Server.java",
        ROOT / "simulation/src/CarbonIntensityModel.java",
        ROOT / "simulation/src/SleepPowerConfig.java",
    ]
    patterns = {
        "weights_and_model": re.compile(r"(?:static\s+)?(?:final\s+)?(?:double|int)\s+(?:CPU_WEIGHT|MEMORY_WEIGHT|ENERGY_WEIGHT|CARBON_WEIGHT|CONSOLIDATION_WEIGHT|MODEL)\s*=\s*[^;]+;"),
        "dataset_path": re.compile(r"DATASET_FILE\s*=\s*[^;]+;"),
    }
    result: dict[str, Any] = {}
    for p in candidates:
        if not p.exists():
            continue
        try:
            content = p.read_text(encoding="utf-8-sig")
        except UnicodeDecodeError:
            continue
        matches: dict[str, list[str]] = {}
        for label, pattern in patterns.items():
            vals = [m.group(0).strip() for m in pattern.finditer(content)]
            if vals:
                matches[label] = vals
        if matches:
            result[p.relative_to(ROOT).as_posix()] = matches
    return result


def main() -> int:
    # Refuse before doing any work if either output already exists.
    existing = [str(p.relative_to(ROOT)) for p in (MANIFEST_PATH, SUMS_PATH) if p.exists()]
    if existing:
        print("Refusing to overwrite existing output(s): " + ", ".join(existing), file=sys.stderr)
        print("Move or rename them manually only after preserving a copy.", file=sys.stderr)
        return 2

    files = collect_files()
    records = []
    sums_lines = []
    for p in files:
        rel = p.relative_to(ROOT).as_posix()
        digest = sha256(p)
        rec = {
            "path": rel,
            "category": category(Path(rel)),
            "size_bytes": p.stat().st_size,
            "sha256": digest,
        }
        normalized = normalized_text_sha256(p)
        if normalized is not None:
            rec["normalized_text_sha256_lf"] = normalized
        records.append(rec)
        if rec["category"] in {"source_or_configuration", "dataset_or_data", "experiment_output_or_record"}:
            sums_lines.append(f"{digest}  {rel}")

    java = run_command(["java", "-version"])
    python_version = sys.version.replace("\n", " ")
    manifest = {
        "manifest_schema": "greenpulse-baseline-provenance-v1",
        "project": "GreenPulse",
        "generated_at_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "repository_root": ".",
        "git": git_info(),
        "environment": {
            "platform": platform.platform(),
            "python_version": python_version,
            "java_version": java.get("stderr") or java.get("stdout") if java.get("available") else None,
            "java_version_probe": java,
        },
        "file_count": len(records),
        "files": records,
        "source_constants_detected": extract_constants(),
        "known_historical_issue": {
            "file": "experiments/outputs/M5_Full_10.txt",
            "issue": "Previously identified as mislabeled in the audit. This script records and hashes the file but does not rename, edit, or infer corrected contents.",
            "handling": "Preserve unchanged; resolve in a separately documented verification step.",
        },
        "limitations": [
            "This manifest describes files present in this repository snapshot; it cannot prove the provenance of an absent raw dataset.",
            "A dataset checksum identifies the exact file present, not the upstream source URL, extraction query, filtering rules, or license.",
            "The generated timestamp changes on each independently generated manifest; per-file SHA-256 values are the stability check.",
        ],
    }

    MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)
    SUMS_PATH.parent.mkdir(parents=True, exist_ok=True)
    # Use exclusive creation to avoid races and accidental overwrite.
    try:
        with MANIFEST_PATH.open("x", encoding="utf-8", newline="\n") as f:
            json.dump(manifest, f, indent=2, ensure_ascii=False)
            f.write("\n")
        with SUMS_PATH.open("x", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(sorted(sums_lines)) + "\n")
    except FileExistsError:
        print("A target appeared during generation; refusing to overwrite.", file=sys.stderr)
        # Do not remove anything: a partial creation, if any, must be reviewed manually.
        return 3

    print(f"Created: {MANIFEST_PATH.relative_to(ROOT)}")
    print(f"Created: {SUMS_PATH.relative_to(ROOT)}")
    print(f"Files recorded: {len(records)}")
    print(f"Checksum entries: {len(sums_lines)}")
    print(f"Git commit: {manifest['git'].get('commit')}")
    print(f"Git working tree has changes: {manifest['git'].get('working_tree_has_changes')}")
    print("No existing source, dataset, or experiment output was modified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
