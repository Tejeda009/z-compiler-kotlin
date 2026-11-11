#!/usr/bin/env bash
# Usage:
#   scripts/ci/ensure-labels.sh            # needs gh + GH_TOKEN (runs in CI)
#
set -euo pipefail

labels=(
    # Path labels applied by .github/workflows/labeler.yml
    "parser|1d76db|Changes to the parser"
    "lexer|fbca04|Changes to the lexer"
    "ast|d4c5f9|Changes to the AST"
    "codegen|0e8a16|Changes to the LLVM code generator"
    "main|5319e7|Changes to the CLI / entry point"
    "tests|bfd4f2|Changes to the test suite"
    "benchmarks|fef2c0|Changes to the benchmarks"
    "docs|0075ca|Documentation"
    "ci-cd|e4e669|CI/CD, workflows and build scripts"
    "build|c5def5|Maven build (pom.xml) and packaging"
    # Labels used by .github/workflows/stale.yml
    "no-issue-activity|ededed|Marked by the stale workflow: issue without activity"
    "no-pr-activity|ededed|Marked by the stale workflow: PR without activity"
    "issue-inactive|cccccc|Closed by the stale workflow: issue without activity"
    "pr-inactive|cccccc|Closed by the stale workflow: PR without activity"
    "pinned|0e8a16|Exempt from the stale workflow"
    "security|b60205|Security related"
    "blocked|eeeeee|Waiting on something else"
    "work-in-progress|fbca04|Author is still working on it"
    # Labels used by the issue templates and by Dependabot
    "bug|d73a4a|Something is not working"
    "enhancement|a2eeef|New feature or request"
    "documentation|0075ca|Docs only"
    "dependencies|0366d6|Dependency updates"
    "good first issue|7057ff|Good for newcomers"
)

if ! command -v gh >/dev/null 2>&1; then
    echo "GitHub CLI (gh) not found - skipping label creation." >&2
    exit 0
fi

if [[ -z "${GH_TOKEN:-}${GITHUB_TOKEN:-}" ]]; then
    if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
        echo "::error::GH_TOKEN is not set; labels cannot be created." >&2
        exit 1
    fi
    echo "No GH_TOKEN in the environment - skipping label creation." >&2
    exit 0
fi

export GH_TOKEN="${GH_TOKEN:-$GITHUB_TOKEN}"
repo="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"

list_labels() {
    gh label list --repo "$repo" --limit 1000 --json name --jq '.[].name'
}

existing="$(list_labels)"

created=0
raced=0
for spec in "${labels[@]}"; do
    IFS='|' read -r name color description <<<"$spec"
    if grep -qiFx "$name" <<<"$existing"; then
        continue
    fi

    if error="$(gh label create "$name" --repo "$repo" --color "$color" --description "$description" 2>&1)"; then
        created=$((created + 1))
        continue
    fi

    if grep -qiFx "$name" <<<"$(list_labels)"; then
        echo "label '$name' was created by a concurrent run"
        raced=$((raced + 1))
        continue
    fi

    echo "::error::cannot create label '$name': $error" >&2
    exit 1
done

echo "Labels: ${#labels[@]} configured, $created created, $raced created concurrently"
