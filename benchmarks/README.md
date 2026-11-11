# Z benchmarks

This directory contains the Z benchmark suite and the harness that compares Z
against C on the same workload.

## Requirements

- Java 21 or newer
- `clang`, `opt`, and `llvm-as`
- Python 3.9 or newer
- `taskset` for CPU pinning (optional)
- `mvn` to build the compiler jar (optional when the jar already exists)

## Run

```sh
python3 benchmarks/bench.py --runs 10 --warmup 2
```

The default run writes `benchmarks/RESULTS.md` and
`benchmarks/results/latest.json`.

Useful variants:

```sh
python3 benchmarks/bench.py --quick
python3 benchmarks/bench.py --list
python3 benchmarks/bench.py --case primes --case hash
python3 benchmarks/bench.py --category allocation --category strings
python3 benchmarks/bench.py --no-pin
python3 benchmarks/bench.py --output -
```

`benchmarks/run.sh` is a wrapper around the same script:

```sh
benchmarks/run.sh --runs 10
```
