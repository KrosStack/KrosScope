---
name: kscope
description: Navigates Kotlin, Android and Kotlin Multiplatform Gradle codebases with the kscope command-line tool (KrosScope). Use it when the user mentions kscope or KrosScope. Also use it before non-trivial work in a large, multi-module or unfamiliar Kotlin, Android or KMP project, to find where a change or a bug fix goes, to gather the code to read within a token budget, to see what a change affects, or to pick the tests to run. Not for an edit in a file the user already pointed to, build or Gradle errors, or Kotlin questions outside a project.
---

# kscope

`kscope` indexes a Kotlin, Android or Kotlin Multiplatform Gradle workspace on this machine and answers questions
from that index. It tells you which files a task touches, what to read, what a change affects and which tests
cover it. At its default level it runs nothing from the project and sends nothing anywhere. This skill came with
kscope {{kscope-version}}; when `kscope --version` differs, `kscope <command> --help` is the authority.

## When to use it

- **The user mentions kscope or KrosScope.** Use it for the request, and say which commands you ran.
- **Before changing code you do not know well.** This is most useful in a large or multi-module project. Run
  `analyze` to find where the change goes and which tests to run, before opening files one by one.
- **Before changing something widely used**, such as a public class, an interface or a data model. Run `impact`
  to see what else the change can affect.
- **When you need the relevant source in one piece,** within a token budget. Run `context`.
- **When the project uses something built in another repository**, such as a library, a native library, a web
  bundle or a shared API spec. If that repository is on the disk, or the user gives its Git URL, offer to register
  it with `kscope workspace add`, then run `kscope scan`. Its files then answer with the project's own. Run
  `kscope workspace list` to see what is registered already. See "Several repositories".

Skip it, and the first check below, for an edit in a file you already know or that the user named. The project's
own pilots on small tasks found no gain over reading the files directly.

## First check

1. **Check it is installed:** `kscope --version`. If the command is not found, tell the user. Do not install
   software unless they ask.
   - **macOS or Linux:** `curl -fsSL https://raw.githubusercontent.com/KrosStack/KrosScope/main/install.sh | sh`
   - **Windows PowerShell:** `irm https://raw.githubusercontent.com/KrosStack/KrosScope/main/install.ps1 | iex`
2. **Find the workspace root.**
   - **With an index:** the directory that holds the `.kscope` folder.
   - **Without one:** the directory with `settings.gradle.kts` or `settings.gradle`. For several repositories side
     by side, it is their common parent.
   - Run every command there, or pass `--workspace DIR`. Scanning a subdirectory of an indexed workspace makes a
     second, separate index.
   - `kscope info --structure` describes a directory without needing an index.
3. **Check the index:** `kscope info`.
   - If it fails with `No index found`, run `kscope scan`. The first scan of a large project can take several
     minutes: say so, and run it in the background if your environment allows long commands. Later scans are
     incremental.
   - **Read the `Level` and `Trust` lines.** If the level is `exact` and the workspace is trusted, a plain
     `kscope scan` runs the project's Gradle build. Ask the user before scanning.
4. **Keep it current.** Answers come from the last scan, and they do not warn when files changed since then. Run
   `kscope scan` after you or the user edit code, before you rely on new answers. A watching server (see the
   last section) rescans by itself, and says `Index is behind: ...` on standard error when it is still catching
   up.

If something fails in a way you do not understand, run `kscope doctor`. It checks the JDK (17 or newer), the
analysis worker, Git, the Android SDK and the index.

## Workflow for a task

1. **Find where it goes:**
   ```
   kscope analyze "<the task, in the user's words>" --detail brief
   ```
   - `--detail brief` gives one screen: the headline, the five best files, the number of tests and the next
     step.
   - Without it you get every section: `Files to edit`, `Files to read`, `Documents`, `Tests to run`, the scope
     of the change, and the context size.
   - **Read the headline's last words.** `one clear match` means the ranking is confident. `no strong match`
     means rephrase the task with the project's own words, such as class, screen or feature names, or look up a
     name with `kscope symbol`.
   - **Open the files to edit first,** then the files to read as you need them.
2. **Read the source in one piece** when you would otherwise open many files:
   ```
   kscope context "<the same task>" --budget 8000 --format markdown > <temporary file outside the workspace>
   ```
   - Use the budget the user gave. Otherwise choose one that fits your context; 4000 to 16000 suits most tasks.
   - Write the package to a file and read the file. A package of several thousand tokens is longer than many
     shells show of a command's output.
   - Below 8000 tokens the package is raw source in rank order. From 8000 it also has signatures, outlines and
     the code at module boundaries.
3. **Check what a change affects** before changing a signature or a shared type, and again after editing:
   ```
   kscope impact <file, path:line, Name or :module>
   ```
   - It lists the affected files and modules, nearest first, and the tests that cover them.
   - When a name matches several declarations, it exits with 2 and lists a value for each. Pass one of those
     values, or the `path:line` shown as `Declared at` by `kscope symbol <Name>`.
4. **Run the tests it names.** `Tests to run` lists test files. Run them with the project's own test tasks, for
   example `./gradlew :module:test --tests 'ClassName'`.
5. **Look up one thing:**
   - `kscope analyze <Name>`: a card with the declaration, what it depends on, what refers to it, its tests and
     its impact. A file path or `path:line` works too.
   - `kscope symbol <Name>`: the declarations matching a name.
   - `kscope related <node>`: the closest collaborators, even ones that share no words with it.
   - `kscope graph`: how modules depend on each other. Add `--scope neighborhood --node <node>` for one node's
     surroundings.

## Several repositories

- **Register one.** `kscope workspace add <path or Git URL>` registers a repository from elsewhere on the disk, then
  `kscope scan` indexes it. A URL is cloned first, beside the workspace root or into `--dir`, at `--ref` when
  given; `--name` sets the name. `kscope workspace list` shows what is registered. `kscope workspace remove <name>`
  forgets one and keeps its checkout. None of these builds anything. Do them only when the user asks or agrees.
- **Paths.** A file of a registered repository prints as `<name>/<path in it>`. Pass that workspace path to
  `impact`, `related` and `analyze`.
- **What is linked across repositories:**
  - **Maven coordinates.** A module that depends on coordinates that a module of another repository publishes.
  - **JAR files.** A `files(...)` or `fileTree(...)` dependency on a JAR, and the module of another repository that
    builds a JAR of that name.
  - **Native libraries.** `System.loadLibrary`, `System.load` or JNA `Native.load`, and the CMake, Cargo or
    ndk-build target that builds the library. Kotlin `external` and Java `native` declarations, and their `Java_`
    functions in C, C++ or Rust.
  - **Specs.** An OpenAPI Generator `inputSpec` or a Wire `sourcePath`, and the spec it names; code generated from
    it, and the operations, schemas and messages it stands for; an OpenAPI operation and the Ktor route that
    implements it.
  - **Web bundles.** A Gradle copy task (`Copy`, `Sync`, `processResources`) and the Vite, webpack, Rollup or
    Kotlin/JS or Wasm project that builds the files it copies; a file of the bundle and the source it came from.
- **What the answers look like.** `impact`, `related`, `analyze` and `context` follow these links, so a change to a
  library, a native function, a spec or a web source lists its consumers in the other repositories.
  - **Certainty.** `impact` prints `exact`, `probable` or `heuristic` for each item; a path is as certain as its
    weakest link. Treat a cross-repository item that is not `exact` as a lead to check in the source.
  - **In JSON** each relationship has `kind`, `subKind` (`maven-coordinates`, `jvm-library`, `native-library`,
    `generator-input`, `openapi-route`, `web-bundle` or `source-map` for these links), `evidenceClass` and
    `resolution`.
  - **The evidence class says how the link was found.** `compiler`: the compiler resolved it. `build-model`: a
    build file declares it. `convention`: a naming rule joins the two ends, such as the name of a library or of a JNI
    function. `content`: the content of a build output matches, such as the classes of a JAR or a source map.
- **Some links need build outputs.** A bundle file is linked to its source only from a built bundle with a source
  map, and a JAR link becomes `exact` only when a built JAR matches the module. These appear after the build has run
  and `kscope scan` has run again. Do not run the build to get them: say that the link is missing, and ask the user
  to build.
- **A producer that is missing or ambiguous is not guessed.** The consumer is `unresolved` with a reason such as
  `artifact.producer-not-in-workspace` or `artifact.ambiguous-producer`. Register the repository that builds it, or
  tell the user.

## Reading the results

- **Paths** are relative to the workspace root, and printed in full when captured.
- **Scores** compare items within one result only. They are not probabilities.
- **Lists stop at a relevance cut-off** (30% of the best score, and at least three items). They say how many they
  left out. `--detail full` lists every relevant item. `--format json` lists everything scored, with the
  evidence (`schemaVersion: 1`). Text is shorter, so prefer it unless you need fields.
- **Task or name.** `analyze` says whether it read the argument as a task or a name. Force it with `--as task`
  or `--as symbol`.
- **The index level** shows in `kscope info` and as `indexLevel` in JSON.
  - `syntax`: declarations only, with no references resolved.
  - `resolved`: the default, with references resolved by the compiler inside the workspace.
  - `exact`: also the project's own build model.
  - Below `exact`, references into libraries stay unresolved, so a link that runs through a library can be
    missing.
- **Exit codes:**
  - 0: success.
  - 1: failure. `No index found` means run `kscope scan`; an unreadable index means `kscope scan --rebuild`.
  - 2: a usage error or an ambiguous name.
  - 3: `scan --verify` found a difference.

## Rules

- **Never make kscope run the project's build on your own.** The build executes code from the project.
  - Do not pass `--level exact` or `--trust-build`.
  - Do not run `kscope trust`: it lets later scans run the build.
  - In a trusted workspace at level `exact`, ask before `kscope scan` or `kscope serve --watch` (see
    "First check").
  - Do any of these only when the user asks for it in so many words.
- **Change nothing outside the index unless asked.** Do not run `kscope service install`,
  `kscope service uninstall` or `kscope trust --revoke`; they change the user's machine or their trust settings.
  Run `kscope workspace add` or `remove` only when the user asks or agrees, and clone a URL only when the user
  gave it.
- **`kscope scan --rebuild` discards the index.** Use it only when the index is unreadable or the user asks.
- **Pass the task as one quoted argument.**
  - For text with non-ASCII characters on Windows, or very long text, write it to a UTF-8 file and pass
    `--task-file FILE`.
  - Put `--` before a task that starts with `-`.
- **`.kscope` holds derived data.** Do not edit or commit it. It can be deleted, and a scan recreates it.

## Many questions in a row

- **A warm server.** Start `kscope serve --watch --idle-timeout 30` in the background. It keeps the index in
  memory and rescans changed files. The same commands then answer faster and see your edits.
  - It runs until `kscope serve --stop`, or until 30 minutes pass without a request.
  - While it runs, `kscope scan` goes through it, and `scan --no-server` is refused.
- **MCP tools.** If your host already offers kscope tools over MCP (`context`, `analyze`, `symbol`, `impact`,
  `related`, `info`, `scan`), use them. They run the same commands. Set up `kscope mcp` for a host only if the
  user wants it.

For every option of every command, read [references/commands.md](references/commands.md).
