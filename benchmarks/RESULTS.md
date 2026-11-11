# Z benchmark results

Generated on `2026-11-06T19:44:27Z` from commit `74a363b` (`Merge pull request #234 from the-big-zed/changelog/update`) on branch `performance/allocator-2`.

Positive `Z vs C` means Z is faster. The headline uses the fastest run of each binary. The target is a geometric mean of at least 1.20x across the suite.

## Summary

| Metric | Value |
| --- | ---: |
| Cases | 18 |
| Geometric mean speedup (best) | 1.222x |
| Median case speedup (best) | 1.000x |
| Geometric mean speedup (median) | 1.245x |
| Z faster | 12 |
| C faster | 6 |
| Ties | 0 |
| Total C best | 4848.249 ms |
| Total Z best | 4415.417 ms |
| Highest C RSD | 31.06% (fibonacci) |
| Highest Z RSD | 34.36% (ptr_accum) |

Run-to-run variance above 5%: `fibonacci`, `collatz`, `pi`, `ackermann`, `mandelbrot`, `hash`, `alloc_churn`, `string_concat`, `fib`, `collatz`, `primes`, `digit_sum`, `concat_chain`, `ptr_accum`. Treat those speedups as indicative and rerun on an idle machine.

## Categories

| Category | Cases | Geometric mean (best) | Geometric mean (median) |
| --- | ---: | ---: | ---: |
| alias | 1 | 1.000x | 1.000x |
| allocation | 1 | 2.009x | 1.982x |
| calls | 1 | 1.000x | 1.000x |
| compute | 7 | 1.282x | 1.345x |
| float | 1 | 1.000x | 1.000x |
| integer | 4 | 1.029x | 1.029x |
| memory | 1 | 1.438x | 1.466x |
| pointers | 1 | 1.000x | 1.000x |
| strings | 1 | 2.011x | 1.981x |

## Results

| Benchmark | Category | C best (ms) | Z best (ms) | Z vs C | C median (ms) | Z median (ms) | C RSD | Z RSD |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| fibonacci | compute | 365.090 | 214.579 | +41.2% | 516.473 | 215.465 | 31.06% | 26.58% |
| collatz | compute | 214.632 | 214.537 | +0.0% | 415.555 | 415.546 | 21.23% | 19.44% |
| pi | compute | 164.344 | 64.138 | +61.0% | 164.769 | 64.602 | 0.19% | 7.79% |
| primes | compute | 615.045 | 615.295 | -0.0% | 615.899 | 615.822 | 0.06% | 0.06% |
| ackermann | compute | 64.160 | 64.160 | +0.0% | 64.573 | 64.553 | 14.86% | 21.68% |
| mandelbrot | compute | 114.172 | 114.182 | -0.0% | 214.913 | 164.821 | 26.40% | 16.38% |
| hash | compute | 214.563 | 164.499 | +23.3% | 214.913 | 214.842 | 0.11% | 8.13% |
| alias | alias | 114.310 | 114.266 | +0.0% | 114.655 | 114.616 | 0.20% | 0.20% |
| alloc_churn | allocation | 31.982 | 15.919 | +50.2% | 32.405 | 16.347 | 9.82% | 28.85% |
| string_concat | strings | 32.002 | 15.917 | +50.3% | 32.397 | 16.351 | 10.22% | 1.71% |
| fib | calls | 514.921 | 514.795 | +0.0% | 916.868 | 917.136 | 29.59% | 28.93% |
| collatz | integer | 214.489 | 214.498 | -0.0% | 214.897 | 214.843 | 11.94% | 10.34% |
| gcd_sum | integer | 364.666 | 364.695 | -0.0% | 365.135 | 365.154 | 3.63% | 4.64% |
| primes | integer | 615.181 | 615.301 | -0.0% | 615.949 | 615.848 | 28.52% | 28.31% |
| digit_sum | integer | 464.759 | 414.659 | +10.8% | 465.610 | 415.565 | 29.90% | 33.49% |
| monte_carlo | float | 264.517 | 264.632 | -0.0% | 265.065 | 265.018 | 4.47% | 4.57% |
| concat_chain | memory | 164.268 | 114.263 | +30.4% | 315.120 | 214.901 | 30.92% | 30.01% |
| ptr_accum | pointers | 315.147 | 315.081 | +0.0% | 315.815 | 315.858 | 29.70% | 34.36% |

`best` is the fastest timed run and is the headline comparison. `median` and RSD show how stable the sample is.

## Method

- C flags: `-O3 -march=native -flto -funroll-loops -mtune=native -std=c17 -fno-builtin`.
- Z pipeline: `opt -O3 -mcpu=native; clang -O3 -flto -march=native -funroll-loops -mtune=native`.
- Warmup runs per binary: 1.
- Timed runs per binary: 100.
- Pinning: CPU 7.
- Statistics: best-of-N and median wall-clock time, plus relative standard deviation; interleaved execution.
- Correctness: both binaries must exit successfully and print the same result before timing.

## Environment

| Field | Value |
| --- | --- |
| OS | Kali GNU/Linux Rolling |
| Kernel | 7.1.5+kali-amd64 |
| CPU | AMD Ryzen 5 5500U with Radeon Graphics |
| Cores | 12 |
| Affinity | [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11] |
| CPU governor | performance |
| Load average | [1.75, 2.04, 2.49] |
| Clang | Debian clang version 21.1.8 (13) |
| opt | Debian LLVM version 21.1.8 |
| Java | openjdk version "25.0.5" 2026-10-20 |
| Working tree | dirty |

## Reproduce

```sh
python3 benchmarks/bench.py --runs 100 --warmup 1
```
