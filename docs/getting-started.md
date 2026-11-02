# Getting started

This page takes you from a fresh install to a first answer about your own project.

## Requirements

- JDK 17 or newer.
- Git is recommended. Inside a Git work tree, the files to index come from `git ls-files`, so `.gitignore` applies.
- A Kotlin, Android or Kotlin Multiplatform project.

## Install

=== "macOS / Linux"

    ```shell
    curl -fsSL https://raw.githubusercontent.com/KrosStack/KrosScope/main/install.sh | sh
    ```

=== "Windows"

    ```powershell
    irm https://raw.githubusercontent.com/KrosStack/KrosScope/main/install.ps1 | iex
    ```

Open a new terminal afterwards. [Installing kscope](install.md) covers other versions, updates, removal and what
the installer changes on your machine.

## Check the setup

Go to the root of your project and run:

```shell
kscope doctor
```

It checks the JDK, the analysis worker, Git, the Android SDK directory and the index. A missing optional component
shows as `missing` with a remedy, and the exit code stays 0.

## Build the index

```shell
kscope scan
```

This builds the index in a `.kscope` folder in your project. It is derived data, so it is safe to delete. A scan at
the default level (`resolved`) executes nothing from your project.

`kscope scan --level exact` and `--trust-build` run your Gradle build to read an exact build model. Use them only
on code you trust. See [`--trust-build`](cli.md#--trust-build).

## Ask about a task

Describe what you want to do in your own words:

```shell
kscope analyze "add a reminder to tasks"
```

The answer ranks the files to edit, the files to read and the tests to run. `kscope analyze` also takes a class,
function or file name, such as `TaskRepository`, and describes it: what it depends on, what refers to it and its
tests.

## Collect context within a budget

```shell
kscope context "add a reminder to tasks" --budget 4000 --format markdown > context.md
```

`--budget` is the most tokens the whole Markdown may use. With `--format markdown` the command prints the context
itself, ready to paste into a prompt. Without a budget, `--level` (`minimal`, `recommended`, `deep` or `full`)
decides how much is included.

For a longer task, put it in a UTF-8 file and pass it with `--task-file`.

## Keep the index current

After the code changes, run `kscope scan` again. Only what changed is redone. If the index becomes unreadable,
`kscope scan --rebuild` starts over.

To make queries answer faster, `kscope serve` keeps one workspace in memory in your terminal. `--watch` follows file
changes. See [`serve`](cli.md#serve).

## Use it from a coding agent

The installer runs `kscope skill install` when it finds a JDK. That installs a skill for the coding agents it finds
in your home folder: Claude Code, Codex, Gemini CLI, GitHub Copilot and others. An agent then knows which commands
to run when you mention `kscope`, or when it works in a Kotlin project.

To install or remove it yourself:

```shell
kscope skill install
kscope skill uninstall
```

`kscope skill show` prints the skill, for an agent that reads no skill folder. To offer the commands as tools
through the Model Context Protocol instead, see [`mcp`](cli.md#mcp).

The skill tells an agent not to run your build (`--level exact`, `--trust-build`, `trust`) or `service install` on
its own. See [`skill`](cli.md#skill).

## Where to go next

- [CLI reference](cli.md): every command, option, setting and environment variable.
- [JSON result schemas](schemas/README.md): for scripts and agents that read `--format json`.
- [Support matrix](support-matrix.md): which links it follows, and which it does not.
- [Results](benchmarks/README.md): what was measured, including where it loses.
- [Architecture](architecture.md): how the index and the ranking work.
