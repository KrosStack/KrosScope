#!/usr/bin/env bash
# Checks a release version and writes it, its tag and whether it is a prerelease as step outputs.
#
#     release-version.sh <version> [--tagged]
#
# The version follows Semantic Versioning 2.0.0, with or without a leading "v". It must equal the version in
# gradle.properties and have a section in CHANGELOG.md, so a release is prepared in a pull request and a tag
# only marks it. Without --tagged the tag must not exist yet; with --tagged it must exist and its commit must
# be on main.
set -euo pipefail

input=${1:?usage: release-version.sh <version> [--tagged]}
mode=${2:-}
version=${input#v}
tag=v$version

fail() {
    echo "::error::$1" >&2
    exit 1
}

number='(0|[1-9][0-9]*)'
identifier='(0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)'
semver="^$number\\.$number\\.$number(-$identifier(\\.$identifier)*)?(\\+[0-9A-Za-z-]+(\\.[0-9A-Za-z-]+)*)?\$"
[[ $version =~ $semver ]] ||
    fail "\"$input\" is not a Semantic Versioning 2.0.0 version, such as 1.2.3 or 1.2.3-rc.1."

declared=$(sed -n 's/^version=//p' gradle.properties | tr -d '\r')
[ "$declared" = "$version" ] ||
    fail "gradle.properties declares version $declared, not $version. Change it in a pull request first."

awk -v heading="## $version" '$0 == heading || index($0, heading " ") == 1 { found = 1 } END { exit !found }' \
    CHANGELOG.md || fail "CHANGELOG.md has no section \"## $version\". Add it in a pull request first."

if [ "$mode" = --tagged ]; then
    commit=$(git rev-parse -q --verify "refs/tags/$tag^{commit}") || fail "The tag $tag does not exist."
    git merge-base --is-ancestor "$commit" origin/main || fail "The commit of $tag is not on main."
else
    if git rev-parse -q --verify "refs/tags/$tag" > /dev/null || [ -n "$(git ls-remote --tags origin "refs/tags/$tag")" ]; then
        fail "The tag $tag exists already."
    fi
fi

core=${version%%+*}
prerelease=false
[[ $core == *-* ]] && prerelease=true
outputs="version=$version
tag=$tag
prerelease=$prerelease"
if [ -n "${GITHUB_OUTPUT:-}" ]; then
    echo "$outputs" >> "$GITHUB_OUTPUT"
fi
echo "$outputs"
