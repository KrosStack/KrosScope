#!/bin/sh
# Installs the kscope command-line tool from a KrosScope release on macOS or Linux:
#
#     curl -fsSL https://raw.githubusercontent.com/KrosStack/KrosScope/main/install.sh | sh
#
# It downloads the release archive, checks it against the release's SHA-256 sums, unpacks it into your home
# directory and links the kscope command into a directory on your PATH. With JDK 17 or newer present, it also runs
# `kscope skill install`, which teaches the coding agents it finds to use kscope. Running it again replaces the
# installed version. kscope needs JDK 17 or newer; the installer warns when it finds none.
#
# Settings, all optional:
#   KSCOPE_VERSION          version to install, such as 0.1.0 (default: the latest release)
#   KSCOPE_INSTALL_DIR      where the tool is unpacked (default: ~/.local/share/kscope)
#   KSCOPE_BIN_DIR          where the kscope command is linked (default: ~/.local/bin)
#   KSCOPE_NO_MODIFY_PATH   set to 1 to leave your shell profile alone
#   KSCOPE_NO_AGENT_SKILL   set to 1 to skip installing the skill for coding agents
#   KSCOPE_DOWNLOAD_URL     where the release files are (default: the GitHub release of the version)
set -eu

repository=KrosStack/KrosScope
install_dir=${KSCOPE_INSTALL_DIR:-$HOME/.local/share/kscope}
bin_dir=${KSCOPE_BIN_DIR:-$HOME/.local/bin}

say() {
    printf '%s\n' "$*"
}

fail() {
    printf 'kscope install: %s\n' "$*" >&2
    exit 1
}

case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*)
        fail "on Windows, run this in PowerShell instead: irm https://raw.githubusercontent.com/$repository/main/install.ps1 | iex"
        ;;
esac

if command -v curl > /dev/null 2>&1; then
    fetch() { curl -fsSL "$1" -o "$2"; }
elif command -v wget > /dev/null 2>&1; then
    fetch() { wget -q "$1" -O "$2"; }
else
    fail "curl or wget is needed to download kscope"
fi
if command -v sha256sum > /dev/null 2>&1; then
    sha256() { sha256sum "$1" | awk '{ print $1 }'; }
elif command -v shasum > /dev/null 2>&1; then
    sha256() { shasum -a 256 "$1" | awk '{ print $1 }'; }
else
    fail "sha256sum or shasum is needed to check the download"
fi
command -v tar > /dev/null 2>&1 || fail "tar is needed to unpack kscope"

marker=.kscope-installation
if [ -e "$install_dir" ] && [ ! -f "$install_dir/$marker" ] && [ -n "$(ls -A "$install_dir" 2> /dev/null)" ]; then
    fail "$install_dir exists and was not made by this installer; set KSCOPE_INSTALL_DIR to another directory"
fi
if [ -e "$bin_dir/kscope" ] && [ ! -L "$bin_dir/kscope" ]; then
    fail "$bin_dir/kscope exists and is not a link to kscope; set KSCOPE_BIN_DIR to another directory"
fi

parent=$(dirname "$install_dir")
mkdir -p "$parent" "$bin_dir"
work=$(mktemp -d "$parent/.kscope-install.XXXXXX")
trap 'rm -rf "$work"' EXIT
trap 'exit 1' INT TERM

version=${KSCOPE_VERSION:-}
if [ -z "$version" ]; then
    fetch "https://api.github.com/repos/$repository/releases/latest" "$work/latest.json" ||
        fail "cannot find the latest release of $repository"
    version=$(sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$work/latest.json" | head -n 1)
    [ -n "$version" ] || fail "cannot read the latest release of $repository"
fi
version=${version#v}

base=${KSCOPE_DOWNLOAD_URL:-https://github.com/$repository/releases/download/v$version}
base=${base%/}
archive=kscope-$version.tar.gz
say "Downloading kscope $version"
fetch "$base/$archive" "$work/$archive" || fail "cannot download $base/$archive"
fetch "$base/SHA256SUMS" "$work/SHA256SUMS" || fail "cannot download $base/SHA256SUMS"

expected=$(awk -v name="$archive" '{ file = $2; sub(/^\*/, "", file) } file == name { print $1; exit }' "$work/SHA256SUMS")
actual=$(sha256 "$work/$archive")
[ -n "$expected" ] || fail "SHA256SUMS lists no sum for $archive"
[ "$expected" = "$actual" ] || fail "the SHA-256 sum of $archive does not match SHA256SUMS; nothing was installed"

tar -xzf "$work/$archive" -C "$work"
[ -x "$work/kscope-$version/bin/kscope" ] || fail "$archive does not contain kscope-$version/bin/kscope"

printf '%s\n' "$version" > "$work/kscope-$version/$marker"
rm -rf "$install_dir"
mv "$work/kscope-$version" "$install_dir"
ln -sf "$install_dir/bin/kscope" "$bin_dir/kscope"
say "Installed kscope $version in $install_dir"
say "Linked $bin_dir/kscope"

case ":$PATH:" in
    *":$bin_dir:"*) ;;
    *)
        line="export PATH=\"$bin_dir:\$PATH\""
        if [ "${KSCOPE_NO_MODIFY_PATH:-}" = 1 ]; then
            say "Add $bin_dir to your PATH to use kscope: $line"
        else
            case "${SHELL:-}" in
                */zsh) profile=$HOME/.zshrc ;;
                */bash) if [ "$(uname -s)" = Darwin ]; then profile=$HOME/.bash_profile; else profile=$HOME/.bashrc; fi ;;
                */fish) profile=$HOME/.config/fish/conf.d/kscope.fish; line="fish_add_path \"$bin_dir\"" ;;
                *) profile=$HOME/.profile ;;
            esac
            mkdir -p "$(dirname "$profile")"
            if ! grep -F -q -x "$line" "$profile" 2> /dev/null; then
                printf '\n# kscope\n%s\n' "$line" >> "$profile"
                say "Added $bin_dir to your PATH in $profile"
            fi
            say "Open a new terminal, or run this in the current one: $line"
        fi
        ;;
esac

java=java
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    java=$JAVA_HOME/bin/java
fi
major=$("$java" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\)\.\{0,1\}\([0-9]*\).*/\1 \2/p' | head -n 1) || major=
case "$major" in
    "1 "*) major=${major#1 } ;;
    *) major=${major%% *} ;;
esac
if [ -z "$major" ]; then
    say "Warning: kscope needs JDK 17 or newer, and no Java was found. Install one, for example from https://adoptium.net."
elif [ "$major" -lt 17 ]; then
    say "Warning: kscope needs JDK 17 or newer, and the Java found is version $major."
fi

if [ -n "$major" ] && [ "$major" -ge 17 ]; then
    if [ "${KSCOPE_NO_AGENT_SKILL:-}" != 1 ] && ! "$install_dir/bin/kscope" skill install; then
        say "Warning: the kscope skill for coding agents was not installed; run 'kscope skill install' to try again."
    fi
    say "Run 'kscope doctor' in a project to check the setup."
elif [ "${KSCOPE_NO_AGENT_SKILL:-}" != 1 ]; then
    say "Then run 'kscope skill install' to teach your coding agents to use kscope."
fi
