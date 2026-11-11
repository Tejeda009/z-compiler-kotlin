#!/usr/bin/env bash

set -euo pipefail

tools=(clang llc opt llvm-as lli)

install=0
if [[ "${1:-}" == "--install" ]]; then
    install=1
fi

missing_tools() {
    local tool
    for tool in "${tools[@]}"; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            printf '%s ' "$tool"
        fi
    done
}

have_all_tools() {
    [[ -z "$(missing_tools)" ]]
}

use_installed_llvm() {
    local directory
    for directory in /usr/lib/llvm-*/bin; do
        [[ -d "$directory" ]] || continue
        case ":$PATH:" in
            *":$directory:"*) continue ;;
        esac
        echo "using the LLVM toolchain in $directory"
        export PATH="$directory:$PATH"
        if [[ -n "${GITHUB_PATH:-}" ]]; then
            echo "$directory" >> "$GITHUB_PATH"
        fi
    done
}

if ! have_all_tools; then
    use_installed_llvm
fi

if ! have_all_tools && [[ "$install" -eq 1 ]]; then
    echo "installing the LLVM toolchain (clang, llvm)..."
    if [[ "$(id -u)" -eq 0 ]]; then
        apt-get update
        apt-get install -y --no-install-recommends clang llvm
    else
        sudo apt-get update
        sudo apt-get install -y --no-install-recommends clang llvm
    fi
    use_installed_llvm
fi

if ! have_all_tools; then
    for tool in "${tools[@]}"; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            echo "::error::LLVM tool '$tool' not found; the end-to-end tests would silently skip it."
        fi
    done
    echo "Install them with: sudo apt-get update && sudo apt-get install -y clang llvm" >&2
    exit 1
fi

for tool in "${tools[@]}"; do
    echo "found: $tool -> $(command -v "$tool")"
done
clang --version 2>/dev/null | sed -n '1p'
