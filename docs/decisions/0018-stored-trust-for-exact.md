# ADR 0018: Stored trust per workspace for the exact level

Status: Accepted · Date: 2026-10-19 · Supersedes one item of decision 3 of [ADR 0015](0015-resident-service-and-settings.md)

## In short

- ADR 0015 says that `exact` runs the build only when the user starts that scan. That item is withdrawn.
- The user trusts a workspace once, with `kscope trust`. The trust is stored for the canonical path of that
  workspace in a file of the user on this machine, `trust.toml` beside `service.toml`. `kscope trust --revoke`
  removes it, and `kscope info` shows whether the workspace is trusted.
- A workspace never grants trust to itself: `kscope.toml` can ask for `level = "exact"`, and the scan runs at
  `resolved` with a notice until the user trusts the workspace.
- A scan with `--level exact` (or `--trust-build`, its alias) is still the request of the user who types it and runs
  without stored trust, for that one scan.
- A plain scan keeps the level of the index and never goes shallower. Only an explicit lower `--level` with
  `--rebuild` does.

## Problem

ADR 0015 lets the settings file ask for `exact` but allows the build to run only in the scan that the user starts, in
a workspace the user has trusted. It does not say where that trust lives or how a later scan, an update by the
watcher or the resident service may use it. Without it, an `exact` index can only be kept up to date by typing the
option again, and the service has to leave such an index alone. Worse, a plain `kscope scan` after `--trust-build`
silently drops the exact model today, because the Gradle tooling unit is no longer wanted.

Running the build executes code of the workspace with the permissions of the user. A repository that is cloned and
scanned must not be able to start that, by a settings file or by anything else it contains.

## Options considered

1. **Trust is the option of the scan that the user types.** What ADR 0015 says. Nothing is stored and nothing can be
   forged, but no scan without the option can keep an `exact` index, and the service never may.
2. **Trust is a key of `kscope.toml`.** Simple and shareable. Rejected: the file comes with the repository, so the
   repository would grant itself the execution of its own build.
3. **Trust is stored per workspace on this machine, outside the workspace.** Chosen. The user decides once, and
   only the user can: the record is a file of the user that no scan, query or MCP tool writes.
4. **Trust is a setting of the service in `service.toml`.** One file for the machine. Rejected for now: that file is
   written only by the service commands and has a strict grammar, and trust has to work without a service.

## Evidence

- `exact` is the build-model flag of the engine: the provider that runs Gradle is eligible only then, and nothing
  else reads it. The level an index has is already stored: it is the producer of the persisted build model, which
  the watcher reads today to leave such an index alone.
- A plain scan after an `exact` scan removes the exact build model unit today. The test of the watcher stages that
  case and expects the drop.
- The settings file is read as data, limited in size and keys, and it is the file that a repository controls, which
  is why trust cannot be kept in it.

## Decision

- **Where.** `trust.toml` in the directory of the service (`~/.kscope/service`, or the directory named by
  `KSCOPE_SERVICE_HOME`), with one key, `trusted-workspaces`, a list of canonical workspace paths. The key is the
  real path of the workspace with `/` as separator, so a link or a path with `..` reaches the same record. A file
  that cannot be read trusts nothing, and a command that would change it refuses and leaves it as it is.
- **Surface.** `kscope trust` trusts the workspace and `kscope trust --revoke` stops. `info` shows the level asked
  for, where it comes from, and whether the workspace is trusted. There is no list command and no prompt on a scan.
- **Effective level of a scan.**
  - A level given on the command line is followed. Deeper than the index, or equal, it needs nothing. Shallower, it
    needs `--rebuild`, otherwise the scan exits with 1 before touching the index.
  - Without one, the level is the larger of the level of the index and the level of `kscope.toml` (`resolved` when
    the file says nothing). A file that asks for `exact` in a workspace that is not trusted gives `resolved` and the
    notice `level.exact-needs-trust`.
  - A plain `kscope scan` does not keep an `exact` index in a workspace that is not trusted, because that would run
    the build again. The scan exits with 1 before touching the index and names `kscope scan --level exact`,
    `kscope trust` and `kscope scan --level resolved --rebuild`.
- **The service and the watcher.** They deepen an index from `syntax` to `resolved` by themselves, and to `exact`
  only in a workspace with stored trust, when `kscope.toml` asks for it. The trust is read again at every scan they
  start. They update an `exact` index in a trusted workspace; when the trust is revoked they leave that index alone
  and never run the build. The `scan` tool of MCP still scans at level `resolved` explicitly and never runs the build.
- `syntax` is not part of this decision.

## Consequences

- **CLI contract.** A new option `--level`, a new command `trust`, a new field `indexLevel` in JSON results, and a refusal
  (exit 1) where a plain scan used to drop the exact model without a word. Scripts that alternate `--trust-build`
  and a plain scan stop at the plain scan until the workspace is trusted.
- **A new file of the user.** `SECURITY.md` describes it. Its only effect is to allow what the user would otherwise
  type as `--level exact`, in a workspace the user named.
- **Trust follows the path.** Moving or renaming a workspace needs a new `kscope trust`. A different checkout at the
  same path inherits the trust, as it would inherit any other setting of that directory.
- **Shared by tools.** Every tool that uses the same home reads the same file, which is intended.

## Reversal cost

Low. Nothing but the new file depends on it, and the file is read in one place. Removing the command returns to
option 1: every `exact` scan is typed, and an `exact` index is left alone. The names `--level` and `trust` and the
`level` field are a contract once released and follow ADR 0015.
