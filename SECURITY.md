# Security Policy

## In short

- **Local only:** KrosScope needs no network access, cloud service or language model. The only sockets it opens
  are loopback sockets, for `kscope serve` and `kscope service`.
- **Nothing runs by default:** at the default level, scanning an untrusted repository does not execute its code.
- **`--level exact` is the exception:** it (and `--trust-build`, its alias) runs the workspace's build scripts with
  the permissions of the current user, so use it only for workspaces you trust. A workspace can ask for it in
  `kscope.toml` but only your own `kscope trust`, stored outside the workspace, lets a scan that you did not type
  with the option run the build.
- **`kscope serve`:** binds `127.0.0.1` only and answers only requests that carry a random 256-bit token.
- **`kscope mcp`:** gives a coding agent the query commands and the plain incremental scan, not a shell, not
  `--level exact` and not `kscope trust`.
- **`kscope service`:** serves several workspaces in the background, on `127.0.0.1` only and with a token, and runs
  a build by itself only in a workspace that you trusted with `kscope trust` and that is, or asks to be, indexed at
  level `exact`. It also serves a read-only status page, on `127.0.0.1` only. `kscope service install`
  registers one entry that starts it with your session; the entry needs no administrator rights, and
  `kscope service uninstall` removes it.
- **Reporting:** use GitHub's private vulnerability reporting, not a public issue.

## Supported versions

Only the latest 0.1.x release receives security fixes. Builds of other branches and of earlier development
snapshots are not supported.

## Reporting vulnerabilities

Do not open a public issue or discussion for a security vulnerability. Use GitHub's private vulnerability
reporting, available on the repository's Security tab. The report reaches the maintainers confidentially.

## Scope

In scope:

- Code execution from a scanned workspace when neither `--level exact` (or `--trust-build`) was passed nor the user
  trusted the workspace with `kscope trust`.
- Writes outside the workspace index and the output locations the user named.
- Disclosure of workspace content beyond the local machine.

Out of scope:

- Resource exhaustion from very large workspaces. KrosScope is a local tool; resource limits are the
  user's responsibility.
- Effects of a build that the user explicitly requested with `--level exact` or `--trust-build`, or allowed with
  `kscope trust`.
- Vulnerabilities in dependencies that KrosScope does not misuse. Report those to their maintainers.

## Security-relevant behaviour

### Local operation

KrosScope analyses a workspace on the local machine.

- It needs no network access, cloud service or language model, and it opens no connection to another machine.
- Git history is read through the local `git` executable.
- The only sockets it opens are loopback sockets: the one of `kscope serve`, which the next section describes, and
  those of `kscope service`, which a later section describes. It opens them only when you start those commands.

### `kscope serve` listens on the loopback interface

`kscope serve` keeps the index of a workspace in memory and answers the query commands and `scan` for it. It is
started by hand, runs in the foreground, and stops with Ctrl+C, `kscope serve --stop` or its idle timeout.

- It binds the address `127.0.0.1` on a port the operating system picks, and nothing else. It cannot be reached from
  another machine, and no option changes that.
- It creates a random 256-bit token when it starts and answers only requests that carry it, comparing in constant
  time. A connection without the token is closed without an answer. The token is stored in `.kscope/serve.json`
  and is never printed or logged.
- `serve.json` is created readable by your account only, whatever the permissions of the workspace are: with the
  mode `rw-------` where the file system has POSIX permissions, and on Windows with an access control list that
  allows your account alone. On a file system that has neither, the server refuses to start rather than write a
  token that others could read.
- A holder of the token can run the commands that the server serves, in that workspace, with the permissions of the
  account that started the server: a `scan` with `--level exact`, which executes the build logic of the workspace
  like the same command typed in a shell, and `analyze` or `context` with `--task-file`, which reads any file that
  account can read. That is why the token must stay readable by your account only, and the server rejects
  everyone else.
- Another local account can open connections to the port but gets nothing without the token.
  - Requests are bounded in size, in time and in number.
  - Such connections cannot delay a request that the server accepted.
  - The most they can do is occupy the places for incoming requests, and then a query runs in its own process as
    if no server ran.
- The server runs no shell and no program that a request names, and writes only what the served commands write.
- With `--watch` the server also scans by itself when files changed. Such a scan runs at level `syntax` or `resolved`,
  and at level `exact` only in a workspace that you trusted with `kscope trust`.
  - At those two levels it executes nothing from the workspace.
  - At level `exact` it runs the build of the workspace like `kscope scan --level exact`. That happens only when
    `kscope.toml` asks for `level = "exact"` or the index was built at `exact`, and the trust is read again when the
    scan starts, after any wait for its turn, so a revoked trust stops it.
  - In a workspace that is not trusted, an index that was built at level `exact` is left alone rather than rebuilt.
  - It writes only inside the index directory.
  - It reads no file content to notice changes, only the list of files with their sizes and modification times.
  - It does not update while Git is in the middle of an operation.

### `kscope service` and the entry that starts it with your session

`kscope service` keeps several workspaces served by one process of yours, `start` runs it in the background, and
`install` registers it to start when you log in. Its server follows the rules of `kscope serve` above.

- It binds `127.0.0.1` only and answers only requests that carry its token. The token is in `service.json` in the
  service directory, which only your account can read: the file has the mode `rw-------` on Linux and macOS, and an
  access control list that allows your account alone on Windows. The service refuses to start where neither is
  possible.
- It serves a read-only status page on a second port of `127.0.0.1`, and nothing else:
  - the page is at an address that holds its own random 256-bit token, new at each start, and shown only by
    `kscope service page`; any other path is answered with 404;
  - only `GET` is accepted, so no request can change a setting or start a scan;
  - a `Host` header other than `127.0.0.1` or `localhost` with the port of the page is refused, which keeps a web page
    from reading it through a rebound name;
  - the page carries no script, form or external file, and its responses forbid caching and framing;
  - it shows the paths of the workspaces and the messages of their failures, so treat the address like the token.
- It runs a build by itself only in a workspace that you trusted with `kscope trust` and that is, or asks to be,
  indexed at level `exact`. Its automatic updates are the scans of `serve --watch` described above, and it reads the
  trust again before each build. Without trust it never runs a build.
- `install` needs no administrator rights and registers one entry for your user:
  - on Windows the value `KrosScope` of the key `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, imported with
    `reg.exe` from the Windows directory out of a registration file, `run-entry.reg` in the service directory, which
    is deleted afterwards;
  - on Linux the systemd user unit `kscope.service` in `$XDG_CONFIG_HOME/systemd/user`, enabled with
    `systemctl --user`;
  - on macOS the launch agent `io.github.krosstack.kscope.plist` in `~/Library/LaunchAgents`.
- `kscope service uninstall` removes that entry and the file `launch.args` that `install` wrote. It leaves
  `service.toml`, which is yours.
- `reg.exe` and `systemctl` run without a shell, with a timeout of 30 seconds. An `install` that fails leaves the
  earlier entry and `launch.args` as they were.

The entry starts the Java and the `kscope` that ran `install`, with the arguments in `launch.args`. Whoever can write
that file or replace those programs can make your session start another program, but only with your own rights. If
Java or `kscope` is removed or moved, `kscope service status` says so; nothing repairs the entry silently.

The three entries are tested with doubles. None has been registered on a real session yet.

### Default: nothing from the workspace is executed

Without `--trust-build`, the static build-model provider reads settings files, build-script outlines, version
catalogs, Gradle properties and directory conventions. It executes nothing. Values it cannot determine
statically are labelled `not-analysed`, never guessed. Kotlin and Java sources are parsed and resolved, not
run. Scanning an untrusted repository in this mode does not execute its code.

Files are listed with `git ls-files` inside a Git work tree. Git is run for this with `core.fsmonitor` turned off,
so that a file-system monitor hook named in the configuration of the repository is not started by the listing.

### The settings file is data

`kscope.toml` in the root of a workspace may come from a repository that you do not trust. It can only set limits, narrow what a scan reads and switch analyzers off.

- It is data. It is read as TOML only when it is a regular file, not a symbolic link, of at most 64 KiB, and nothing
  in it is executed, expanded or followed. Any key that this version does not know makes the file invalid.
- It cannot run a build or grant trust. `--level exact` stays an option of the command that you type, and the file
  can only ask for level `exact`: a scan runs the build for that only in a workspace that you trusted with
  `kscope trust`, a record that is kept outside the workspace.
- Its values are bounded: at most 256 patterns of at most 512 characters, 1 to 8 Kotlin workers and a worker heap of
  256 to 8,192 MiB. The analyzer switches can only turn a group of analyzers off; none of them turns on anything
  that a scan did not already do, and none reaches the build. The patterns only select among the files that a scan already lists; they cannot add a file,
  reach outside the workspace or follow a link. The environment variables
  `KSCOPE_WORKERS` and `KSCOPE_WORKER_HEAP` override the file on your machine.

### `kscope mcp` gives a coding agent tools, not a shell

`kscope mcp` is started by an agent host as a child process and speaks the Model Context Protocol on its standard
streams. Its tools run the query commands and the plain incremental scan, and nothing else.

- No tool passes an option that changes what runs. The `scan` tool takes no arguments, so an agent cannot ask for
  `--level exact` and cannot trust a workspace, and the other tools take a task, a name or a path, a token budget or a
  detail level. The `scan` tool always scans at level `resolved`, so it never executes the build logic of any
  workspace. A watcher, in a server, the service or `kscope mcp --watch`, can still deepen the index of a workspace that
  you trusted with `kscope trust` to `exact`; no tool can request that.
- The text an agent sends is passed to a command as one argument after `--`. It is never read as an option or as
  an argument file, properties that a tool does not define are rejected, and sizes are bounded.
- There is no tool that writes to the workspace, runs a program or reads a file by path. What a tool returns is
  what the same command prints for a person: content of the workspace that the agent could read anyway.
- When no server runs for the workspace, `kscope mcp` starts the loopback server described above inside its own
  process and stops it when its standard input closes; everything said about that server applies.

### `--level exact` and `--trust-build` execute the workspace's build logic

`--level exact`, which `--trust-build` is an alias of, reads the exact build model through the Gradle Tooling API. That
runs the workspace's settings and build scripts, included builds and `buildSrc`, with the permissions of the current
user. A build may do anything its scripts say, including network access.

It is off by default. Use it only for workspaces you trust. See
[ADR 0003](docs/decisions/0003-build-model-acquisition.md) and [the CLI reference](docs/cli.md).

### Trust is stored per workspace, on your machine

A scan without the option runs the build only in a workspace that you trusted with `kscope trust`. That is how a
`level = "exact"` in `kscope.toml`, or an index that was built at `exact`, can be scanned again without typing the
option. See [ADR 0018](docs/decisions/0018-stored-trust-for-exact.md).

- **Where it lives.** In `trust.toml`, a file of yours in the directory of the service (`~/.kscope/service`, or
  `KSCOPE_SERVICE_HOME`), as the canonical path of the workspace. It is never read from the workspace, and no scan,
  query, server, service or MCP tool writes it. Only `kscope trust` and `kscope trust --revoke` do.
- **A repository cannot grant it.** `kscope.toml` can ask for `level = "exact"`; without stored trust the scan runs at
  `resolved` with the warning `level.exact-needs-trust`. A plain scan of an `exact` index in a workspace that is not
  trusted is refused rather than run the build again.
- **It is keyed by the real path.** A link or a path with `..` reaches the same record, and a path that is not
  recorded is not trusted. Another checkout at a trusted path is trusted too; revoke the trust when you delete a
  workspace you trusted.
- **It fails closed.** A `trust.toml` that cannot be read or has other keys trusts nothing, and `kscope trust` leaves
  it as it is and says so.
- **Revoking.** `kscope trust --revoke` takes effect with the next scan. `kscope info` shows whether a workspace is
  trusted.

### Analyzer isolation

Kotlin and Java analysis runs in a child JVM, separate from the engine. An analyzer failure degrades to
`not-analysed` facts instead of a failed scan. The isolation is for stability and dependency separation; it
is not a security sandbox.

## Dependencies

Dependencies are limited to permissive licences; any other licence requires a decision record. See
[ADR 0001](docs/decisions/0001-platform-and-licensing.md).
