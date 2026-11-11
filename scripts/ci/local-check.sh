#!/usr/bin/env bash

# Usage:
#   scripts/ci/local-check.sh            # lint (if tools available)
#   scripts/ci/local-check.sh --fast     # skip the optional linters
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fast=()
[[ "${1:-}" == "--fast" ]] && fast=(1)

cd "$repo_root"

section() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }

if [[ ${#fast[@]} -eq 0 ]]; then
    section "actionlint (GitHub Actions workflows)"
    if command -v actionlint >/dev/null 2>&1; then
        actionlint -color
    else
        echo "actionlint not installed - skipping (see https://github.com/rhysd/actionlint#installation)"
    fi

    section "yamllint (YAML files)"
    if command -v yamllint >/dev/null 2>&1; then
        yamllint -c .yamllint.yml .
    else
        echo "yamllint not installed - skipping (pip install yamllint)"
    fi

    section "markdownlint (Markdown files)"
    if command -v npx >/dev/null 2>&1; then
        npx --yes markdownlint-cli2 "**/*.md" "#target" "#bin" "#dist"
    else
        echo "npx not installed - skipping (npm install -g markdownlint-cli2)"
    fi
fi

section "LLVM toolchain (end-to-end tests)"
scripts/ci/llvm.sh

section "mvn clean verify (build + tests)"
mvn --batch-mode --no-transfer-progress clean verify

section "smoke test (runnable jar)"
jar=(target/z-compiler-*-all.jar)
scripts/ci/smoke-test.sh "${jar[0]}"

section "all checks passed"
