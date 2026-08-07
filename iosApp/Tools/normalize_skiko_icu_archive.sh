#!/bin/sh

# NTsocial MeshLink original work and modifications:
# Copyright (c) 2026 LiberaNt LLC
#
# Developed and/or modified for NTsocial MeshLink in 2026.
#
# SPDX-License-Identifier: GPL-3.0-or-later

set -eu

framework_binary=${1:?"framework binary path is required"}
deployment_target=${2:?"iOS deployment target is required"}
platform_name=${3:?"Apple platform name is required"}

case "$platform_name" in
    iphonesimulator)
        clang_target="arm64-apple-ios${deployment_target}-simulator"
        ;;
    iphoneos)
        clang_target="arm64-apple-ios${deployment_target}"
        ;;
    *)
        exit 0
        ;;
esac

if [ ! -f "$framework_binary" ]; then
    echo "error: MeshLinkKit binary not found: $framework_binary" >&2
    exit 1
fi

member_name=libicu.icudtl_dat.o
member_count=$(xcrun ar -t "$framework_binary" | awk -v member="$member_name" '$0 == member { count += 1 } END { print count + 0 }')
if [ "$member_count" -eq 0 ]; then
    exit 0
fi

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/ntsocial-skiko-icu.XXXXXX")
cleanup() {
    case "$temporary_directory" in
        "${TMPDIR:-/tmp}"/ntsocial-skiko-icu.*)
            /bin/rm -rf -- "$temporary_directory"
            ;;
    esac
}
trap cleanup EXIT HUP INT TERM

original_object="$temporary_directory/$member_name"
patched_directory="$temporary_directory/patched"
patched_object="$patched_directory/$member_name"
patched_archive="$temporary_directory/MeshLinkKit"

mkdir "$patched_directory"
xcrun ar -p "$framework_binary" "$member_name" > "$original_object"
object_minimum=$(xcrun vtool -show-build "$original_object" | awk '$1 == "minos" { print $2; exit }')

version_is_greater() {
    awk -v candidate="$1" -v baseline="$2" 'BEGIN {
        candidate_count = split(candidate, candidate_parts, ".")
        baseline_count = split(baseline, baseline_parts, ".")
        part_count = candidate_count > baseline_count ? candidate_count : baseline_count
        for (part_index = 1; part_index <= part_count; part_index += 1) {
            candidate_part = part_index <= candidate_count ? candidate_parts[part_index] + 0 : 0
            baseline_part = part_index <= baseline_count ? baseline_parts[part_index] + 0 : 0
            if (candidate_part > baseline_part) exit 0
            if (candidate_part < baseline_part) exit 1
        }
        exit 1
    }'
}

if [ -z "$object_minimum" ] || ! version_is_greater "$object_minimum" "$deployment_target"; then
    exit 0
fi

# Skiko 0.144.x labels this one object with the build machine's OS. It contains no executable code: one ICU data
# symbol backed only by __TEXT,__const. Relink that data-only member at the App's deployment target, preserving the
# section bytes and symbol while giving the final App linker accurate platform metadata.
code_size=$(size -m "$original_object" | awk '/Section \(__TEXT, __text\):/ { print $NF; exit }')
constant_size=$(size -m "$original_object" | awk '/Section \(__TEXT, __const\):/ { print $NF; exit }')
data_symbol_count=$(nm -g "$original_object" | awk '$NF ~ /^_icudt.*_dat$/ { count += 1 } END { print count + 0 }')
if [ "$code_size" != "0" ] || [ -z "$constant_size" ] || [ "$constant_size" -le 0 ] || [ "$data_symbol_count" -ne 1 ]; then
    echo "error: refusing to normalize unexpected Skiko ICU object layout" >&2
    exit 1
fi

xcrun --sdk "$platform_name" clang \
    -target "$clang_target" \
    -r \
    -nostdlib \
    -Wl,-w \
    -o "$patched_object" \
    "$original_object"
patched_constant_size=$(size -m "$patched_object" | awk '/Section \(__TEXT, __const\):/ { print $NF; exit }')
patched_symbol_count=$(nm -g "$patched_object" | awk '$NF ~ /^_icudt.*_dat$/ { count += 1 } END { print count + 0 }')
patched_minimum=$(xcrun vtool -show-build "$patched_object" | awk '$1 == "minos" { print $2; exit }')
if [ "$patched_constant_size" != "$constant_size" ] || \
    [ "$patched_symbol_count" -ne 1 ] || \
    [ "$patched_minimum" != "$deployment_target" ]; then
    echo "error: normalized Skiko ICU object failed integrity checks" >&2
    exit 1
fi

cp "$framework_binary" "$patched_archive"
remaining_members=$member_count
while [ "$remaining_members" -gt 0 ]; do
    xcrun ar -d "$patched_archive" "$member_name"
    remaining_members=$((remaining_members - 1))
done
xcrun ar -r "$patched_archive" "$patched_object"
xcrun ranlib "$patched_archive"
mv "$patched_archive" "$framework_binary"

echo "Normalized data-only Skiko ICU deployment metadata ($object_minimum -> $deployment_target)."
