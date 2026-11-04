#!/usr/bin/env python3
"""Run one GreenPulse E1 policy with a reproducible server iteration order.

Examples:
  python tools/run_e1.py --policy first-fit --run-id E1_first_fit_001
  python tools/run_e1.py --policy first-fit --server-order 2-1-3 --run-id E1_first_fit_213

The server order changes only the order in which the existing policy loops
visit the three server objects. Server capacities, power profiles, names,
initial active state, task order, scoring logic, and dataset remain unchanged.
Java source files are copied to a temporary build directory and patched there;
repository source files are never edited.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from tempfile import TemporaryDirectory

POLICIES = {
    "m1": ("Scheduler", ["1", "0.10"], "CPU + memory spreading (M1)"),
    "first-fit": ("FirstFitScheduler", [], "First Fit"),
    "best-fit": ("BestFitScheduler", [], "Best Fit"),
    "online-min-delta-power": (
        "MBFDScheduler", [], "Online minimum incremental power (not canonical MBFD)"
    ),
    "m4": ("Scheduler", ["4", "0.10"], "GreenPulse energy + carbon (M4)"),
    "m5": ("Scheduler", ["5", "0.10"], "GreenPulse full model (M5)"),
}

ORDER_TARGETS = {
    "FirstFitScheduler.java": "List<Task> tasks =",
    "BestFitScheduler.java": "List<Task> tasks =",
    "MBFDScheduler.java": "List<Task> tasks =",
    "Scheduler.java": "List<Task> tasks =",
}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def command_output(command: list[str], cwd: Path | None = None) -> str | None:
    try:
        result = subprocess.run(command, cwd=cwd, capture_output=True, text=True, check=False)
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


def parse_order(value: str) -> list[int]:
    try:
        order = [int(part) for part in value.split("-")]
    except ValueError as exc:
        raise argparse.ArgumentTypeError("use a permutation such as 1-2-3") from exc
    if sorted(order) != [1, 2, 3] or len(order) != 3:
        raise argparse.ArgumentTypeError("server order must contain 1, 2 and 3 exactly once")
    return order


def inject_server_order(source: str, filename: str, order: list[int]) -> str:
    target = ORDER_TARGETS[filename]
    index = source.find(target)
    if index < 0:
        raise RuntimeError(f"could not locate server initialization boundary in {filename}")

    rank_lines = [
        "        java.util.Map<String, Integer> greenPulseServerOrder = new java.util.HashMap<>();"
    ]
    for rank, server_id in enumerate(order):
        rank_lines.append(
            f'        greenPulseServerOrder.put("Server-{server_id}", {rank});'
        )
    rank_lines.extend([
        "        servers.sort(java.util.Comparator.comparingInt(",
        "                server -> greenPulseServerOrder.getOrDefault(server.name, Integer.MAX_VALUE)",
        "        ));",
        f'        System.out.println("Server Iteration Order : {"-".join(map(str, order))}");',
        "",
    ])
    injection = "\n".join(rank_lines) + "\n        "
    return source[:index] + injection + source[index:]


def main() -> int:
    parser = argparse.ArgumentParser(description="Run a GreenPulse E1 policy reproducibly.")
    parser.add_argument("--policy", choices=sorted(POLICIES), required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--server-order", type=parse_order, default=[1, 2, 3],
                        help="server iteration permutation, e.g. 2-1-3 (default: 1-2-3)")
    args = parser.parse_args()

    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", args.run_id):
        parser.error("--run-id must be 1-80 characters: letters, numbers, dot, underscore or hyphen")

    root = Path(__file__).resolve().parents[1]
    source_dir = root / "simulation" / "src"
    dataset_dir = root / "simulation" / "datasets"
    dataset = dataset_dir / "greenpulse_tasks.csv"
    run_dir = root / "experiments_v2" / "runs" / args.run_id
    main_class, java_args, policy_label = POLICIES[args.policy]
    order_text = "-".join(map(str, args.server_order))

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

    run_dir.mkdir(parents=True, exist_ok=False)
    started = datetime.now(timezone.utc).isoformat()

    with TemporaryDirectory(prefix="greenpulse_e1_build_") as temp:
        temp_root = Path(temp)
        temp_source_dir = temp_root / "src"
        build_dir = temp_root / "classes"
        temp_source_dir.mkdir()
        build_dir.mkdir()

        transformed_hashes: dict[str, str] = {}
        for original in java_sources:
            copied = temp_source_dir / original.name
            content = original.read_text(encoding="utf-8")
            if original.name in ORDER_TARGETS:
                content = inject_server_order(content, original.name, args.server_order)
                transformed_hashes[original.name] = hashlib.sha256(
                    content.encode("utf-8")
                ).hexdigest()
            copied.write_text(content, encoding="utf-8", newline="")

        compile_command = ["javac", "-d", str(build_dir),
                           *[str(p) for p in sorted(temp_source_dir.glob("*.java"))]]
        compile_result = subprocess.run(
            compile_command, cwd=temp_source_dir, capture_output=True, text=True, check=False
        )

        if compile_result.returncode == 0:
            run_command = ["java", "-cp", str(build_dir), main_class, *java_args]
            run_result = subprocess.run(
                run_command, cwd=dataset_dir, capture_output=True, text=True, check=False
            )
            stdout, stderr, exit_code = run_result.stdout, run_result.stderr, run_result.returncode
        else:
            run_command = None
            stdout, stderr, exit_code = "", compile_result.stderr, compile_result.returncode

        (run_dir / "stdout.txt").write_text(stdout, encoding="utf-8")
        (run_dir / "stderr.txt").write_text(stderr, encoding="utf-8")

    source_hashes = {
        str(path.relative_to(root)).replace("\\", "/"): sha256_file(path)
        for path in java_sources
    }
    manifest = {
        "schema_version": 2,
        "run_id": args.run_id,
        "started_utc": started,
        "finished_utc": datetime.now(timezone.utc).isoformat(),
        "status": "success" if exit_code == 0 else "failed",
        "exit_code": exit_code,
        "project_root": str(root),
        "git_commit": git_value(root, "rev-parse", "HEAD"),
        "git_status_short": git_value(root, "status", "--short"),
        "parameters": {
            "policy_id": args.policy,
            "policy_label": policy_label,
            "main_class": main_class,
            "java_arguments": java_args,
            "sleep_power_ratio": 0.10,
            "sleep_power_ratio_source": "SleepPowerConfig default; legacy policy classes ignore CLI args",
            "server_iteration_order": args.server_order,
            "server_iteration_order_note": "Only list traversal order changes; server identities and initial active state are unchanged.",
            "dataset": "simulation/datasets/greenpulse_tasks.csv",
        },
        "toolchain": {
            "java_version": command_output(["java", "-version"]),
            "javac_version": command_output(["javac", "-version"]),
            "python_version": sys.version,
        },
        "source_transformation": {
            "applied_in_temporary_build_copy_only": True,
            "description": "Inject server-list sort immediately after server construction and before task loading.",
            "transformed_java_source_sha256": transformed_hashes,
        },
        "commands": {
            "compile": ["javac", "-d", "<temporary_build_dir>", "<temporary source copies>"],
            "run": run_command,
        },
        "working_directory": "simulation/datasets",
        "hashes": {
            "dataset_sha256": sha256_file(dataset),
            "original_java_sources_sha256": source_hashes,
            "stdout_sha256": sha256_file(run_dir / "stdout.txt"),
            "stderr_sha256": sha256_file(run_dir / "stderr.txt"),
        },
    }
    (run_dir / "run_manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )

    print(f"Policy: {args.policy} ({policy_label})")
    print(f"Server order: {order_text}")
    print(f"Run ID: {args.run_id}")
    print(f"Status: {manifest['status']}")
    print(f"Output: {run_dir / 'stdout.txt'}")
    print(f"Manifest: {run_dir / 'run_manifest.json'}")
    print(f"Exit code: {exit_code}")
    if exit_code != 0:
        print(f"See {run_dir / 'stderr.txt'} for compiler/runtime errors.", file=sys.stderr)
        return exit_code or 1
    if "Total Tasks Loaded" not in stdout:
        print("WARNING: run exited successfully but output lacks the task-load marker.", file=sys.stderr)
        return 1
    print("Original Java source files, datasets and historical outputs were not modified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
