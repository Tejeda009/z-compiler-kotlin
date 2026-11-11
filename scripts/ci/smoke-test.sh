#!/usr/bin/env bash
# Usage:
#   scripts/ci/smoke-test.sh <artifact> [expected-exit-code]
#
#   <artifact>             runnable jar (e.g. target/z-compiler-1.0-SNAPSHOT-all.jar)
#                          or a native binary (e.g. target/z-compiler)
#   [expected-exit-code]   expected exit status of the compiled program (default: 61)
#
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
artifact="${1:?usage: smoke-test.sh <jar-or-native-binary> [expected-exit-code]}"
expected="${2:-61}"
workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

note() { printf '\n==> %s\n' "$*"; }
fail() {
    printf '\nSMOKE TEST FAILED: %s\n' "$*" >&2
    exit 1
}

cd "$repo_root"

if [[ ! -f "$artifact" ]]; then
    fail "artifact not found: $artifact"
fi

if [[ "$artifact" == *.jar ]]; then
    run_artifact() { java -jar "$artifact" "$@"; }
else
    run_artifact() { "$artifact" "$@"; }
fi

note "compiler help: $artifact"
run_artifact --help >/dev/null || fail "--help returned a non-zero status"

note "compiling test.z (expects exit status $expected)"
run_artifact test.z -o "$workdir/smoke" \
    || fail "the compiler failed on test.z"

if [[ ! -x "$workdir/smoke" ]]; then
    fail "the compiler did not produce an executable at $workdir/smoke"
fi

note "running the compiled binary"
set +e
"$workdir/smoke"
status=$?
set -e

if [[ "$status" -ne "$expected" ]]; then
    fail "expected exit status $expected, got $status"
fi

printf '\nSMOKE TEST PASSED: %s compiled test.z and the binary exited with %s\n' \
    "$artifact" "$status"
