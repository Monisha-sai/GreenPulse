#!/usr/bin/env python3
"""Run a GreenPulse simulation safely and save an immutable run record.

Usage from the repository root:
  python tools/run_experiment.py --model 4 --sleep-ratio 0.10 --run-id M4_repro_001

The script compiles Java sources into a temporary directory, runs Scheduler from
simulation/datasets (required by Scheduler.DATASET_FILE), and writes only to a
new experiments_v2/runs/<run-id>/ directory. It refuses to reuse a run ID.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from tempfile import TemporaryDirectory


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def command_output(command: list[str], cwd: Path | None = None) -> str | None:
    try:
        result = subprocess.run(
            command, cwd=cwd, capture_output=True, text=True, check=False
        )
        return (result.stdout + result.stderr).strip()
    except (FileNotFoundError, OSError):
        return None


def git_value(root: Path, *args: str) -> str | None:
    try:
        result = subprocess.run(
            ["git", *args], cwd=root, capture_output=True, text=True, check=False
        )
        if result.returncode == 0:
            return result.stdout.strip()
    except (FileNotFoundError, OSError):
        pass
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description="Run a reproducible GreenPulse simulation.")
    parser.add_argument("--model", type=int, required=True, help="Scheduler model number, e.g. 4")
    parser.add_argument("--sleep-ratio", type=float, required=True, help="Sleep power ratio from 0.0 to 1.0")
    parser.add_argument("--run-id", required=True, help="Unique folder name for this run")
    args = parser.parse_args()

    if not 0.0 <= args.sleep_ratio <= 1.0:
        parser.error("--sleep-ratio must be between 0.0 and 1.0")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", args.run_id):
        parser.error("--run-id must be 1-80 characters: letters, numbers, dot, underscore or hyphen")

    root = Path(__file__).resolve().parents[1]
    source_dir = root / "simulation" / "src"
    dataset_dir = root / "simulation" / "datasets"
    dataset = dataset_dir / "greenpulse_tasks.csv"
    run_dir = root / "experiments_v2" / "runs" / args.run_id

    if not source_dir.is_dir():
        print(f"ERROR: Java source directory not found: {source_dir}", file=sys.stderr)
        return 2
    if not dataset.is_file():
        print(f"ERROR: required dataset not found: {dataset}", file=sys.stderr)
        return 2
    java_sources = sorted(source_dir.glob("*.java"))
    if not java_sources:
        print(f"ERROR: no Java source files found in {source_dir}", file=sys.stderr)
        return 2
    if run_dir.exists():
        print(f"ERROR: refusing to overwrite existing run: {run_dir}", file=sys.stderr)
        return 2

    # Create the unique destination before running. Even a failed run is retained
    # as an auditable record; an existing run is never overwritten.
    run_dir.mkdir(parents=True, exist_ok=False)
    started = datetime.now(timezone.utc).isoformat()

    with TemporaryDirectory(prefix="greenpulse_build_") as temp:
        build_dir = Path(temp) / "classes"
        build_dir.mkdir()
        compile_command = ["javac", "-d", str(build_dir), *[str(p) for p in java_sources]]
        compile_result = subprocess.run(
            compile_command, cwd=source_dir, capture_output=True, text=True, check=False
        )

        run_result = None
        if compile_result.returncode == 0:
            run_command = [
                "java", "-cp", str(build_dir), "Scheduler",
                str(args.model), f"{args.sleep_ratio:.2f}"
            ]
            run_result = subprocess.run(
                run_command, cwd=dataset_dir, capture_output=True, text=True, check=False
            )
            stdout = run_result.stdout
            stderr = run_result.stderr
            exit_code = run_result.returncode
        else:
            run_command = None
            stdout = ""
            stderr = compile_result.stderr
            exit_code = compile_result.returncode

        (run_dir / "stdout.txt").write_text(stdout, encoding="utf-8")
        (run_dir / "stderr.txt").write_text(stderr, encoding="utf-8")

    source_hashes = {
        str(path.relative_to(root)).replace("\\", "/"): sha256_file(path)
        for path in java_sources
    }
    manifest = {
        "schema_version": 1,
        "run_id": args.run_id,
        "started_utc": started,
        "finished_utc": datetime.now(timezone.utc).isoformat(),
        "status": "success" if exit_code == 0 else "failed",
        "exit_code": exit_code,
        "project_root": str(root),
        "git_commit": git_value(root, "rev-parse", "HEAD"),
        "git_status_short": git_value(root, "status", "--short"),
        "parameters": {
            "scheduler": "Scheduler",
            "model": args.model,
            "sleep_power_ratio": args.sleep_ratio,
            "dataset": "simulation/datasets/greenpulse_tasks.csv"
        },
        "toolchain": {
            "java_version": command_output(["java", "-version"]),
            "javac_version": command_output(["javac", "-version"]),
            "python_version": sys.version
        },
        "commands": {
            "compile": ["javac", "-d", "<temporary_build_dir>", "<all simulation/src/*.java>"],
            "run": ["java", "-cp", "<temporary_build_dir>", "Scheduler",
                    str(args.model), f"{args.sleep_ratio:.2f}"]
        },
        "working_directory": "simulation/datasets",
        "hashes": {
            "dataset_sha256": sha256_file(dataset),
            "java_sources_sha256": source_hashes,
            "stdout_sha256": sha256_file(run_dir / "stdout.txt"),
            "stderr_sha256": sha256_file(run_dir / "stderr.txt")
        }
    }
    (run_dir / "run_manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )

    print(f"Run ID: {args.run_id}")
    print(f"Status: {manifest['status']}")
    print(f"Output: {run_dir / 'stdout.txt'}")
    print(f"Manifest: {run_dir / 'run_manifest.json'}")
    print(f"Exit code: {exit_code}")
    if exit_code != 0:
        print(f"See {run_dir / 'stderr.txt'} for compiler/runtime errors.", file=sys.stderr)
        return exit_code or 1
    print("Existing source files, datasets and historical outputs were not modified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
