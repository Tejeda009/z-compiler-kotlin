<div align="center">
 <h1> <b>Z-Compiler</b> </h1>

  <h3><b>A Toy compiler built with Java and LLVM</b></h3>

  <p>
    <img src="https://img.shields.io/github/commit-activity/w/the-big-zed/z-compiler" alt="0">
    <img src="https://img.shields.io/github/issues-pr-closed/the-big-zed/z-compiler" alt="0">
    <img src="https://img.shields.io/github/downloads/the-big-zed/z-compiler/latest/total" alt="0">
    <img src="https://img.shields.io/github/license/the-big-zed/z-compiler" alt="LGPL-2.1">
  </p>

  <p>
    <img src="https://github.com/the-big-zed/z-compiler/actions/workflows/ci.yml/badge.svg" alt="CI">
    <img src="https://github.com/the-big-zed/z-compiler/actions/workflows/lint.yml/badge.svg" alt="Lint">
    <img src="https://github.com/the-big-zed/z-compiler/actions/workflows/release.yml/badge.svg" alt="Release">
    <a href="https://codecov.io/gh/the-big-zed/z-compiler"><img src="https://codecov.io/gh/the-big-zed/z-compiler/branch/main/graph/badge.svg" alt="Codecov"></a>
  </p>

  <p>
   <img alt="Total downloads" src="https://img.shields.io/github/downloads/the-big-zed/z-compiler/total">
   <img alt="Discord" src="https://img.shields.io/discord/1552756009385197611">
   <img alt="Closed Issues" src="https://img.shields.io/github/issues-closed/the-big-zed/z-compiler">
  </p>

  <h1><a href="https://discord.gg/hmAYfCkfz"><b>Discord</b></a></h1>
</div>

## Quick Start

**Requirements:** JDK 21, LLVM (`clang`, `opt`), Maven.

```sh
git clone https://github.com/the-big-zed/z-compiler
cd z-compiler
mvn --batch-mode --no-transfer-progress clean package
java -jar target/z-compiler-*-all.jar hello.z -o hello
./hello
```

## Hello, World

```z
proc main() {
    ret 0
}
```

## Syntax

### Types

| Z type | LLVM type | Notes |
|--------|-----------|-------|
| `int32` / `int64` | `i32` / `i64` | signed integer |
| `uint32` / `uint64` | `i32` / `i64` | unsigned integer |
| `flt32` / `flt64` | `float` / `double` | floating point |
| `bool` | `i1` | `true` / `false` |
| `str` | `i8*` | string; `+` concatenates |
| `void` | `void` | no value |
| `T*` | `ptr` | pointer to `T`; a bare `ptr` is `void*` |

### Variables and constants

```z
int32 count -> 0
int32 answer            // defaults to 0
str greeting -> "hello"
cn int32 MAX -> 100     // constant: cannot be assigned again
```

### Functions

```z
proc add(int32 a | int32 b) -> int32 {
    ret a + b
}
```

The return type is optional. Without `-> type`, `main` returns `int32` and
every other function returns `flt64`.

### Control flow

```z
if (x > 0) {
    ...
} else {
    ...
}

for (int32 i -> 0 | i < 5 | i + 1) {
    ...
}
```

`eif` is available as `else if`:

```z
if (x > 0) {
    ...
} eif (x < 0) {
    ...
} else {
    ...
}
```

### Operators

| Operator | Meaning |
|----------|---------|
| `+ - * / %` | arithmetic |
| `< > <= >= == !=` | comparison |
| `->` | assignment |
| `&x` | address of `x` |
| `*p` | load through `p` |
| `*p -> v` | store through `p` |

### Pointers

```z
proc main() {
    int32 test
    scanf("%d" | &test)     // pass the address
    printf("%d" | test)     // pass the value
    ret test
}
```

### Externs and stdlib

```z
extern printf(str | ...) -> int32   // C function; `...` is the variadic tail
import std                          // pulls in stdlib/std.z
```

### Comments

```z
// line comment
/* block comment
   across lines */
```

## Examples

Runnable examples live in the [`examples/`](./examples/) folder. Run
all of them with:

```sh
scripts/run-examples.sh
```

### Reading and printing numbers

```z
extern printf(str | ...) -> int32
extern scanf(str | ...) -> int32

proc main() {
    int32 test
    scanf("%d" | &test)
    printf("%d" | test)
    ret test
}
```

### Summing user input in a loop

```z
extern scanf(str | ...) -> int32
extern printf(str | ...) -> int32

proc add(int32 a | int32 b) -> int32 {
    ret a + b
}

proc main() {
    int32 sum -> 0
    for (int32 i -> 0 | i < 5 | i + 1) {
        int32 dummy
        printf("Insert number %d: " | i)
        scanf("%d" | &dummy)
        sum -> add(sum | dummy)
    }

    printf("The sum is: %d" | sum)
    ret 281
}
```

### Incrementing through a pointer

```z
proc inc(int32* p) -> int32 {
    *p -> *p + 1
    ret 0
}

proc main() {
    int32 x -> 0
    inc(&x)
    inc(&x)
    inc(&x)
    ret x        // 3
}
```

### Concatenating strings

```z
extern printf(str | ...) -> int32

proc main() {
    str hello -> "hello"
    str world -> " world"
    str greeting -> hello + world
    printf("%s" | greeting)
    ret 0
}
```

See [`examples/`](./examples/) for the rest, including
[`conditional.z`](./examples/conditional.z) (`if` / `else`).

## Documentation
Documentation about how the compiler works can be found in the [docs folder](./docs/)

## Changelog
Changelogs can be found in the [changelog file](./CHANGELOG.md)

## Development

```sh
mvn --batch-mode --no-transfer-progress clean verify   # build + all tests
scripts/ci/local-check.sh                              # exactly what CI runs 
```

The end-to-end tests need LLVM (`sudo apt-get install -y clang llvm`, or run
`scripts/ci/llvm.sh --install`); without it they are silently skipped, so CI
checks for the tools explicitly.

## Benchmarks

The benchmark suite compares Z against C with the same optimization flags,
validates that both binaries produce the same result, pins the measured
processes to the least busy allowed CPU, and reports best-of-N timings with
medians and run-to-run variance.

```sh
python3 benchmarks/bench.py --runs 10 --warmup 2
```

The report is written to [`benchmarks/RESULTS.md`](benchmarks/RESULTS.md) and the
raw measurements to `benchmarks/results/latest.json`. A scheduled GitHub
Actions workflow regenerates the report and opens a pull request against
`main`.

See [`benchmarks/README.md`](benchmarks/README.md) for the method, the case
list, and the fairness rules.

## Release channels

| Channel | Trigger | Published as |
| ------- | ------- | ------------ |
| **Nightly** | every night at 03:00 UTC (skipped when `nightly` already points at `main`; manual runs can force) | rolling prerelease [`nightly`](https://github.com/the-big-zed/z-compiler/releases/tag/nightly), image `ghcr.io/the-big-zed/z-compiler:nightly` |
| **Staging** | tag `v1.2.0-rc.1` | prerelease on the tag, image `ghcr.io/the-big-zed/z-compiler:rc` |
| **Production** | tag `v1.2.0` | release marked *latest* with a provenance attestation, images `:1.2.0` and `:latest` |

```shell
git tag v1.2.0-rc.1 && git push origin v1.2.0-rc.1   # staging
git tag v1.0.0 && git push origin v1.0.0             # production
```

```shell
docker run --rm -v "$PWD:/workspace" ghcr.io/the-big-zed/z-compiler:nightly test.z -o out
```
