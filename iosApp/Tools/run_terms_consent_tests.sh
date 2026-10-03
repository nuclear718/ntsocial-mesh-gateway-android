#!/usr/bin/env bash
# Copyright (c) 2026 LiberaNt LLC. SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail

host_dir="$(cd "$(dirname "$0")/.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT

xcrun swiftc \
    -parse-as-library -warnings-as-errors \
    "$host_dir/NTsocialMeshLink/TermsConsentStore.swift" \
    "$host_dir/Tests/TermsConsentTests.swift" \
    -o "$test_dir/TermsConsentTests"
"$test_dir/TermsConsentTests"
