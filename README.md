# KrosScope

`kscope` indexes a Kotlin, Android or Kotlin Multiplatform project and tells you which files a task touches, what a
change can affect, and which source to read within a token budget. It runs on your machine, with no network, cloud
service or language model, and the same inputs always give the same output.

**Documentation:** [krosstack.github.io/KrosScope](https://krosstack.github.io/KrosScope/)

## Install

You need JDK 17 or newer; Git is recommended.

```shell
# macOS and Linux
curl -fsSL https://raw.githubusercontent.com/KrosStack/KrosScope/main/install.sh | sh
```

```powershell
# Windows, in PowerShell
irm https://raw.githubusercontent.com/KrosStack/KrosScope/main/install.ps1 | iex
```

Open a new terminal afterwards. [Installing kscope](docs/install.md) covers other versions, updates and removal.

## Quick start

In the root of your project:

```shell
kscope doctor                    # checks the JDK, the analysis worker, Git and the Android SDK
kscope scan                      # builds the index in .kscope
kscope analyze "add a reminder to tasks"
kscope context "add a reminder to tasks" --budget 4000 --format markdown > context.md
```

Run `kscope scan` again after the code changes; only what changed is redone. `.kscope` is derived data, safe to
delete. Every command explains itself with `kscope <command> --help`.

## Commands

### Build the index

| Command | What it does |
|---|---|
| `kscope scan` | Builds the index, or brings it up to date. `--rebuild` starts over, `--verify` checks it against a clean rebuild. |
| `kscope scan --level syntax\|resolved\|exact` | Sets how deep to read. `resolved` is the default, `exact` runs your Gradle build. |
| `kscope trust` | Lets later scans of this workspace run its build. `--revoke` takes that back. |
| `kscope workspace add\|list\|remove` | Registers repositories from elsewhere on disk, or from a Git URL, so they are indexed and linked together. |
| `kscope workspace artifacts add\|list\|remove` | Registers folders of built JAR and AAR files, such as `--maven-local`, to link a module to the one that builds its dependency. |

### Ask about a task

| Command | What it does |
|---|---|
| `kscope analyze "<task>"` | Ranks the files to edit, the files to read and the tests to run for a task. |
| `kscope analyze <name>` | Describes one class, function or file: what it depends on, what refers to it, its tests. |
| `kscope context "<task>"` | Gathers the source worth reading for a task, cut to `--budget <tokens>`. Add `--format markdown` to get the source itself. |

### Inspect the code

| Command | What it does |
|---|---|
| `kscope info` | Summarizes what the index holds. `--structure` describes the directory before anything is indexed. |
| `kscope symbol <name>` | Looks up declarations by name, or by a location such as `path/File.kt:42`. |
| `kscope impact <target>` | Lists what a change to a symbol, file or module can affect, nearest first. |
| `kscope related <target>` | Lists the files and symbols closest to a target in the graph, even with no words in common. |
| `kscope graph` | Shows how modules depend on each other. `--scope neighborhood --node <target>` zooms in; `--format dot` draws it with Graphviz. |

### Keep it ready

| Command | What it does |
|---|---|
| `kscope serve` | Keeps one workspace in memory in this terminal, so queries answer faster. `--watch` follows file changes, `--map` adds a live map in your browser, `--status` and `--stop` control it. |
| `kscope service add\|remove` | Chooses the workspaces that the background service keeps ready. |
| `kscope service start\|stop\|run\|status\|page` | Runs the service and shows what it is doing. `page` prints the address of its status page. |
| `kscope service install\|uninstall` | Starts the service with your session, or stops doing so. |

### Use it from a coding agent

| Command | What it does |
|---|---|
| `kscope skill install\|uninstall\|show` | Teaches Claude Code, Codex, Gemini CLI and GitHub Copilot which commands to run and how to read them. The installer runs `install` for you. |
| `kscope mcp` | Offers the commands as tools to an agent host through the Model Context Protocol ([set-up](docs/cli.md#mcp)). `--watch` keeps the index up to date. |

### Diagnose

| Command | What it does |
|---|---|
| `kscope doctor` | Checks that what kscope needs is available, and reports the text encodings in use. |
| `kscope benchmark` | For developers: scores file ranking against tasks with known answers. |

## Options that work across commands

| Option | What it does |
|---|---|
| `--workspace <dir>` | The project to work on. Defaults to the current directory. |
| `--format text\|json\|markdown` | Text for people, JSON for programs ([schemas](docs/schemas/README.md)), Markdown for prompts. `graph` also has `dot`. |
| `--detail brief\|standard\|full` | How much `analyze`, `symbol` and `related` print. |
| `--sections <list>` | Prints only these sections of `analyze`, `symbol` and `related`. |
| `--task-file <file>` | Reads the task of `analyze` or `context` from a UTF-8 file. |
| `--progress auto\|always\|never`, `-q` | Controls the progress shown on standard error. |
| `--no-server` | Answers without a running `serve` or `service`. |

Results go to standard output; progress and errors go to standard error. Exit codes: `0` success, `1` failure, `2`
usage error or ambiguous name, `3` a `--verify` mismatch.

## Running your build

A scan at the default level executes nothing from your project. `--level exact` and `--trust-build` run your Gradle
build to read an exact build model, so use them only on code you trust. `kscope trust` lets later scans do the same,
including those of `serve --watch` and the service. [SECURITY.md](SECURITY.md) has the details.

## More

- [Command-line reference](docs/cli.md): every option, `kscope.toml` settings and environment variables.
- [Support matrix](docs/support-matrix.md): the 23 kinds of link it follows, and what it does not.
- [Results](docs/benchmarks/README.md): what was measured, with the charts, the losses and the limits.
- [Architecture](docs/architecture.md), [decision records](docs/decisions/README.md) and
  [roadmap](docs/roadmap.md).
- [Contributing](CONTRIBUTING.md), [development guide](docs/development.md), [changelog](CHANGELOG.md) and
  [security](SECURITY.md).

Licensed under [Apache-2.0](LICENSE); see the [third-party licences](docs/third-party-licenses.md).
