# ADR 0015: A resident service, scan levels and a settings file per workspace

Status: Accepted, implemented in 0.1.0, except that no MCP query tool takes an argument that narrows one query to a module or a path (decision 5) · Date: 2026-10-10 · Decision 2 amended by [ADR 0017](0017-row-order-dropped.md) · Decision 3, the item "exact needs the user to start the scan", superseded by [ADR 0018](0018-stored-trust-for-exact.md), so the service runs the build of a workspace that the user trusted

## In short

- KrosScope becomes installable as a background service of the user. It keeps chosen workspaces indexed and in
  memory, and the `kscope` client and the MCP tools answer through it.
- The work that every query repeats is removed: three items of a latency
  review of the query path.
- One settings file per workspace says what is scanned and how deep. Depth is one of three named levels, and
  every result names the level that produced it.
- There is no dashboard for now. The service shows a status page that cannot change anything.
- Through MCP an agent can read the settings and narrow one query. It cannot change a setting.
- This work is required and comes before the other items of the [roadmap](../roadmap.md).

## Problem

A query should return as fast as the index allows. Today it does not, for reasons that have nothing to do with the
size of the answer:

- Every `kscope` call starts a process, then loads and decodes the index. A [server](../cli.md#serve) avoids
  that, but it is started by hand for one workspace, and it is gone after a restart of the machine.
- Several costs repeat on every query even inside a server: see Evidence.
- A scan has one depth. The only choice is `--trust-build` or not, so a user cannot index less, or more
  shallowly, to get answers sooner.
- Settings are spread over command-line flags and environment variables such as `KSCOPE_WORKERS` and
  `KSCOPE_WORKER_HEAP`, so they are neither visible in a result nor shared with a team.

## Options considered

1. **Keep `kscope serve` as it is.** Nothing to install, but every new session pays the cold cost unless the
   user remembers to start a server for each workspace.
2. **A service of the user, one settings file per workspace and a status page.** Chosen.
3. **A dashboard or desktop application that configures and controls scans.** It is a second product to build,
   secure and maintain, and it does not make one query faster. Not now.
4. **Many independent switches**, for example weights of the ranking or single rules. Results would no longer be
   comparable between two users or reproducible from a report. Rejected in favour of a few named choices.
5. **Settings that an agent can change through MCP.** Rejected: an agent can be steered by text in the
   repository that it reads.

## Evidence

Queries with and without a server, from the [performance page](../benchmarks/performance.md#queries-through-a-server):

| Workspace | Command | One-off command | Through a server |
|---|---|---:|---:|
| Clone of Now in Android, 715 files | `analyze` | 2.8 s | 1.6 s |
| | `context` at 8,000 tokens | 2.7 s | 1.2 s |
| Private codebase, about twelve times larger | `analyze` | 21.1 s | 5.3 s |
| | `context` at 8,000 tokens | 15.2 s | 8.3 s |

About 0.6 s of every figure in the last column is the start of the `kscope` process that asks.

What still repeats on every query, from the latency review:

- `analyze` compiles a context package four times
  to print token estimates.
- Every read of the index encodes every row as JSON to sort it.
- Token counting is most of what is left of a query
  on the large codebase.
- The client process starts for every call.

The planned state has not been measured. No figure on this page is a claim about it.

## Decision

### 1. A service of the user

- `kscope service install`, `start`, `stop`, `status` and `uninstall`. Installing needs no administrator rights
  and registers one entry that starts the service with the user's session; uninstalling removes it and leaves
  nothing behind.
- `kscope service add` and `remove` choose the workspaces. They are written to the file of the user, `service.toml`
  in the directory of the service.
- The service keeps the registered workspaces indexed and their graphs in memory, and watches them with the
  existing [watcher](../cli.md#watching).
- An idle limit and a memory ceiling bound it: the workspace that was used least recently is released first. A
  release takes the graph out of memory while the server and the watcher stay, and the next request loads it again.
- It keeps the [safety of the server](../cli.md#safety-of-the-server): loopback only, a token for every request,
  and it never runs a build by itself.
- The `kscope` client and `kscope mcp` find the service the way they find a server today, and work without one
  as they do now.

### 2. No repeated work on the query path

- `analyze` no longer compiles a context package for each estimate. The estimate is the sum of the measures of
  the blocks of the package: the task, one block per fragment and the line for unresolved links. It is a sum
  where every block has a safe token boundary and the estimator can add blocks up. Otherwise the text is counted
  whole, as today.
- Rows are stored and read in their canonical order, without encoding them again to sort.
- The measure of a block that comes from a file is stored, keyed by the content of the file, the range, the
  rendering and the estimator.

The output of every command stays byte for byte the same, and a test proves it for each of the three changes.

### 3. One settings file per workspace

A file named `kscope.toml` in the workspace root, which can be committed and shared, holds every setting of a
workspace. The settings of a scan live in this file, and an environment variable that exists today still overrides
its value on one machine. What belongs to the machine and not to a workspace is kept apart,
in a file of the user that only the service reads: the registered workspaces and the limits of the service.

| Setting | Choices |
|---|---|
| What to scan | Paths to include and to exclude; tests on or off; generated directories |
| How deep | One of three levels, below |
| Analyzers | On or off per kind: Android resources, AIDL, schemas, native bindings, Git history |
| Resources | Analyzer workers and their memory |
| Freshness | Watching on or off, and its quiet period |

The three levels:

| Level | What it does | Cost |
|---|---|---|
| `syntax` | Files, build model and declarations, read from syntax alone | Seconds |
| `resolved` | References resolved by the compiler inside the workspace. Today's default | Today's scan |
| `exact` | The build model and classpaths from Gradle. Today's [`--trust-build`](../cli.md#--trust-build) | Runs the build |

- A setting that changes facts is part of what the index is keyed by, `kscope info` shows it, and every result
  names the level that produced it.
- The service answers from the level it has. By itself it deepens only from `syntax` to `resolved`, which runs
  nothing from the workspace.
- `exact` is never reached automatically, because it runs the build. The file can ask for it, but the build runs
  only when the user starts that scan, in a workspace that the user has trusted on this machine. A file from a
  repository can never grant that trust. The service keeps such an index as the watcher does today: it leaves
  it alone rather than rebuild it.
- Not configurable: the weights of the ranking, the evidence rules and anything else that would change what a
  result means without showing it.

### 4. A status page, not a dashboard

The service serves one page on the loopback address: the registered workspaces, the level and age of each index,
the last scan and the memory in use. It changes nothing. Settings are changed in the file.

### 5. Read-only through MCP

- The `info` tool reports the settings and the level of the index.
- A query tool accepts arguments that narrow that one query, for example to a module or a path.
- No tool changes a setting, widens what is scanned, raises a limit or trusts a build. A request that would need
  one returns the command for the user to run.

## Consequences

- **Memory.** A warm workspace holds its graph in memory. The amount per workspace has to be measured and
  published before the ceiling gets a default.
- **An entry in the operating system.** The service registers itself to start with the session. `SECURITY.md`
  has to describe it, and `uninstall` has to be tested on every supported system.
- **Claims per level.** The [support matrix](../support-matrix.md) states precision and recall for `resolved` and
  `exact` today. The `syntax` level finds fewer relationships and gets its own column; nothing is claimed for it
  until the conformance run measures it.
- **A new field in results.** The level is added to the JSON results as an additional field.
- **Order of work.** The service first, then the query path, then the settings file with scope and resources.
  The levels come last, because the `syntax` level and the deepening to `resolved` in the background need the
  analyzer changes that the latency review describes.
- **How it is judged.** By the timings of the performance page, taken again on both workspaces before and after
  each step, and by the regression gates of the latency review. Until then no speed is claimed.

## Reversal cost

Low for the service and the query path. The service is an addition that the client works without, and the three
changes of the query path keep the output identical, so each can be reverted on its own.

Higher for the settings file and the levels once they are released. The name and keys of the file, the names of
the levels and the field in the JSON results become a contract with users and with programs that read results.
Withdrawing them later needs a migration and a new schema version, so they are released only after the
conformance run has measured each level.
