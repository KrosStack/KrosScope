#!/usr/bin/env bash
set -euo pipefail

archive=${1:?usage: check-components-archive.sh <zip>}
entries=$(unzip -Z1 "$archive")

fail() {
    echo "::error::$1" >&2
    exit 1
}

require_entry() {
    grep -Fxq "$1" <<< "$entries" || fail "Missing runtime archive entry: $1"
}

safe_entry() {
    local name=${1%/} segment
    local lowered=${name,,}
    [[ -n $name && $lowered != kscope-component.json && $lowered != kscope-staging.json ]] || return 1
    [[ $name != */ && $name != /* ]] || return 1
    IFS=/ read -r -a segments <<< "$name"
    for segment in "${segments[@]}"; do
        [[ $segment =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ && $segment != *. ]] || return 1
        lowered=${segment%%.*}
        [[ ! ${lowered,,} =~ ^(con|prn|aux|nul|com[0-9]|lpt[0-9])$ ]] || return 1
    done
}

duplicates=$(sort <<< "$entries" | uniq -d)
[[ -z $duplicates ]] || fail "Duplicate runtime archive entries: $duplicates"

while IFS= read -r entry; do
    safe_entry "$entry" || fail "Unsafe runtime archive entry: $entry"
    case "$entry" in
        lib/|native/|licenses/|LICENSE|NOTICE|THIRD-PARTY-LICENSES.md) ;;
        lib/*.jar|licenses/*.txt) ;;
        native/windows-x64/|native/linux-x64/|native/linux-aarch64/|native/macos-arm64/) ;;
        native/windows-x64/*.dll|native/linux-x64/*.so|native/linux-aarch64/*.so|native/macos-arm64/*.dylib) ;;
        *) fail "Unexpected runtime archive entry (no models or launch scripts allowed): $entry" ;;
    esac
done <<< "$entries"

for jar in semantic-worker model spi kotlin-stdlib kotlinx-serialization-core-jvm kotlinx-serialization-json-jvm onnxruntime annotations; do
    grep -Eq "^lib/$jar-[0-9][^/]*\.jar$" <<< "$entries" || fail "Missing runtime jar: $jar"
done

for platform in windows-x64 linux-x64 linux-aarch64 macos-arm64; do
    case "$platform" in
        windows-x64) libraries=(onnxruntime.dll onnxruntime4j_jni.dll onnxruntime_providers_shared.dll) ;;
        linux-*) libraries=(libonnxruntime.so libonnxruntime4j_jni.so) ;;
        macos-arm64) libraries=(libonnxruntime.dylib libonnxruntime4j_jni.dylib) ;;
    esac
    for library in "${libraries[@]}"; do
        require_entry "native/$platform/$library"
    done
done

for licence in LICENSE NOTICE THIRD-PARTY-LICENSES.md; do
    require_entry "$licence"
done
repository=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
for licence in "$repository"/licenses/*.txt; do
    require_entry "licenses/${licence##*/}"
done

echo "Runtime archive layout verified: $archive"
