# ADR 0019: Installers in shell and PowerShell, and a skill for coding agents

Status: Accepted · Date: 2026-10-26

## In short

- **Installers.** `install.sh` (POSIX shell, for macOS and Linux) and `install.ps1` (PowerShell, for Windows)
  install a release for the current user and put `kscope` on the `PATH`. They are the one place outside the build
  and the hooks where owned code is not Kotlin. The code guidelines require an ADR for that, and this is it.
- **Agent skill.** The tool carries a skill for coding agents, a `SKILL.md` with one reference file.
  `kscope skill install` writes it into `~/.claude/skills` for Claude Code, and into `~/.agents/skills` for the
  agents that read the shared folder. The installers run that command.

## Problem

A release was a set of archives. A user had to unpack one, find its `bin` directory and edit the `PATH` by hand,
differently on each system, before typing the first command. A tool that agents are meant to call has to be one
command away, on every machine.

An agent that has `kscope` on its `PATH` does not know that it exists, when it helps, or that some of its options
run the project's own build. The agent hosts in common use load instructions from skill folders: Claude Code from
`~/.claude/skills`, and Codex, Gemini CLI and GitHub Copilot from `~/.agents/skills`. A skill has to be there, and
match the installed version, for the agent to use the tool well when the user mentions it.

## Options considered

1. **Installers in Kotlin.** Not possible: they run before a JVM is known to exist, and they download the JVM
   program itself.
2. **Package managers.** Homebrew, Scoop, winget and SDKMAN each need their own manifest repository, a review
   process and upkeep per release. These are worth adding later, but none of them covers all three systems.
3. **One shell script and one PowerShell script, fetched from the repository.** Chosen. The two languages that each
   system already runs, with no administrator rights and no extra repository.
4. **The skill as a file in the repository that users copy.** It goes stale with every release, and users have to
   know where each agent looks.
5. **The skill inside the tool, installed by a command.** Chosen. It is versioned with the tool, testable, and works
   for every install method, including a build from source.
6. **A folder for every agent** (`~/.codex/skills`, `~/.copilot/skills`, `~/.gemini/skills` too). Rejected:
   agents that read both their own folder and `~/.agents/skills` would list the skill twice.

## Evidence

- The documentation of each host names its personal skill folder.
  - Claude Code: `~/.claude/skills`.
  - Codex: `$HOME/.agents/skills`, with no folder of its own documented.
  - GitHub Copilot: `~/.copilot/skills` or `~/.agents/skills`.
  - Gemini CLI: `~/.gemini/skills`, with `~/.agents/skills` as an alias.
  - All of them read `SKILL.md` with `name` and `description` in its front matter.
- The installers were run against a local copy of a release.
  - **`install.sh`:** under `dash` on Ubuntu, piped as `curl | sh` does.
  - **`install.ps1`:** in Windows PowerShell 5.1 through `Invoke-Expression`.
  - Both installed and updated, refused a tampered archive and a foreign folder, and found Java or warned.
- `SkillBundleTest` fails when the skill names a command or subcommand that the tool does not have, or when its
  description passes 1,024 characters.

## Decision

- **Installers.**
  - Each downloads the archive of the latest release, or of `KSCOPE_VERSION`, and checks it against the
    release's `SHA256SUMS`. It installs nothing on a mismatch.
  - Each unpacks into a folder of the user and replaces only a folder that carries its marker file.
  - Each puts `kscope` on the `PATH`: a link in `~/.local/bin` plus one line in the shell's startup file, or the
    user `PATH` on Windows, kept as `REG_EXPAND_SZ`.
  - Each warns without JDK 17 or newer, and then runs `kscope skill install`.
  - Every effect can be switched off with an environment variable.
- **Skill.**
  - `kscope skill install` writes `SKILL.md` and `references/commands.md`, with the tool's version written in.
  - It writes to `<skills>/kscope` for each agent whose folder exists in the home folder, or to the folders given
    with `--dir`.
  - It replaces and `uninstall` removes only a `kscope` folder that is empty or names `kscope` in its front matter.
    A link is never followed.
  - The skill forbids the agent to run the build (`--level exact`, `--trust-build`, `trust`) or `service install`
    unless the user asks.

## Consequences

- Two non-Kotlin files ship to users. They have no tests in the build. A change to them is checked by running
  them against a local release, as recorded in the pull request.
- The installers read the repository at `KrosStack/KrosScope`. Moving or renaming it means changing the URL in
  both scripts and in the documentation.
- The skill is part of the user-facing contract. A renamed command or option has to change the skill too, and the
  test above enforces that for command names.

## Reversal cost

Low. The installers can be deleted; the archives stay usable by hand. `kscope skill` is one command group in its
own package; removing it leaves the skill folders that users installed, which `kscope skill uninstall` from an
older version removes.
