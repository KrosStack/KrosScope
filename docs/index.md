# KrosScope

<div class="hero" markdown>

`kscope` indexes a Kotlin, Android or Kotlin Multiplatform project and tells you which files a task touches, what a
change can affect, and which source to read within a token budget. It runs on your machine, with no network, cloud
service or language model, and the same inputs always give the same output.

[Get started](getting-started.md){ .md-button .md-button--primary }
[View on GitHub](https://github.com/KrosStack/KrosScope){ .md-button }

</div>

## Install

You need JDK 17 or newer; Git is recommended.

=== "macOS / Linux"

    ```shell
    curl -fsSL https://raw.githubusercontent.com/KrosStack/KrosScope/main/install.sh | sh
    ```

=== "Windows"

    ```powershell
    irm https://raw.githubusercontent.com/KrosStack/KrosScope/main/install.ps1 | iex
    ```

Open a new terminal afterwards. [Installing kscope](install.md) covers other versions, updates and removal.

## What it does

<div class="grid cards" markdown>

-   __Rank the files for a task__

    `kscope analyze` lists the files to edit, the files to read and the tests to run.

    [:octicons-arrow-right-24: `analyze`](cli.md#analyze)

-   __Read within a budget__

    `kscope context` gathers the source worth reading, cut to the number of tokens you give it.

    [:octicons-arrow-right-24: `context`](cli.md#context)

-   __See what a change reaches__

    `kscope impact` and `kscope related` list what a change to a symbol, file or module can affect.

    [:octicons-arrow-right-24: Support matrix](support-matrix.md)

-   __Use it from a coding agent__

    `kscope skill` teaches Claude Code, Codex, Gemini CLI and GitHub Copilot which commands to run.

    [:octicons-arrow-right-24: `skill`](cli.md#skill)

</div>

## Results

KrosScope is ahead of both BM25 baselines on most task sets and metrics, and behind on some. The
[results](benchmarks/README.md) show the losses and the limits as well.

![Changed files and source share in a context package, by token budget](benchmarks/charts/context-budget.light.svg#only-light)
![Changed files and source share in a context package, by token budget](benchmarks/charts/context-budget.dark.svg#only-dark)

A 2,000-token package holds 38.9% of the changed files, against 14.7% for the better BM25 baseline. At 16,000
tokens it holds 67.6% against 40.4%, but less of that package is source of the changed files (3.6% against 4.5%).

![Changed files found in the first five and ten results](benchmarks/charts/localization.light.svg#only-light)
![Changed files found in the first five and ten results](benchmarks/charts/localization.dark.svg#only-dark)

In the first ten results, 46.5% of the changed files are found, against 43.4% for the better BM25 baseline. Both
charts are means over the 60 held-out tasks of two public repositories.

![Seconds per operation on two workspaces](benchmarks/charts/speed.light.svg#only-light)
![Seconds per operation on two workspaces](benchmarks/charts/speed.dark.svg#only-dark)

On a clone of Now in Android with 715 files, an update after an edit inside a function body takes 8.6 s and
`analyze` through `kscope serve` takes 1.6 s. These are single runs on one shared machine.

## Next steps

- [Getting started](getting-started.md): from install to your first context package.
- [CLI reference](cli.md): every command, option and setting.
- [Architecture](architecture.md) and [design decisions](decisions/README.md): how it works.
- [Results](benchmarks/README.md): what was measured, and how.
