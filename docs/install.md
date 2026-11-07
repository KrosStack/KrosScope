# Installing kscope

`kscope` needs JDK 17 or newer. Git is recommended. Install it once per machine, and then use it from any
terminal.

The installers bundle no Java runtime; on Windows, the optional semantic worker requires a JDK 17 whose bundled
Microsoft Visual C++ runtime is 14.40 or newer, for example Temurin 17.0.21 or newer.
Oracle JDK 17.0.12 bundles an older runtime that can crash ONNX initialization in the child JVM; component installation
tests the worker and reports this failure while keeping the installation.

On macOS and Linux:

```shell
curl -fsSL https://raw.githubusercontent.com/KrosStack/KrosScope/main/install.sh | sh
```

On Windows, in PowerShell:

```powershell
irm https://raw.githubusercontent.com/KrosStack/KrosScope/main/install.ps1 | iex
```

Then open a new terminal and check the setup:

```shell
kscope --version
kscope doctor        # in a project: checks the JDK, the analysis worker, Git and the Android SDK
```

## What the installer does

1. **Downloads and checks the release.** It downloads the latest release from GitHub and checks the archive
   against the release's `SHA256SUMS`. If the sum does not match, it installs nothing.
2. **Unpacks it for your user only.** On macOS and Linux it unpacks into `~/.local/share/kscope`; on Windows,
   into `%LOCALAPPDATA%\Programs\kscope`. It replaces a version it installed before, and never touches a
   folder it did not make.
3. **Puts `kscope` on your `PATH`.**
   - **macOS and Linux:** it links `~/.local/bin/kscope`. If `~/.local/bin` is not yet on your `PATH`, it adds
     the directory in your shell's startup file: `~/.zshrc`, `~/.bashrc` (`~/.bash_profile` on macOS), a file
     in `~/.config/fish/conf.d`, or else `~/.profile`.
   - **Windows:** it adds the `bin` folder to your user `PATH`.
4. **Checks Java.** It warns when it finds no JDK 17 or newer, on the `PATH` or in `JAVA_HOME`.
5. **Teaches your coding agents.** With a JDK present, it runs `kscope skill install`. That command installs a
   skill for the coding agents it finds in your home folder: Claude Code, Codex, Gemini CLI, GitHub Copilot and
   others. An agent then knows how to use `kscope` when you mention it, or when it works in a Kotlin project. See
   [`skill`](cli.md#skill).

It needs no administrator rights.

## Options

Set these environment variables to change what the installer does. All of them are optional.

| Variable | Meaning | Default |
|---|---|---|
| `KSCOPE_VERSION` | The version to install, such as `0.1.0`. | the latest release |
| `KSCOPE_INSTALL_DIR` | Where the tool is unpacked. | `~/.local/share/kscope`, `%LOCALAPPDATA%\Programs\kscope` |
| `KSCOPE_BIN_DIR` | macOS and Linux: where `kscope` is linked. | `~/.local/bin` |
| `KSCOPE_NO_MODIFY_PATH` | `1` leaves your shell startup file or user `PATH` alone. | not set |
| `KSCOPE_NO_AGENT_SKILL` | `1` skips installing the skill for coding agents. | not set |
| `KSCOPE_DOWNLOAD_URL` | Where the release files are, for a mirror. | the GitHub release of the version |

For example:

```shell
curl -fsSL https://raw.githubusercontent.com/KrosStack/KrosScope/main/install.sh | KSCOPE_VERSION=0.1.0 sh
```

```powershell
$env:KSCOPE_VERSION = '0.1.0'; irm https://raw.githubusercontent.com/KrosStack/KrosScope/main/install.ps1 | iex
```

## Update

Run the installer again. It replaces the installed version with the latest release, or with
`KSCOPE_VERSION`.

## Uninstall

First run `kscope skill uninstall`, which removes the skill for coding agents. If you registered the background
service, also run `kscope service uninstall` (see [the CLI reference](cli.md#service)). Then delete what the
installer made.

On macOS and Linux:

1. Delete the tool and its link:
   ```shell
   rm -rf ~/.local/share/kscope ~/.local/bin/kscope
   ```
2. Remove the `# kscope` lines the installer added to your shell startup file.

On Windows:

1. Delete the tool:
   ```powershell
   Remove-Item -Recurse "$env:LOCALAPPDATA\Programs\kscope"
   ```
2. Remove its `bin` folder from your user `PATH`, under *Edit environment variables for your account* in the
   Start menu.

## Without the installer

- **From a release:** download `kscope-<version>.zip` or `kscope-<version>.tar.gz` from the
  [releases page](https://github.com/KrosStack/KrosScope/releases). Check it against `SHA256SUMS`, unpack it
  anywhere, and put its `bin` directory on your `PATH`.
- **From source:** run `./gradlew :cli:installDist` (`gradlew.bat` on Windows) in this repository. Then put
  `cli/build/install/kscope/bin` on your `PATH`. See the [development guide](development.md).
