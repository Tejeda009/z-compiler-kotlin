#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

artifact="${1:-}"
if [[ -z "$artifact" ]]; then
    mvn -q -DskipTests package
    shopt -s nullglob
    jars=(target/z-compiler-*-all.jar)
    shopt -u nullglob
    if ((${#jars[@]} == 0)); then
        printf 'runnable jar not found after mvn package\n' >&2
        exit 1
    fi
    artifact="${jars[0]}"
fi

if [[ ! -f "$artifact" ]]; then
    printf 'artifact not found: %s\n' "$artifact" >&2
    exit 1
fi

if [[ "$artifact" == *.jar ]]; then
    run_compiler() { java -jar "$artifact" "$@"; }
else
    run_compiler() { "$artifact" "$@"; }
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

shopt -s nullglob
examples=(examples/*.z)
shopt -u nullglob

if ((${#examples[@]} == 0)); then
    printf 'no .z files found in examples\n' >&2
    exit 1
fi

failed=0
for example in "${examples[@]}"; do
    name="$(basename "$example" .z)"
    printf '\n==> compiling %s\n' "$example"

    if ! run_compiler "$example" -o "$workdir/$name"; then
        printf 'FAILED to compile %s\n' "$example" >&2
        failed=1
        continue
    fi

    if [[ ! -x "$workdir/$name" ]]; then
        printf 'FAILED: %s produced no executable\n' "$example" >&2
        failed=1
        continue
    fi

    printf '==> running %s\n' "$name"
    set +e
    "$workdir/$name" </dev/null
    status=$?
    set -e
    printf '==> %s exited with status %s\n' "$name" "$status"
done

exit "$failed"
