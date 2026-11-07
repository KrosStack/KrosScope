# ADR 0022: Optional components from a pinned manifest

Status: Proposed · Date: 2026-11-06

## Problem

Optional models and inference runtimes can be large and platform dependent. The default distribution must remain
usable offline, with no learned signals required. Users need an explicit, inspectable way to download optional
files, to bring them from an offline source, and to remove them without deleting an unrelated directory.

## Options considered

1. **Vendor every component into the distribution.** Simple installation, but every user pays the download and disk
   cost, including users who never enable learned signals.
2. **Use a package manager.** Delegates downloads and upgrades, but adds a prerequisite and different packaging and
   version policies on each operating system.
3. **Ship a manifest with pinned checksums and install into a user directory.** Chosen. The release determines the
   versions and source URLs; the user explicitly approves installation and can supply the same files offline.

## Evidence

- [ADR 0019](0019-installers-and-agent-skill.md) establishes checksum verification before installation and replacement
  only of marked directories. Optional components copy that discipline.
- The first component's individual file sizes and total installed size are **to be measured**. No component is
  shipped by this change: the release manifest has an empty `components` list.
- Tests construct their own manifests, files and archives, including an HTTP server on loopback, and exercise
  checksum and size failures, unsafe archive entries, replacement, listing and offline installation.

## Decision

- A strict, schema-versioned `components.json` resource in the CLI pins each component's name, version, licence,
  licence file, description and files. Every file has a relative path, platform, ordered HTTPS URLs (mirror before
  origin), byte size and SHA-256. Unknown manifest keys are rejected. Platforms are `any`, `windows-x64`, `linux-x64`,
  `linux-aarch64`, `macos-arm64` and `macos-x64`; files for other platforms are skipped.
- `kscope components install NAME [--yes] [--from DIR|FILE]` prints the name, version, licence, total download size
  and URLs before asking `Download? [y/N]`. Without terminal input it requires `--yes`. Only manifest URLs are
  downloaded, using `HttpClient` with redirects; HTTPS redirects cannot downgrade to HTTP. No installation occurs
  implicitly during analysis.
- Downloads stream into `<name>/<version>.installing-<random>` beside the target, with a running checksum and a
  hard bound on bytes. A wrong size or checksum discards staging. ZIP archives are unpacked within staging; parent
  traversal, absolute paths, drive letters and output collisions are refused. Entries are checked for escapes
  and written as plain files, so an archive can never create a link. Expanded bytes are limited
  to eight times the pinned archive size, and each archive is removed after extraction.
- `--from DIR` supplies the manifest files at their relative paths without network access. `--from FILE` supplies
  one ZIP archive when the platform's file list contains exactly that one archive. All checks remain in force.
- Components live in `~/.kscope/components/<name>/<version>/`, overridden by `KSCOPE_COMPONENTS_HOME` or the
  `kscope.components.home` system property, with the property taking precedence as in `MachineDirectory`.
  `kscope-component.json` records the name, version and manifest file paths. The completed staging directory is
  moved atomically into place. Replacement first preserves the marked old directory beside it and restores it if
  the final move fails. Removal and replacement refuse unmarked directories and do not follow directory links.
- `components list` reports manifest entries with version, licence, platform download size and state: installed,
  not installed, disabled by workspace, other version installed, or unsupported platform. `components status`
  reports marked installations, including components absent from this release, their directories and manifest
  version matches. Both support `--format json`.
- `[components]` in `kscope.toml` maps component names to booleans. `NAME = false` disables use in that workspace;
  it does not uninstall machine files. Values must be booleans; unknown tables, nested keys and invalid names are
  rejected by the settings reader's existing validation path.
- Inference runtimes run out of process. Their output is derived data stored beside the index and never a fact.
  `--no-ml` returns the ranking without learned signals. Runtime launch, workers and interactive setup are later
  changes.
- This decision amends the “Network” consequence and the “one network action” wording of accepted
  [ADR 0020](0020-links-through-built-artifacts.md): explicitly approved component installation is the second
  network action after `workspace add URL`. The accepted ADR is left unchanged.

## Consequences

The default distribution and all analysis remain useful offline. Download approval is separate from workspace
settings and build trust. Release maintainers must review licences, URLs, hashes and sizes before adding a component.
The marker proves ownership for deletion, not authenticity of an already installed tree; local edits are not
reverified by status. Staging and replacement require space for both old and new installations and an atomic move
on the component filesystem.

## Reversal cost

Low before any component ships: remove the command group, manifest and settings field. Once components are installed,
retain a marker-aware removal path or document manual removal. Out-of-process runtimes and derived data keep the
fact model and index schema independent of this mechanism.
