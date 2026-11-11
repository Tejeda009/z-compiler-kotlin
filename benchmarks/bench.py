#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import math
import os
import platform
import shlex
import shutil
import statistics
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BENCHMARKS = ROOT / "benchmarks"
DEFAULT_CFLAGS = "-O3 -march=native -flto -funroll-loops -mtune=native -std=c17 -fno-builtin"
Z_PIPELINE = "opt -O3 -mcpu=native; clang -O3 -flto -march=native -funroll-loops -mtune=native"
VERBOSE = False


class BenchmarkError(Exception):
    pass


def env() -> dict[str, str]:
    value = os.environ.copy()
    value["LC_ALL"] = "C"
    value["LANG"] = "C"
    return value


def run_command(command: list[str], cwd: Path | None = None, timeout: float = 600.0,
                check: bool = True) -> subprocess.CompletedProcess[str]:
    if VERBOSE:
        print("+ " + " ".join(str(part) for part in command), file=sys.stderr)
    try:
        process = subprocess.run(
            [str(part) for part in command],
            cwd=str(cwd) if cwd else None,
            env=env(),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout,
        )
    except FileNotFoundError as error:
        raise BenchmarkError(f"command not found: {command[0]}") from error
    except subprocess.TimeoutExpired as error:
        raise BenchmarkError("command timed out: " + " ".join(str(part) for part in command)) from error
    if check and process.returncode != 0:
        output = (process.stdout or "") + (process.stderr or "")
        raise BenchmarkError(
            "command failed with exit code "
            f"{process.returncode}: {' '.join(str(part) for part in command)}\n{output}"
        )
    return process


def first_line(command: list[str]) -> str:
    process = run_command(command, check=False)
    output = ((process.stdout or "") + (process.stderr or "")).strip().splitlines()
    for line in output:
        stripped = line.strip()
        if stripped and not stripped.startswith("Picked up"):
            return stripped
    return "unavailable"


def load_suite() -> list[dict[str, str]]:
    path = BENCHMARKS / "suite.json"
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise BenchmarkError(f"cannot read {path}: {error}") from error
    cases = data.get("cases")
    if not isinstance(cases, list) or not cases:
        raise BenchmarkError(f"{path} must contain a non-empty cases array")
    result: list[dict[str, str]] = []
    for item in cases:
        if not isinstance(item, dict):
            raise BenchmarkError(f"invalid case in {path}: {item!r}")
        name = item.get("name")
        category = item.get("category")
        description = item.get("description", "")
        if not isinstance(name, str) or not name:
            raise BenchmarkError(f"invalid case name in {path}: {item!r}")
        if not isinstance(category, str) or not category:
            raise BenchmarkError(f"invalid case category in {path}: {item!r}")
        if not (BENCHMARKS / f"{name}.c").is_file():
            raise BenchmarkError(f"missing C source for {name}")
        if not (BENCHMARKS / f"{name}.z").is_file():
            raise BenchmarkError(f"missing Z source for {name}")
        result.append({"name": name, "category": category, "description": str(description)})
    return result


def newest_source_mtime() -> float:
    paths = list((ROOT / "src").rglob("*.java"))
    paths.append(ROOT / "pom.xml")
    return max(path.stat().st_mtime for path in paths)


def find_jar() -> Path | None:
    jars = sorted(
        (ROOT / "target").glob("z-compiler-*-all.jar"),
        key=lambda path: path.stat().st_mtime,
        reverse=True,
    )
    return jars[0] if jars else None


def ensure_jar(force_build: bool, no_build: bool) -> Path:
    jar = find_jar()
    if no_build:
        if jar is None:
            raise BenchmarkError("no compiler jar found; run without --no-build")
        return jar
    if not force_build and jar is not None and jar.stat().st_mtime >= newest_source_mtime():
        return jar
    print("building the compiler jar", file=sys.stderr)
    run_command(
        ["mvn", "--batch-mode", "--no-transfer-progress", "-q",
         "-Dmaven.test.skip=true", "package"],
        cwd=ROOT,
        timeout=900.0,
    )
    jar = find_jar()
    if jar is None:
        raise BenchmarkError("the build did not produce target/z-compiler-*-all.jar")
    return jar


def compile_z(jar: Path, case: dict[str, str], build_dir: Path, timeout: float) -> Path:
    binary = build_dir / f"{case['name']}.zbin"
    run_command(
        ["java", "-jar", str(jar), str(BENCHMARKS / f"{case['name']}.z"), "-o", str(binary)],
        cwd=build_dir,
        timeout=timeout,
    )
    if not binary.is_file():
        raise BenchmarkError(f"the Z compiler did not create {binary}")
    return binary


def compile_c(case: dict[str, str], build_dir: Path, cflags: list[str], timeout: float) -> Path:
    binary = build_dir / f"{case['name']}.cbin"
    run_command(
        ["clang", *cflags, str(BENCHMARKS / f"{case['name']}.c"), "-o", str(binary), "-lm"],
        cwd=build_dir,
        timeout=timeout,
    )
    if not binary.is_file():
        raise BenchmarkError(f"clang did not create {binary}")
    return binary


def allowed_cpus() -> list[int]:
    try:
        return sorted(os.sched_getaffinity(0))
    except (AttributeError, OSError):
        return list(range(os.cpu_count() or 1))


def cpu_busy_fraction() -> dict[int, float]:
    def snapshot() -> dict[int, tuple[int, int]]:
        values: dict[int, tuple[int, int]] = {}
        try:
            lines = Path("/proc/stat").read_text(encoding="utf-8").splitlines()
        except OSError:
            return values
        for line in lines:
            if not line.startswith("cpu") or line.startswith("cpu "):
                continue
            parts = line.split()
            try:
                index = int(parts[0][3:])
                numbers = [int(part) for part in parts[1:]]
            except ValueError:
                continue
            if len(numbers) < 4:
                continue
            idle = numbers[3] + (numbers[4] if len(numbers) > 4 else 0)
            values[index] = (idle, sum(numbers))
        return values

    first = snapshot()
    if not first:
        return {}
    time.sleep(0.2)
    second = snapshot()
    busy: dict[int, float] = {}
    for index, (idle, total) in first.items():
        if index not in second:
            continue
        idle_after, total_after = second[index]
        total_delta = total_after - total
        idle_delta = idle_after - idle
        busy[index] = (total_delta - idle_delta) / total_delta if total_delta > 0 else 1.0
    return busy


def choose_cpu(requested: int | None) -> int | None:
    cpus = allowed_cpus()
    if requested is not None:
        if requested not in cpus:
            raise BenchmarkError(f"CPU {requested} is not in the allowed set {cpus}")
        return requested
    if len(cpus) > 1:
        busy = cpu_busy_fraction()
        candidates = [cpu for cpu in cpus if cpu in busy]
        if candidates:
            return min(candidates, key=lambda cpu: (busy[cpu], cpu))
    return cpus[0] if cpus else None


def pin_prefix(cpu: int | None, disable: bool) -> list[str]:
    if disable or cpu is None:
        return []
    if shutil.which("taskset") is None:
        return []
    return ["taskset", "-c", str(cpu)]


def validate_output(z_binary: Path, c_binary: Path, prefix: list[str],
                    timeout: float) -> str:
    z_process = run_process([*prefix, str(z_binary)], timeout=timeout, capture=True)
    c_process = run_process([*prefix, str(c_binary)], timeout=timeout, capture=True)
    if z_process.returncode != 0:
        raise BenchmarkError(
            f"Z binary exited with {z_process.returncode}: {(z_process.stderr or '').strip()}"
        )
    if c_process.returncode != 0:
        raise BenchmarkError(
            f"C binary exited with {c_process.returncode}: {(c_process.stderr or '').strip()}"
        )
    z_output = (z_process.stdout or "").strip()
    c_output = (c_process.stdout or "").strip()
    if z_output != c_output:
        raise BenchmarkError(
            "output mismatch\n"
            f"Z: {z_output!r}\n"
            f"C: {c_output!r}"
        )
    return z_output


def run_process(command: list[str], timeout: float, capture: bool) -> subprocess.CompletedProcess[str]:
    if VERBOSE:
        print("+ " + " ".join(command), file=sys.stderr)
    try:
        return subprocess.run(
            command,
            env=env(),
            text=True,
            stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
            stderr=subprocess.PIPE if capture else subprocess.DEVNULL,
            timeout=timeout,
        )
    except FileNotFoundError as error:
        raise BenchmarkError(f"command not found: {command[0]}") from error
    except subprocess.TimeoutExpired as error:
        raise BenchmarkError("command timed out: " + " ".join(command)) from error


def time_once(binary: Path, prefix: list[str], timeout: float) -> float:
    start = time.perf_counter_ns()
    process = run_process([*prefix, str(binary)], timeout=timeout, capture=False)
    elapsed = (time.perf_counter_ns() - start) / 1_000_000.0
    if process.returncode != 0:
        raise BenchmarkError(f"{binary.name} exited with {process.returncode}")
    return elapsed


def measure_pair(z_binary: Path, c_binary: Path, prefix: list[str],
                 warmup: int, runs: int, timeout: float) -> tuple[list[float], list[float]]:
    z_values: list[float] = []
    c_values: list[float] = []
    for _ in range(warmup):
        time_once(z_binary, prefix, timeout)
        time_once(c_binary, prefix, timeout)
    for index in range(runs):
        order = (z_binary, c_binary) if index % 2 == 0 else (c_binary, z_binary)
        for binary in order:
            value = time_once(binary, prefix, timeout)
            if binary == z_binary:
                z_values.append(value)
            else:
                c_values.append(value)
    return z_values, c_values


def summarize(values: list[float]) -> dict[str, float | int]:
    count = len(values)
    mean = statistics.fmean(values)
    median = statistics.median(values)
    deviation = statistics.stdev(values) if count > 1 else 0.0
    return {
        "runs": count,
        "median_ms": median,
        "mean_ms": mean,
        "stdev_ms": deviation,
        "min_ms": min(values),
        "max_ms": max(values),
        "rsd_pct": (deviation / mean * 100.0) if mean else 0.0,
    }


def geometric_mean(values: list[float]) -> float:
    if not values:
        return 0.0
    return math.exp(sum(math.log(value) for value in values) / len(values))


def cpu_model() -> str:
    try:
        for line in Path("/proc/cpuinfo").read_text(encoding="utf-8").splitlines():
            if line.lower().startswith("model name"):
                return line.split(":", 1)[1].strip()
    except OSError:
        pass
    return platform.processor() or "unknown"


def os_name() -> str:
    try:
        for line in Path("/etc/os-release").read_text(encoding="utf-8").splitlines():
            if line.startswith("PRETTY_NAME="):
                return line.split("=", 1)[1].strip().strip('"')
    except OSError:
        pass
    return platform.system()


def git_value(*args: str) -> str:
    process = run_command(["git", *args], cwd=ROOT, check=False)
    return (process.stdout or "").strip()


def cpu_governor(cpu: int | None) -> str:
    indexes = [cpu] if cpu is not None else []
    indexes.extend(index for index in allowed_cpus() if index not in indexes)
    for index in indexes:
        path = Path(f"/sys/devices/system/cpu/cpu{index}/cpufreq/scaling_governor")
        try:
            return path.read_text(encoding="utf-8").strip()
        except OSError:
            continue
    return "unavailable"


def load_average() -> list[float]:
    try:
        return [round(value, 2) for value in os.getloadavg()]
    except OSError:
        return []


def metadata(cpu: int | None) -> dict[str, object]:
    status = git_value("status", "--porcelain")
    return {
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "git": {
            "commit": git_value("rev-parse", "--short", "HEAD") or "unknown",
            "subject": git_value("log", "-1", "--format=%s") or "unknown",
            "branch": git_value("rev-parse", "--abbrev-ref", "HEAD") or "unknown",
            "dirty": bool(status),
        },
        "host": {
            "os": os_name(),
            "kernel": platform.release(),
            "machine": platform.machine(),
            "cpu": cpu_model(),
            "cpus": os.cpu_count() or 1,
            "affinity": allowed_cpus(),
            "governor": cpu_governor(cpu),
            "load_average": load_average(),
            "python": platform.python_version(),
        },
        "toolchain": {
            "clang": first_line(["clang", "--version"]),
            "opt": first_line(["opt", "--version"]),
            "java": first_line(["java", "-version"]),
        },
    }


def escape_md(value: object) -> str:
    return str(value).replace("|", "\\|")


def fmt_ms(value: float) -> str:
    return f"{value:.3f}"


def fmt_pct(value: float) -> str:
    return f"{value:+.1f}%"


def render_markdown(data: dict[str, object]) -> str:
    cases = data["cases"]
    summary_data = data["summary"]
    meta = data["metadata"]
    method = data["method"]
    failures = data["failures"]
    lines: list[str] = []
    lines.append("# Z benchmark results")
    lines.append("")
    lines.append(
        "Generated on `{}` from commit `{}` (`{}`) on branch `{}`.".format(
            meta["generated_at"], meta["git"]["commit"], meta["git"]["subject"], meta["git"]["branch"]
        )
    )
    lines.append("")
    lines.append(
        "Positive `Z vs C` means Z is faster. The headline uses the fastest run of each binary. "
        "The target is a geometric mean of at least 1.20x across the suite."
    )
    lines.append("")
    lines.append("## Summary")
    lines.append("")
    lines.append("| Metric | Value |")
    lines.append("| --- | ---: |")
    lines.append(f"| Cases | {summary_data['cases']} |")
    lines.append(
        f"| Geometric mean speedup (best) | {summary_data['geometric_mean_speedup']:.3f}x |"
    )
    lines.append(f"| Median case speedup (best) | {summary_data['median_speedup']:.3f}x |")
    lines.append(
        "| Geometric mean speedup (median) | "
        f"{summary_data['geometric_mean_median_speedup']:.3f}x |"
    )
    lines.append(f"| Z faster | {summary_data['z_faster']} |")
    lines.append(f"| C faster | {summary_data['c_faster']} |")
    lines.append(f"| Ties | {summary_data['ties']} |")
    lines.append(f"| Total C best | {fmt_ms(summary_data['total_c_ms'])} ms |")
    lines.append(f"| Total Z best | {fmt_ms(summary_data['total_z_ms'])} ms |")
    lines.append(
        "| Highest C RSD | {:.2f}% ({}) |".format(
            summary_data["max_c_rsd_pct"],
            escape_md(summary_data["max_c_rsd_case"]) if summary_data["max_c_rsd_case"] else "n/a",
        )
    )
    lines.append(
        "| Highest Z RSD | {:.2f}% ({}) |".format(
            summary_data["max_z_rsd_pct"],
            escape_md(summary_data["max_z_rsd_case"]) if summary_data["max_z_rsd_case"] else "n/a",
        )
    )
    lines.append("")
    noisy = summary_data["noisy"]
    if noisy:
        lines.append(
            "Run-to-run variance above 5%: "
            + ", ".join(f"`{escape_md(name)}`" for name in noisy)
            + ". Treat those speedups as indicative and rerun on an idle machine."
        )
        lines.append("")
    lines.append("## Categories")
    lines.append("")
    lines.append("| Category | Cases | Geometric mean (best) | Geometric mean (median) |")
    lines.append("| --- | ---: | ---: | ---: |")
    for category in summary_data["categories"]:
        lines.append(
            "| {} | {} | {:.3f}x | {:.3f}x |".format(
                escape_md(category["name"]),
                category["cases"],
                category["geometric_mean_speedup"],
                category["geometric_mean_median_speedup"],
            )
        )
    lines.append("")
    lines.append("## Results")
    lines.append("")
    lines.append(
        "| Benchmark | Category | C best (ms) | Z best (ms) | Z vs C | C median (ms) | "
        "Z median (ms) | C RSD | Z RSD |"
    )
    lines.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
    for case in cases:
        lines.append(
            "| {} | {} | {} | {} | {} | {} | {} | {:.2f}% | {:.2f}% |".format(
                escape_md(case["name"]),
                escape_md(case["category"]),
                fmt_ms(case["c"]["min_ms"]),
                fmt_ms(case["z"]["min_ms"]),
                fmt_pct(case["delta_pct"]),
                fmt_ms(case["c"]["median_ms"]),
                fmt_ms(case["z"]["median_ms"]),
                case["c"]["rsd_pct"],
                case["z"]["rsd_pct"],
            )
        )
    lines.append("")
    lines.append(
        "`best` is the fastest timed run and is the headline comparison. "
        "`median` and RSD show how stable the sample is."
    )
    lines.append("")
    if failures:
        lines.append("## Failures")
        lines.append("")
        lines.append("| Benchmark | Error |")
        lines.append("| --- | --- |")
        for failure in failures:
            lines.append(f"| {escape_md(failure['name'])} | {escape_md(failure['error'])} |")
        lines.append("")
    lines.append("## Method")
    lines.append("")
    lines.append(f"- C flags: `{method['c_flags']}`.")
    lines.append(f"- Z pipeline: `{method['z_pipeline']}`.")
    lines.append(f"- Warmup runs per binary: {method['warmup']}.")
    lines.append(f"- Timed runs per binary: {method['runs']}.")
    lines.append(f"- Pinning: {method['pinning']}.")
    lines.append(
        "- Statistics: best-of-N and median wall-clock time, plus relative standard deviation; "
        "interleaved execution."
    )
    lines.append("- Correctness: both binaries must exit successfully and print the same result before timing.")
    lines.append("")
    lines.append("## Environment")
    lines.append("")
    lines.append("| Field | Value |")
    lines.append("| --- | --- |")
    lines.append(f"| OS | {escape_md(meta['host']['os'])} |")
    lines.append(f"| Kernel | {escape_md(meta['host']['kernel'])} |")
    lines.append(f"| CPU | {escape_md(meta['host']['cpu'])} |")
    lines.append(f"| Cores | {meta['host']['cpus']} |")
    lines.append(f"| Affinity | {escape_md(meta['host']['affinity'])} |")
    lines.append(f"| CPU governor | {escape_md(meta['host']['governor'])} |")
    lines.append(f"| Load average | {escape_md(meta['host']['load_average'])} |")
    lines.append(f"| Clang | {escape_md(meta['toolchain']['clang'])} |")
    lines.append(f"| opt | {escape_md(meta['toolchain']['opt'])} |")
    lines.append(f"| Java | {escape_md(meta['toolchain']['java'])} |")
    lines.append(f"| Working tree | {'dirty' if meta['git']['dirty'] else 'clean'} |")
    lines.append("")
    lines.append("## Reproduce")
    lines.append("")
    lines.append("```sh")
    lines.append(f"python3 benchmarks/bench.py --runs {method['runs']} --warmup {method['warmup']}")
    lines.append("```")
    lines.append("")
    return "\n".join(lines)


def write_json(path: Path | None, data: dict[str, object]) -> None:
    if path is None:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Compare Z against C on the benchmark suite")
    parser.add_argument("--runs", type=int, default=None)
    parser.add_argument("--warmup", type=int, default=None)
    parser.add_argument("--quick", action="store_true")
    parser.add_argument("--case", action="append", default=[])
    parser.add_argument("--category", action="append", default=[])
    parser.add_argument("--output", default=str(BENCHMARKS / "RESULTS.md"))
    parser.add_argument("--json", default=str(BENCHMARKS / "results" / "latest.json"))
    parser.add_argument("--no-json", action="store_true")
    parser.add_argument("--build", action="store_true")
    parser.add_argument("--no-build", action="store_true")
    parser.add_argument("--no-pin", action="store_true")
    parser.add_argument("--cpu", type=int, default=None)
    parser.add_argument("--timeout", type=float, default=300.0)
    parser.add_argument("--c-flags", default=os.environ.get("Z_BENCH_CFLAGS", DEFAULT_CFLAGS))
    parser.add_argument("--keep", action="store_true")
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--list", action="store_true")
    return parser.parse_args()


def main() -> int:
    global VERBOSE
    args = parse_args()
    VERBOSE = args.verbose
    cases = load_suite()

    if args.list:
        for case in cases:
            print(f"{case['name']}\t{case['category']}\t{case['description']}")
        return 0

    if args.case:
        requested = set(args.case)
        cases = [case for case in cases if case["name"] in requested]
        missing = requested - {case["name"] for case in cases}
        if missing:
            raise BenchmarkError("unknown benchmark(s): " + ", ".join(sorted(missing)))
    if args.category:
        requested = set(args.category)
        cases = [case for case in cases if case["category"] in requested]
    if not cases:
        raise BenchmarkError("no benchmarks selected")

    runs = args.runs
    if runs is None:
        runs = 3 if args.quick else int(os.environ.get("Z_BENCH_RUNS", os.environ.get("Z_BENCH_REPEATS", "10")))
    warmup = args.warmup
    if warmup is None:
        warmup = 1 if args.quick else int(os.environ.get("Z_BENCH_WARMUP", "2"))
    if runs < 1:
        raise BenchmarkError("--runs must be at least 1")
    if warmup < 0:
        raise BenchmarkError("--warmup cannot be negative")

    cflags = shlex.split(args.c_flags)
    jar = ensure_jar(args.build, args.no_build)
    requested_cpu = args.cpu
    if requested_cpu is None and os.environ.get("Z_BENCH_CPU"):
        requested_cpu = int(os.environ["Z_BENCH_CPU"])
    cpu = choose_cpu(requested_cpu)
    prefix = pin_prefix(cpu, args.no_pin)
    pinning = f"CPU {cpu}" if prefix else "disabled"

    build_dir = Path(tempfile.mkdtemp(prefix="zbench-"))
    results: list[dict[str, object]] = []
    failures: list[dict[str, str]] = []
    try:
        for index, case in enumerate(cases, start=1):
            name = case["name"]
            print(f"[{index}/{len(cases)}] {name}", file=sys.stderr)
            try:
                z_binary = compile_z(jar, case, build_dir, args.timeout)
                c_binary = compile_c(case, build_dir, cflags, args.timeout)
                output = validate_output(z_binary, c_binary, prefix, args.timeout)
                z_values, c_values = measure_pair(
                    z_binary, c_binary, prefix, warmup, runs, args.timeout
                )
                z_stats = summarize(z_values)
                c_stats = summarize(c_values)
                speedup = c_stats["min_ms"] / z_stats["min_ms"] if z_stats["min_ms"] else 0.0
                median_speedup = (
                    c_stats["median_ms"] / z_stats["median_ms"] if z_stats["median_ms"] else 0.0
                )
                delta = (
                    (c_stats["min_ms"] - z_stats["min_ms"]) / c_stats["min_ms"] * 100.0
                    if c_stats["min_ms"] else 0.0
                )
                median_delta = (
                    (c_stats["median_ms"] - z_stats["median_ms"]) / c_stats["median_ms"] * 100.0
                    if c_stats["median_ms"] else 0.0
                )
                results.append(
                    {
                        "name": name,
                        "category": case["category"],
                        "description": case["description"],
                        "output": output,
                        "z": {**z_stats, "samples_ms": z_values},
                        "c": {**c_stats, "samples_ms": c_values},
                        "speedup": speedup,
                        "median_speedup": median_speedup,
                        "delta_pct": delta,
                        "median_delta_pct": median_delta,
                        "pinning": pinning,
                    }
                )
                print(
                    f"  C {fmt_ms(c_stats['min_ms'])} ms | "
                    f"Z {fmt_ms(z_stats['min_ms'])} ms | "
                    f"{fmt_pct(delta)} best | {fmt_pct(median_delta)} median",
                    file=sys.stderr,
                )
            except Exception as error:
                failures.append({"name": name, "error": str(error)})
                print(f"  failed: {error}", file=sys.stderr)

        speedups = [case["speedup"] for case in results]
        median_speedups = [case["median_speedup"] for case in results]
        categories: list[dict[str, object]] = []
        for category in sorted({case["category"] for case in results}):
            group = [case for case in results if case["category"] == category]
            categories.append(
                {
                    "name": category,
                    "cases": len(group),
                    "geometric_mean_speedup": geometric_mean([case["speedup"] for case in group]),
                    "geometric_mean_median_speedup": geometric_mean(
                        [case["median_speedup"] for case in group]
                    ),
                }
            )
        summary = {
            "cases": len(results),
            "geometric_mean_speedup": geometric_mean(speedups),
            "median_speedup": statistics.median(speedups) if speedups else 0.0,
            "geometric_mean_median_speedup": geometric_mean(median_speedups),
            "z_faster": sum(1 for value in speedups if value > 1.0),
            "c_faster": sum(1 for value in speedups if value < 1.0),
            "ties": sum(1 for value in speedups if math.isclose(value, 1.0, rel_tol=1e-09, abs_tol=1e-09)),
            "total_c_ms": sum(case["c"]["min_ms"] for case in results),
            "total_z_ms": sum(case["z"]["min_ms"] for case in results),
            "max_c_rsd_pct": max((case["c"]["rsd_pct"] for case in results), default=0.0),
            "max_c_rsd_case": max(results, key=lambda case: case["c"]["rsd_pct"])["name"] if results else "",
            "max_z_rsd_pct": max((case["z"]["rsd_pct"] for case in results), default=0.0),
            "max_z_rsd_case": max(results, key=lambda case: case["z"]["rsd_pct"])["name"] if results else "",
            "noisy": [
                case["name"]
                for case in results
                if case["c"]["rsd_pct"] > 5.0 or case["z"]["rsd_pct"] > 5.0
            ],
            "categories": categories,
        }
        data = {
            "schema": 1,
            "metadata": metadata(cpu),
            "method": {
                "c_flags": " ".join(cflags),
                "z_pipeline": Z_PIPELINE,
                "warmup": warmup,
                "runs": runs,
                "pinning": pinning,
            },
            "summary": summary,
            "cases": results,
            "failures": failures,
        }
        markdown = render_markdown(data)
        output = Path(args.output)
        if args.output == "-":
            print(markdown)
        else:
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(markdown, encoding="utf-8")
        if not args.no_json and args.json:
            write_json(Path(args.json), data)
        print(f"geometric mean speedup: {summary['geometric_mean_speedup']:.3f}x", file=sys.stderr)
        if args.output != "-":
            print(f"markdown: {output}", file=sys.stderr)
        if not args.no_json and args.json:
            print(f"raw results: {args.json}", file=sys.stderr)
        if args.keep:
            print(f"build directory: {build_dir}", file=sys.stderr)
        return 1 if failures else 0
    finally:
        if not args.keep:
            shutil.rmtree(build_dir, ignore_errors=True)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except BenchmarkError as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
