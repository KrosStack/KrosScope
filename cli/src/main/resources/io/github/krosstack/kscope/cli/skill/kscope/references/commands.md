# kscope commands

The full reference is `docs/cli.md` in the KrosScope repository, and `kscope <command> --help` prints the options
and examples of one command.

## Common options

They work before or after the command name.

| Option | Meaning |
|---|---|
| `--workspace DIR` | The project directory. Defaults to the current directory. |
| `--format text\|json\|markdown\|dot` | `text` (default) for reading, `json` for fields (`schemaVersion: 1`), `markdown` for `context` packages, `dot` for `graph`. |
| `--detail brief\|standard\|full` | `analyze`, `symbol`, `related` only: one screen, the default, or every relevant item. Not with `--format json`. |
| `--sections LIST` | `analyze`, `symbol`, `related` only: print just these sections, for example `--sections tests`. |
| `-q`, `--quiet` | No progress on standard error; errors still appear. |
| `--no-server` | Answer without a running `kscope serve`. A scan is refused with it while a server runs. |
| `--no-ml`, `--ml on\|off` | `analyze` only: answer without learned signals when off; JSON `semantic.state` says `off`. `--no-ml` wins. |

Results go to standard output, and progress and errors to standard error.

## Build the index

```
kscope scan [--rebuild] [--verify] [--level syntax|resolved|exact] [--trust-build]
```

- **What it does.** Builds `.kscope`, or brings it up to date incrementally. Inside a Git work tree it indexes
  what `git ls-files` lists, honouring `.gitignore`.
- **The level.** It keeps the level of the index. The first scan runs at `resolved`.
- **`--rebuild`** starts from scratch.
- **`--verify`** compares the result with a clean rebuild and exits with 3 on a difference.
- **`--level exact` and `--trust-build`** run the project's Gradle build, which executes project code. Use them
  only when the user asks.

```
kscope trust [--revoke]
```

Lets later scans at level `exact` run the build of this workspace on this machine. Only at the user's request.

```
kscope workspace add LOCATION [--name NAME] [--ref REF] [--dir DIR]
kscope workspace remove NAME
kscope workspace list
```

- **What it does.** Registers a repository from elsewhere on the disk with the workspace. The next scan indexes it
  as one more repository, and its files print under its name, such as `native-core/src/checksum.cpp`.
- **`LOCATION`** is a directory in a Git work tree with a commit, outside the workspace, or a Git URL. A URL is cloned
  first, beside the workspace root or into `--dir`, at `--ref` when given.
- **`--name`** sets the name. It defaults to the folder name of the checkout or of the URL.
- **`remove`** forgets the registration and keeps the checkout. **`list`** shows each one and says `(missing)` when
  its checkout is gone.
- Nothing is scanned or built by these commands. A refusal exits with 1 and says why. Only at the user's request.
- **After registering,** run `kscope scan`. Links to JAR files, native libraries, specs and web bundles are made by
  that scan; the ones that need a build output appear once the user has built and you scan again.

## Ask about a task

```
kscope analyze [TASK] [--task-file FILE] [--as task|symbol]
```

- **A task in words** gives:
  - files to edit and to read, with scores and reasons;
  - documents, tests to run, modules and the scope of the change;
  - the context size per level, and a next step.
- **One token** is read as a name or file and gives a card: a CamelCase or snake_case identifier, a qualified
  name, a workspace path or `path:line`.
- **`--format json`** is always the task analysis.
- **Sections** for `--sections`:
  - a task: `headline`, `top`, `query`, `edit`, `read`, `documents`, `cutoff`, `tests`, `scope`, `gaps`,
    `connectedness`, `context`, `next`;
  - a name: `headline`, `declaration`, `depends-on`, `referred-by`, `tests`, `impact`, `alternatives`, `next`.

```
kscope context [TASK] [--task-file FILE] [--budget TOKENS] [--level minimal|recommended|deep|full]
               [--estimator o200k_base|cl100k_base|chars]
```

- **What it compiles.** The source worth reading for the task, from the current files.
- **Formats.** `--format markdown` prints the package itself, `text` a summary of what was chosen, and `json` the
  fragments with their evidence and omissions.
- **The budget** covers the whole rendered Markdown.
- **Below 8000 tokens,** every level gives raw source in rank order, with no signatures or outlines. Tests appear
  there below `deep` only when they rank as files to edit.
- **Levels,** without a budget or from 8000 tokens on:
  - `minimal`: about 50% of the relevance;
  - `recommended`, the default: 80%, with boundary code and test signatures;
  - `deep`: adds direct neighbours and test bodies;
  - `full`: every relevant file.

## Inspect the code

| Command | What it answers |
|---|---|
| `kscope info` | What the index holds: files, modules, symbols, relationships by state, the index level, the settings and whether the workspace is trusted. `--structure` describes the directory instead. |
| `kscope symbol NAME` | Declarations matching a simple or qualified name, a symbol id or `path:line`: exact matches first, then ranked ones. |
| `kscope impact NODE` | What a change to a file, `path:line`, symbol or module (`:core:model`) can affect, nearest first, with certainty, modules and covering tests. An ambiguous name exits with 2 and lists the values to pass. |
| `kscope related NODE` | The files and symbols closest to a node through the stored relationships, up to 50 of each. |
| `kscope graph [--scope modules\|workspace\|neighborhood] [--node NODE] [--depth 0-3] [--limit N]` | Module dependencies by default. `neighborhood` shows the surroundings of `--node`. `--format dot` prints Graphviz. |

## Keep it ready

```
kscope serve [--watch] [--idle-timeout MINUTES]
kscope serve --status
kscope serve --stop
```

- **What it does.** Keeps the index of one workspace in memory, so that `analyze`, `context`, `symbol`, `impact`,
  `related`, `graph`, `info` and `scan` in that workspace answer faster. The output is the same byte for byte.
- **It runs in the foreground** until `--stop`, Ctrl+C, or `--idle-timeout` minutes without a request (`0`, the
  default, never stops).
- **`--watch`** rescans changed files by itself, and says `Index is behind: ...` on standard error while it catches
  up. It runs the build only in a trusted workspace whose `kscope.toml` asks for `level = "exact"`.
- **`--status`** exits with 0 when a server runs, and 1 when none does.

```
kscope mcp [--watch]
```

- **What it does.** Offers `context`, `analyze`, `symbol`, `impact`, `related`, `info` and `scan` as Model
  Context Protocol tools over standard input and output.
- **What it cannot do.** The `scan` tool never runs the build, and no tool writes files.
- **Setting it up.** Claude Code reads `.mcp.json` in the project:
  `{"mcpServers": {"kscope": {"command": "kscope", "args": ["mcp", "--watch"]}}}`.

```
kscope service add|remove|run|start|stop|status|page|install|uninstall
```

One background process that serves several workspaces. `install` and `uninstall` change what starts with the
user's session, so run them only at the user's request.

## Diagnose and maintain

| Command | What it does |
|---|---|
| `kscope doctor` | Checks the JDK (17 or newer), the Kotlin analysis worker, Git, the Android SDK and the index. It reports missing optional parts with a remedy, and exits with 0 unless the index cannot be read. |
| `kscope skill install [--dir DIR]` | Installs this skill for the coding agents found in the home folder, or in `DIR`. Running it again updates it. |
| `kscope skill uninstall [--dir DIR]` | Removes this skill again. |
| `kscope skill show` | Prints this skill's `SKILL.md`. |

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Success. |
| 1 | Failure. `No index found` means run `kscope scan`, and an unreadable index means `kscope scan --rebuild`. It also covers a scan refused because of its level, a workspace that cannot be read, an invalid `kscope.toml`, and a skill folder that kscope did not make. |
| 2 | A usage error: an unknown option, an invalid value, `--detail` or `--sections` where they do not apply, or a name that matches several declarations. |
| 3 | `scan --verify` found a difference from a clean rebuild. |
