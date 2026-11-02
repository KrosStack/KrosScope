#!/usr/bin/env bash
# Prints the section of CHANGELOG.md for one version, without its heading, as the notes of its release.
#
#     release-notes.sh <version>
set -euo pipefail

version=${1:?usage: release-notes.sh <version>}

awk -v heading="## $version" '
    index($0, "## ") == 1 {
        if (inside) exit
        inside = $0 == heading || index($0, heading " ") == 1
        next
    }
    inside && (started || NF) { started = 1; print }
' CHANGELOG.md
