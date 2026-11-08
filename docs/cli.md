# Command-line reference

Every command and option of `kscope`, the `kscope.toml` settings and the environment variables. The
[README](https://github.com/KrosStack/KrosScope/blob/main/README.md) gives the overview; `kscope <command> --help` prints the options of one command.

## Common options

They are accepted before and after the command name; an option after the command wins. A command honours only the
ones listed for it here.

| Option | What it does | Default | Applies to |
|---|---|---|---|
| `--workspace DIR` | The project to work on. It must exist. | The current directory | Every command except `skill`. `service add` and `service remove` take their own. |
| `--format text\|json\|markdown\|dot` | How to print the result; see [Output formats](#output-formats). | `text` | `scan`, `context`, `analyze`, `info`, `symbol`, `impact`, `related`, `graph`, `doctor`, `benchmark`. `dot` is for `graph` only. |
| `--progress auto\|always\|never` | When to show progress on standard error; see [Progress](#progress). | `auto` | `scan`, `context`, `analyze`, `symbol`, `impact`, `related`, `graph` |
| `-q`, `--quiet` | No progress; standard error then carries errors only. | Off | Same as `--progress` |
| `--detail brief\|standard\|full` | How much text to print; see [Output size](#output-size). Text and markdown only. | `standard` | `analyze`, `symbol`, `related` |
| `--sections LIST` | Prints only these sections, a comma-separated list. Text and markdown only. | All | `analyze`, `symbol`, `related` |
| `--task-file FILE` | Reads the task from a UTF-8 file; see [Text encodings](#text-encodings). | None | `analyze`, `context` |
| `--no-server` | Answers without the server that [`serve`](#serve) or the [service](#service) runs for the workspace. | Off | `scan`, `context`, `analyze`, `info`, `symbol`, `impact`, `related`, `graph`, `mcp` |
| `--no-ml`, `--ml on\|off` | Answers without learned signals when off; the JSON `semantic.state` says `off`. | `on` | `analyze` |
| `--version`, `-h`, `--help` | Print the version, or the help of the tool or a command, and exit. | | |

`--detail` and `--sections` on any other command, or with `--format json`, exit with 2. `--no-ml` wins over
`--ml on` wherever they appear. With no learned provider installed, turning ML off changes only the JSON semantic
status; rankings, context estimates and text output stay the same. `context` does not expose this control.

## Output formats

The JSON of `analyze` carries `schemaVersion` 2 and a `semantic` object; its `state` is `off`, `not-installed`, `disabled`, `used`, `behind`, `worker-unavailable` or `static-fallback`. The other results stay at version 1.

| Format | For | Notes |
|---|---|---|
| `text` | People | The default. The answer in one line, then sections under headings. |
| `json` | Programs and agents | Stable. Every result has `schemaVersion` and, when read from an index, `indexLevel`. See the [schemas](schemas/README.md). |
| `markdown` | Prompts | `context` prints the context package itself, `graph` prints DOT, the others print their text. |
| `dot` | Graphviz | `graph` only. The other commands that take `--format` exit with 2. |

- Counts have thousands separators and paths are relative to the workspace. A long list is cut and ends with
  `N more (use --format json for the full list)`; JSON always has the full list.
- On a terminal the first line, headings and table headers are bold cyan (not with `NO_COLOR` set) and long paths are
  shortened in the middle. Captured output (`>`, a pipe, an agent) holds no escape code and every path in full.
- A command that needs an index and finds none prints `error: No index found. Run `kscope scan` first.` on standard
  error, exits with 1 and creates nothing. `doctor` reports a missing index as a check instead.

## Progress

Progress goes to standard error, never to standard output. A command that takes more than a second draws one status
line on a terminal (stage, counts, elapsed time), replaced by a summary at the end. Elsewhere it prints one plain
line for each stage that took a second or more, such as `Analysing sources: 68 units (6.7 s)`, and a summary.

| Option | Effect |
|---|---|
| `--progress auto` | The default. A live line when standard error is a terminal, plain lines otherwise. |
| `--progress always` | Always the live line, with every stage shown. For terminals that are not recognised. |
| `--progress never` | No progress. |
| `--quiet` | No progress and no summary. Errors still go to standard error. |

## `scan`

Builds the index in `.kscope`, or brings it up to date. It is the one command that indexes, and it is incremental.

```
kscope scan [--rebuild] [--verify] [--level syntax|resolved|exact] [--trust-build]
```

Inside a Git work tree the files come from `git ls-files`, so `.gitignore` applies. Elsewhere the scan skips `.git`,
`.gradle`, `.idea`, `.kscope`, `build`, `out` and `node_modules`. An update redoes only the work whose inputs changed:

- An edit inside a function body, accessor, secondary constructor or `init` block redoes only that source set.
- An edit that other files can see (a signature, expression body, initializer, default value, annotation, `inline`
  function) also redoes the source sets that depend on it.
- A Java file, a script, or a Kotlin file that does not parse always counts with its whole content.

| Option | What it does | Default |
|---|---|---|
| `--rebuild` | Discards the index and scans from scratch. Use it when the index is unreadable. | Off |
| `--verify` | Scans again into a scratch index and compares the two. A difference prints `Verification failed: ...` and exits with 3. | Off |
| `--level LEVEL` | How deep to scan; see [Levels](#levels). | The level of the index, else `resolved` |
| `--trust-build` | The same as `--level exact`. Runs your build; see [`--trust-build`](#--trust-build). | Off |

Errors and warnings are listed under `Problems`; notes are only counted, and `--format json` lists all. An invalid
[`kscope.toml`](#settings-file) stops the scan with exit 1 before it touches the index. `update` is a hidden alias of
`scan` for old scripts.

### Levels

| Level | What it does |
|---|---|
| `syntax` | Declarations only: files, the build model read from files, and the symbols they declare. No reference, call or inheritance is resolved, so nothing has compiler evidence. Queries still answer from declarations, names and conventions. |
| `resolved` | The default. The compiler resolves references inside the workspace and the build model is read from files. No code of the workspace runs. |
| `exact` | Also runs the Gradle build of the workspace to read its exact build model and classpaths. See [`--trust-build`](#--trust-build). |

Every JSON result names the level that produced it as `indexLevel`. `info` shows the level of the index, the level the
settings ask for and whether the workspace is trusted. A query on a `syntax` index adds `Index level: syntax;
references are not analysed.` to standard error.

- **`--level` is followed.** `--trust-build` equals `--level exact`; giving both with different levels exits with 2.
- **Without it,** a scan keeps the level of the index and never goes shallower. A `level` in `kscope.toml` counts when
  it is deeper than the index. A first scan runs at `resolved`, and a plain scan deepens a `syntax` index to `resolved`.
- **A shallower level needs `--rebuild`.** `kscope scan --level resolved` on an `exact` index exits with 1 and touches
  nothing.
- **`exact` needs trust unless you type it.** `level = "exact"` in `kscope.toml`, or an `exact` index, runs the build
  only in a workspace you [trusted](#trust). Otherwise a scan runs at `resolved`, exits with 0 and lists the warning
  `level.exact-needs-trust`; a plain `kscope scan` of an `exact` index there exits with 1 and runs nothing.

### Workspaces of several repositories

A directory that is not itself in a Git work tree can hold several repositories side by side. When two or more of its
direct subdirectories hold a checkout (a `.git` directory or file), one scan indexes all of them into one index.

- Each checkout keeps its own id, build roots, modules, ignore rules and relative paths. Hidden directories and
  symbolic links are not looked at, and files of the root outside any checkout are not indexed.
- A directory inside a Git work tree is one repository, with any checkouts inside it. Fewer than two checkouts leave
  the directory walked as one repository.
- Repositories elsewhere on disk join when you register them with [`kscope workspace add`](#workspace).

| Diagnostic | Meaning |
|---|---|
| `workspace.repository-without-commit` | A checkout has no commit yet. Not indexed. |
| `workspace.duplicate-repository` | A checkout has the id of an earlier one, such as a second clone or worktree. Not indexed. |
| `workspace.files-outside-repositories` | The number of root files that belong to no checkout. |
| `workspace.repository-name-taken` | A [registered](#workspace) repository has a name that a file or directory of the root, or another repository, has. Not indexed. |
| `workspace.registered-repository-missing` | The checkout of a registered repository is gone or no longer a Git work tree. Not indexed. |
| `workspace.coordinate-version-mismatch` | A module pins a version of a coordinate that another repository builds at another version. Links across it are heuristic. |
| `workspace.coordinate-version-unknown` | A dependency on a coordinate another repository builds has a missing, dynamic or ranged version, so it cannot be compared. |
| `workspace.coordinate-ambiguous` | Several modules of other repositories publish the coordinate, so none is linked. |

`impact`, `related`, `analyze` and `context` follow links between repositories of these kinds:

| Link | What is matched |
|---|---|
| Maven coordinates | A dependency and the module of another repository that publishes it. Heuristic when the version is computed. |
| JAR files | A `files(...)` or `fileTree(...)` dependency and the module that builds a JAR of that name. Exact when a built JAR's classes match the sources. A name several modules build links none. |
| Native libraries | `System.loadLibrary`, `System.load` or JNA `Native.load` and the CMake, Cargo or ndk-build target of that name. Kotlin `external` and Java `native` declarations link to their `Java_` functions in C, C++ and Rust. |
| Code from a spec | An OpenAPI Generator `inputSpec` or Wire `sourcePath` and the spec or proto files it names. An OpenAPI operation also links to the Ktor route with the same method and path. |
| Web bundles | A `processResources`, `Copy` or `Sync` `from(...)` path and the npm package or Kotlin/JS or Wasm module whose build writes there. After a build, bundle files link to the sources their source map names. |
| Built archives | JAR and AAR files in a folder you register with [`workspace artifacts`](#workspace-artifacts). |

#### Paths in a workspace of several repositories

A file is named by its workspace path: the directory of its repository, a slash and its path in the repository, such
as `library/model/src/main/kotlin/Record.kt`.

- Text and Markdown output print workspace paths. JSON keeps `{repository, path}` with the path relative to its
  repository; the `repositories` field of [`info`](#info) gives each directory.
- Arguments that name a file (`analyze`, `symbol`, `impact`, `related`, `graph --node`) take a workspace path, with
  `:line` where a location is allowed. A path without the directory names no file: the command lists the workspace
  paths it can mean and exits with 1.
- In a workspace of one repository the directory is empty and paths are as in the repository.

### `--trust-build`

The same as `--level exact`: the scan runs the Gradle build of the workspace to read its exact build model. **Pass it
only for workspaces you trust.** Reading the model executes everything a Gradle configuration executes:

- the settings and build scripts;
- the code of included builds and `buildSrc`;
- initialization scripts in the Gradle user home;
- downloads of the Gradle distribution, plugins and dependencies.

No Gradle tasks run. The build uses the distribution its wrapper declares (a build without a wrapper is not run) and
the JVM arguments of its `gradle.properties`. The model gives projects and plugins, Android namespaces, flavors and
variants, Kotlin Multiplatform targets and source sets, and the declared dependencies and compile classpath of every
source set. It is cached under `.kscope` against the build inputs, so an update with unchanged inputs does not run
Gradle again; a first run takes ten seconds to a minute.

**Which JVM runs the build.** The JVM that runs `kscope`. For a build that needs a newer JDK, run `kscope` with that
JDK or set `KSCOPE_GRADLE_JAVA_HOME` to its home directory. If the build fails to configure, or that variable is not a
directory, the scan reports a warning and falls back to the model read from files.

**Without it,** nothing from the workspace runs. Modules and source sets are read from settings scripts, build scripts
in either DSL, version catalogs, convention plugins of included builds and the directory conventions of Android, Kotlin
Multiplatform and JVM projects.

| What the files state | How it is reported |
|---|---|
| A literal declaration | Exact. |
| Derived from a convention, template or convention plugin | Heuristic. |
| Unknowable without running the build: a computed `include`, a conditional or computed plugin, computed flavors, plugins that a library plugin applies, a custom source set hierarchy | A diagnostic, never a guess. Computed flavors are the exception: unknown source set directories are taken as flavors and labelled heuristic. |

## `trust`

Trusts the workspace to run its own build on this machine, or stops trusting it.

```
kscope trust [--revoke]
```

A scan you start with `--level exact` is your own request and needs no trust. Trust lets scans without that option run
the build too: those that `level = "exact"` in `kscope.toml` or an `exact` index ask for, `serve --watch` and the
[service](#service). `--revoke` removes the record, and later scans do not run the build unless you type
`--level exact`.

- **Where it is kept.** In `trust.toml` in the [service directory](#the-files-of-the-service), a file of yours, by the
  real path of the workspace. Moving the workspace needs a new `kscope trust`.
- **A `kscope.toml` cannot grant trust.** It can ask for level `exact`; only this command, on this machine, allows it.
- An unreadable `trust.toml` trusts nothing, and `trust` refuses to change it (exit 1). `kscope info` shows whether
  the workspace is trusted (`settings.level.trusted` in JSON).
- `trust` is not offered through a server, the service or `kscope mcp`.

## `workspace`

Registers repositories that live elsewhere on disk with the workspace, or unregisters or lists them.
[`workspace artifacts`](#workspace-artifacts) does the same for folders of built JAR and AAR files.

```
kscope workspace add LOCATION [--name NAME] [--ref REF] [--dir DIR]
kscope workspace remove NAME
kscope workspace list
```

A registered repository is scanned as one more repository of a
[workspace of several repositories](#workspaces-of-several-repositories), and its files print under its name, such as
`native-core/src/checksum.cpp`. Use it for repositories that build something the workspace uses: a library, a native
library, a web bundle or a shared API spec.

| Option | What it does | Default |
|---|---|---|
| `LOCATION` | A directory anywhere in the repository's work tree (its top is registered), or a Git URL, cloned with your Git. Required. | |
| `--name NAME` | The name its files print under: letters, digits, dots, hyphens, underscores, no leading dot. | The folder name of the checkout or URL, with `-2`, `-3` added if taken |
| `--ref REF` | With a Git URL: the branch or tag to check out. | The default branch |
| `--dir DIR` | With a Git URL: the directory to clone into. | A directory beside the workspace root |
| `NAME` (`remove`) | The name as `workspace list` prints it. Required. | |

- **What can be registered.** A Git work tree with at least one commit, outside the workspace.
- **Where it is kept.** In `workspaces.toml` in the [service directory](#the-files-of-the-service), by the real path of
  the workspace. Nothing in the workspace or the checkout changes, and nothing is built.
- **When it takes effect.** At the next `kscope scan`, also for a server or the service. `remove` leaves the checkout on
  disk and the next scan drops its files from the index.
- **Refusals,** with exit 1 and no change: a directory that is not a work tree, a work tree without a commit, a
  checkout inside the workspace or containing it, a name in use, a failed clone, a `remove` of an unknown name, and any
  change while `workspaces.toml` cannot be read.

### `workspace artifacts`

Registers folders of built archives, such as a Maven local repository or a Gradle cache, that a scan reads to link a
module to the module of another repository that builds what it depends on.

```
kscope workspace artifacts add (DIR | --maven-local)
kscope workspace artifacts remove DIR
kscope workspace artifacts list
```

kscope never reads such a folder on its own, so an index does not depend on the machine it was made on and never comes
near the credentials these places can hold. After you register one, a scan reads only the archives of the coordinates
that the build files of the workspace name.

| Option | What it does |
|---|---|
| `DIR` | A folder that exists: a Maven local repository, a Gradle user home (`~/.gradle`), its `caches/modules-2` or `files-2.1`, or any folder laid out like them. Your shell expands `~`; kscope does not. |
| `--maven-local` | Registers `.m2/repository` in your home directory (the Java `user.home`) instead of a `DIR`. |

- **What is read.** A dependency on `group:artifact:version` with a literal version that no module of another
  repository publishes is looked up as `artifact-version.jar`, then `.aar`, in the Maven and the Gradle cache layouts.
  When a module of another repository builds an archive of that name, it is compared with the module's sources and
  the dependency links to it: exact when the classes match, heuristic otherwise. A module whose archive is in no
  folder is reported as `artifact.output-missing`, and several modules building it as `artifact.ambiguous-producer`. See
  [ADR 0020](decisions/0020-links-through-built-artifacts.md).
- **Order and storage.** Folders are looked in the order registered, which `list` prints; a folder registered twice
  counts once. They are kept in `workspaces.toml` as absolute paths and take effect at the next scan.
- **Refusals** (exit 1, no change): a path that is not a directory, a `remove` of a folder that is not registered, and
  any change while `workspaces.toml` cannot be read. A `DIR` together with `--maven-local`, or neither, exits with 2.

`workspace` and `workspace artifacts` are not offered through a server, the service or `kscope mcp`.

## `components`

Installs optional, versioned components in your user directory, or removes or lists them. Install the embedding runtime
with `kscope components install semantic --yes` and the CodeRankEmbed model with `kscope components install semantic-model --yes`.
Both support Windows x64, Linux x64, Linux aarch64 and macOS arm64.

```
kscope components install NAME [--yes] [--from DIR|FILE]
kscope components remove NAME
kscope components list [--format text|json]
kscope components status [--format text|json]
```

| Option | What it does | Default |
|---|---|---|
| `NAME` | A component named in this release's manifest (`install`), or installed on this machine (`remove`). | Required |
| `--yes` | Approves installation without prompting. Required when standard input is not a terminal. | Off |
| `--from DIR\|FILE` | Reads pinned files from their relative paths in DIR, or one ZIP FILE for a component with exactly one applicable archive. Uses no network. | Manifest HTTPS URLs |
| `--format text\|json` | Lists manifest entries or installed components as text or JSON. | `text` |

- **Before downloading,** `install` prints the component's name, version, licence, total size and URLs, then asks
  `Download? [y/N]`. Non-terminal input without `--yes` exits with 2 after showing that information.
- **Verification.** Files must match the manifest's byte counts and SHA-256 hashes. Mirrors are tried in order.
  ZIP entries are checked for escapes and written as plain files, so an archive can never create a link.
  Expanded bytes are limited to eight times the archive size. Failure discards
  staging and leaves an existing installation intact. Archives are removed after extraction.
- **Semantic self-test.** Installing `semantic` or `semantic-model` tests the worker when both are installed. `install`
  starts the installed worker, requires its handshake and one small embedding within the provider's existing
  deadlines, then stops it. The result line says `Semantic worker answered.` on success. On failure, installed files
  are kept, the command exits with 1, and the result includes the reason and the Windows requirement: a JDK 17 whose
  bundled Microsoft Visual C++ runtime is 14.40 or newer, for example Temurin 17.0.21 or newer. `list` and `status`
  show the installed component with its failed self-test as problem text; reinstalling or running `doctor` retries it.
- **Where files live.** `~/.kscope/components/NAME/VERSION/`, overridden by `KSCOPE_COMPONENTS_HOME` (or the
  `kscope.components.home` Java property, which wins). Only directories carrying a matching `kscope-component.json`
  marker can be replaced or removed; links are not followed.
- **Listing.** `list` shows each manifest entry's name, version, licence, platform download size and state:
  `installed`, `not installed`, `disabled by workspace`, `other version installed: X` or `unsupported platform`.
  `status` shows installed components with their directories and whether their versions match this release's
  manifest, including components that this release no longer lists.
- **Workspace switches.** In `kscope.toml`, `[components]` with `NAME = false` disables that component for the
  workspace. Values must be booleans; nested or malformed keys are refused. Disabling does not delete installed files.
- **Network.** Installation is explicit; analysis does not fetch components. See
  [ADR 0022](decisions/0022-optional-components.md), which amends ADR 0020's network consequence.

`components` is not offered through a server, the service or `kscope mcp`.

## `context`

Compiles the files and declarations worth reading for a task from the index and the current files, cut to a token
budget. Run `scan` first.

```
kscope context [TASK] [--task-file FILE] [--budget TOKENS] [--level minimal|recommended|deep|full]
               [--estimator o200k_base|cl100k_base|chars]
kscope context "greet a person by name" --budget 4000 --format markdown > context.md
```

The text format summarizes what was chosen, `--format markdown` prints the context itself for a prompt, and
`--format json` prints the fragments with their evidence, source ranges, token counts and omissions with reasons.

| Option | What it does | Default |
|---|---|---|
| `TASK` | What you want to do, in your own words. Give it or `--task-file`, never both. | |
| `--task-file FILE` | Reads the task from a UTF-8 file. | |
| `--budget TOKENS` | The most tokens the whole rendered Markdown may use, including task text, headers, fences and warnings. A nonnegative integer; zero gives no text. The JSON envelope is not limited. | None: the level decides |
| `--level LEVEL` | How much to include; see below. | `recommended` |
| `--estimator NAME` | How tokens are counted: `o200k_base`, `cl100k_base`, or `chars` (four characters per token, reported as `chars_4`). | `o200k_base` |

| Level | Selection |
|---|---|
| `minimal` | The candidates that cover 50% of the ranked relevance. |
| `recommended` | 80% of the relevance, plus boundary endpoints and selected test signatures. |
| `deep` | `recommended` plus direct neighbors and test bodies. |
| `full` | Every relevant file in full, within the budget when one is given. |

- **Budgets below 8,000 tokens** use a compact policy at every level: raw source files and declarations in rank order,
  a line prefix filling the remaining space, and no signatures, outlines, summaries or warning lines. Files with the
  `test` role are left out below `deep`, unless ranked as a file to edit.
- Generated bodies are excluded; generated signatures keep their source-of-truth provenance.

## `analyze`

Answers two kinds of question, and its first line says which one it understood.

```
kscope analyze [TASK] [--task-file FILE] [--as task|symbol] [--stage full|lexical]
```

- **A task,** in your own words: the files, modules and tests it is likely to touch, ranked by how well the task
  matches names, code and relationships, with which to edit and which to read.
- **A name or a file:** a card with the declaration, where it sits, what it depends on, what refers to it, its tests
  and what a change to it affects.

| Option | What it does | Default |
|---|---|---|
| `TASK` | A task, or a name or file to describe. Give it or `--task-file`. | |
| `--task-file FILE` | Reads the task from a UTF-8 file. | |
| `--as task` | Analyzes the argument as a task. | Guessed |
| `--as symbol` | Looks the argument up as a name. It never falls back; with no match it prints `Nothing in the index is named ...`. | Guessed |
| `--stage full\|lexical` | Selects task retrieval. `lexical` returns the lexical ranking before structural fusion or semantic signals; `full` runs the complete pipeline. Applies to task analysis and name fallback. | `full` |

Task-analysis JSON includes `stage: "lexical"` only for lexical analysis; full analysis omits `stage`. Lexical
analysis reports `semantic.state` as `"off"`, including with `--ml on`. Text, JSON and context estimates use the selected ranking.

### Task or name

The argument is read as a name when it is one token, without a space, and any of these holds:

- It is CamelCase or snake_case: it has an upper-case letter or an underscore (`TaskRepository`, `task_repository`).
- It is a qualified name: identifiers joined by dots (`sample.data.TaskRepository`, `TaskRepository.save`).
- It is a file of the workspace, with an optional line (`data/db/TaskRepository.kt:14`).

Everything else is a task, including a single plain word such as `reminder`. A guessed name that matches no declaration
and no file is analyzed as a task after all, and the first line says so. `--format json` is always the task analysis
and refuses `--as symbol`; use `symbol` or `impact` with `--format json` for data about a name.

### Task analysis

```
Read as a task (use --as symbol to look it up as a name).
3 files to edit and 25 to read, touching 2 modules; 122 files scored; several close candidates.

Files to edit (3)
  score  file                                 because
  0.129  data/settings/.../UserPreference.kt  lexical artifact, lexical symbol
```

The headline counts what is relevant, not what was scored. A file is **to edit** when the ranking puts it above the
largest drop in score among the best files, and **to read** otherwise. The other sections hold the query terms, tests
to run, documents, the predicted scope of the change, gaps and the estimated context size at each level.

#### Relevance cut-off

A ranked list shows only the items that pass one rule, using the scores of the result alone: an item is relevant when
its score is at least 30% of the best score, and the three best always are. For `symbol` and `related` the node you
asked about (an exact match, the node itself, the file that declares it) is always listed and the 30% is taken of the
best of the others. A list longer than its row limit says `showing 10 of 25` and ends with `15 more relevant`; items
below the cut-off are counted in one line. `--detail full` lists every relevant item and `--format json` everything
scored.

**How sure the ranking is.** The end of the headline describes this ranking's scores, not a probability: `one clear
match` (the second best file scores at most 65% of the best), `no strong match` (otherwise, and 10 or more files score
at least 80% of the best), or `several close candidates`. The `confidence` field of the JSON result counts agreeing
signals and is a different measure.

**Documents and binary files.** Binary files (a zero byte in the first 8 KiB), such as images, never appear under
files to edit or read; those that pass the cut-off are counted in one line. Files classified as documentation, today
Markdown, have their own `Documents` section. Neither counts in the headline.

### Name or file

The card shows kind, declaration, signature, module and source set, then `Depends on`, `Referred to by`, `Tests to run`
and an `Impact` line (the headline of [`impact`](#impact)).

- `Depends on` holds what the declaration and its nested declarations point to; `Referred to by` holds what points to
  them. Both are grouped by node with every relation, closest kinds first (`extends`, `implements`, `overrides`,
  `type-uses`, `calls`, `references`, `imports`). For a file the card adds `Declares`.
- A name that several declarations share is not guessed: the card shows the first by symbol id, the headline says there
  are several, and `Other declarations with this name` lists the rest with the `path:line` that picks each, as in
  `kscope analyze path/File.kt:42`. The exit code is 0.

### Output size

`--detail` and `--sections` apply to `analyze`, `symbol` and `related` in text and markdown. A section name that the
reading does not have exits with 2 and lists the available ones.

| `--detail` | Prints |
|---|---|
| `brief` | One screen: the headline, the five best candidates, the number of tests and the next step. For a name, the declaration. |
| `standard` | Every section, at most 10 rows per list (15 for `symbol`). |
| `full` | Every section with every relevant item and no row limit, still cut at the relevance cut-off. |

`--sections a,b` prints only those sections, in their usual order; the first line belongs to `headline`. For example,
`kscope analyze "..." --sections tests` prints the tests to run and nothing else. `top` is the short list `brief`
shows; `standard` does not print it.

| Command | Section names |
|---|---|
| `analyze`, a task | `headline`, `top`, `query`, `edit`, `read`, `documents`, `cutoff`, `tests`, `scope`, `gaps`, `connectedness`, `context`, `next` |
| `analyze`, a name or file | `headline`, `declaration`, `depends-on`, `referred-by`, `tests`, `impact`, `alternatives`, `next` |
| `symbol` | `headline`, `best`, `matches` |
| `related` | `headline`, `files`, `symbols`, `support` |

## `info`

Summarizes the index without changing it, or with `--structure` describes the directory before anything is indexed.

```
kscope info [--structure [--structure-depth N]]
```

It shows the counts of files, modules, symbols and relationships, and the `Settings` in effect (see
[Settings file](#settings-file)). A workspace that was never scanned exits with 1. Relationships are counted by
relation kind, evidence class and resolution state (`exact`, `probable`, `heuristic`, `unresolved`, `not-analysed`). A
workspace of several repositories lists each with its directory, short id and module count.

| Option | What it does | Default |
|---|---|---|
| `--structure` | Prints the structure report instead of the index summary. | Off |
| `--structure-depth N` | With `--structure`, how many directory levels below the workspace to inspect, 0 to 20. | 4 |

### Structure report

Says what kind of directory the workspace is, with the evidence for each statement. It needs no index, creates nothing,
reads file names, `.git` entries and the text of Gradle settings scripts, asks your Git which paths its ignore rules
cover, and runs no project code or Gradle scripts. The JSON follows `structure.schema.json` in the
[schemas](schemas/README.md).

- **Git.** The checkout that holds the directory (a `.git` directory is a repository, a `.git` file a linked worktree
  or submodule) and the other worktrees of the same repository.
- **Gradle builds.** Every directory with `settings.gradle` or `settings.gradle.kts`: *top-level*, an *included build*
  (named by `includeBuild` in code, not in a comment or string) or a *nested independent build*.
- **Other projects and nested checkouts.** `package.json`, `Cargo.toml`, `pyproject.toml`, `go.mod`, `pom.xml`,
  `Package.swift`, and directories below the workspace with a `.git` entry.

| Conclusion | When |
|---|---|
| a single build | One Gradle build with its included builds, or one other project. |
| a repository with several builds or projects | Independent builds and project markers add up to more than one. |
| a directory holding several repositories | More than one repository; worktrees of one repository count once, submodules with their superproject. This wins. |
| no conclusion | No marker is found. |

Inside a Git work tree, ignored directories are pruned (never the inspected directory itself) using your Git's ignore
rules, and the report counts them. If Git is unavailable the report says that ignore rules were not applied. The walk
skips `.git`, `.gradle`, `.idea`, `.kscope`, `build`, `out` and `node_modules`, follows no symbolic link and stops after
20,000 directories, saying so.

## `symbol`

Looks up declarations by name: `kscope symbol NAME`, where `NAME` is a simple or qualified name, a symbol id, or a
location such as `path/File.kt:42`.

- Exact declarations come first, then ranked matches down to the [relevance cut-off](#relevance-cut-off). The headline
  says how many matches are relevant and how many declarations were scored.
- A name that several declarations share lists each candidate with the value that picks it and exits with 2.
- `--detail` and `--sections` apply (sections `headline`, `best`, `matches`; see [Output size](#output-size)).

## `impact`

Lists what a change to a symbol, file or module can affect, nearest first, with the modules and tests involved:
`kscope impact NODE`.

- `NODE` is a path of the workspace (with its repository directory when there are several; see
  [Paths](#paths-in-a-workspace-of-several-repositories)), `path:line`, a symbol id, a qualified or simple name, or a
  module path such as `:core:model`.
- Impact follows consumers and inheritance in reverse and generated boundaries forward. Every entry has a distance and
  a certainty, which is the weakest edge on its path; another path to the same node may differ.
- A name that several declarations share lists the candidates with the value that picks each and exits with 2. A name
  that matches nothing gives an empty result and exit 0.

## `related`

Lists the files and symbols closest to a node in the graph, including collaborators that share no words with it:
`kscope related NODE`.

- `NODE` is written as for `impact`. A module brings in the files it owns.
- The lists stop at the [relevance cut-off](#relevance-cut-off); the node itself and the file that declares it are
  always listed. The query scores the 50 closest files and the 50 closest symbols, and the headline says so.
- `--detail` and `--sections` apply (sections `headline`, `files`, `symbols`, `support`).

## `graph`

Shows how the modules and files of the workspace depend on each other.

```
kscope graph [--scope modules|workspace|neighborhood] [--node NODE] [--depth N] [--limit N]
```

| Option | What it does | Default |
|---|---|---|
| `--scope modules\|workspace\|neighborhood` | `modules`: dependencies between modules. `workspace`: the whole stored graph. `neighborhood`: what surrounds `--node`. | `modules` |
| `--node NODE` | The node to centre a neighborhood on: a symbol name or id, a module path or a file path. Required for, and only with, `--scope neighborhood`. | |
| `--depth N` | How many relationships away a neighborhood reaches, 0 to 3. | 1 |
| `--limit N` | The most nodes and relationships to return, 1 to 100,000. A cut result says so. | 2,000 |

`--format dot` prints Graphviz DOT, for example `kscope graph --format dot > modules.dot`. JSON keeps the evidence class
and resolution state of every relationship, including unresolved ones.

## `serve`

Keeps the index of one workspace open with the graph in memory and answers the other commands for it, so a query
skips the start-up that is most of its time on a large workspace. For what that saves, see the
[results](benchmarks/README.md).

```
kscope serve [--watch] [--map] [--idle-timeout MINUTES]
kscope serve --status
kscope serve --stop
```

Start it, then use the other commands as usual. While a server runs for a workspace, `scan`, `context`, `analyze`,
`info`, `symbol`, `impact`, `related` and `graph` there are executed by it. Standard output and the exit code are byte
for byte what the command gives without a server, in every format; only progress lines can differ. `doctor`,
`benchmark`, `info --structure`, `--help`, `--version` and every usage error stay in your own process.

| Option | What it does | Default |
|---|---|---|
| `--watch` | Keeps the index up to date; see [Watching](#watching). | Off |
| `--map` | Also serves a live map of the workspace to a browser; see [Map](#map). Not with `--status` or `--stop`. | Off |
| `--idle-timeout MINUTES` | Stops by itself after this many minutes without a request. Automatic updates are not requests. | 0: never |
| `--status` | Says whether a server runs, with its process id, version, start time, watcher state and map address. Exit 0 when one runs, 1 when none does. After 2 seconds without an answer it says the server did not answer. | |
| `--stop` | Stops the server after the request it is running. With none running it says so and exits with 0. A workspace the [service](#service) keeps is not stopped this way (exit 1). | |

- It runs in the foreground of your terminal until Ctrl+C, `--stop` or the idle timeout. Nothing starts it for you.
- One server per workspace; a second `kscope serve` is refused. It answers one request at a time, so a query sent
  during a scan waits. Without `--watch` it does not look at your files: the index changes when you run `kscope scan`.
- **Scans through the server** run with the environment variables of the server, not of your shell
  (`KSCOPE_WORKERS`, `KSCOPE_WORKER_HEAP`, `KSCOPE_GRADLE_JAVA_HOME`). A scan cannot run beside a server, so
  `scan --no-server` while one runs, and a scan that cannot reach a server that is alive, are refused with a message
  that says to stop it.
- With no server, with `--no-server` or `KSCOPE_NO_SERVER=1`, or when the server cannot be reached, a query runs in
  your own process silently. A server of another version of `kscope` makes it run in your process and say so in one
  line on standard error; a `scan` is refused until that server stops.

### Watching

`kscope serve --watch` looks at the workspace every two seconds, waits until files stop changing, and runs the
incremental scan itself, so a query sees an edit without `kscope scan`. It writes nothing but the index.

- **It runs your build only where you trusted the workspace.** An automatic update is a scan at `syntax` or `resolved`.
  It reaches `exact` only in a [trusted](#trust) workspace whose `kscope.toml` asks for `level = "exact"`, and it reads
  the trust again at every update. Otherwise an `exact` index is not updated automatically, the build never runs, and
  queries say so.
- **It deepens by itself.** The first scan without an index runs at `syntax`. Once files are quiet, the next update
  deepens it to `resolved` (or leaves it at `syntax` if `kscope.toml` says so, or goes to `exact` after `kscope trust`).
  A scan that stays shallow, as when the build fails, is retried after 10 seconds, then at doubling intervals, five
  attempts in all. An index never becomes shallower by itself.
- **It looks, it does not listen.** It compares the file list a scan would read, with sizes and modification times; it
  reads no file content and uses no operating-system file watcher. In a Git work tree each look runs one
  `git ls-files`, using at most about 5% of one processor.
- **It waits for quiet and for Git.** An update starts when files were the same for two looks at least three seconds
  apart. During a rebase, merge, cherry-pick, revert or bisect, or while Git holds its index lock, nothing updates.
- **It is bounded.** A query that arrives during an update waits for it. After a failed update it waits 10 seconds,
  doubling up to 5 minutes, and after five failures in a row it stops watching until the server restarts.

While the index is behind the workspace, a query's standard error ends with one line such as
`Index is behind: a Git operation is in progress.`; standard output and the exit code do not change. The reasons are:
changes are settling; a Git operation is in progress; the index is shallower than wanted and is being deepened; the scan
did not reach the wanted level (for instance the build failed); the index is `exact` and not updated automatically; an
update failed and is retried; the watcher gave up. `kscope serve --status` adds a watcher line: up to date, behind with
the reason, or stopped.

### Map

`kscope serve --map` also serves a map of the workspace to a browser. Its address, with a token, is printed at the start
and by `--status`. The map shows repositories, modules, source sets, directories, files and symbols with the
relationships between them by kind and certainty, including unresolved ones. It answers what a change to a file,
module or symbol reaches and which files a task points at, using the code of [`impact`](#impact) and
[`analyze`](#analyze), and it follows the index when a scan or the watcher updates it. `kscope service` does not offer
it. What the page reads is not a stable interface; see [ADR 0021](decisions/0021-browser-map.md).

- It is read only, has no form, runs no command, loads nothing from a network and uses no cookie. It listens on
  `127.0.0.1` only, on a port the system picks, and serves at most 32 connections at a time.
- The address holds a random 256-bit token, new at every start and written to no file. Other paths get 404, other
  methods than `GET` get 405, and a `Host` other than `127.0.0.1` or `localhost` with the map's port gets 403, so a
  web page cannot reach it through another name.

### Safety of the server

- It listens on the loopback address `127.0.0.1` only. There is no option to listen anywhere else.
- At start it creates a random 256-bit token and writes it, with its port and process id, to `.kscope/serve.json`. It
  answers only requests that carry the token; anything else is closed without an answer.
- Whoever can read `serve.json` can use the server as you, so only your account can: mode `rw-------` on Linux and
  macOS, an access control list for your account alone on Windows. Where neither can be set, `kscope serve` refuses to
  start rather than write a token others could read.
- It runs only the commands listed above, with the same code as the command line, never anything a request names. A
  request is one line of at most 1 MiB that must arrive within 10 seconds; at most 16 connections are read at once.
- It writes only what the commands write: the index during a scan, and `serve.json` and `serve.lock` in `.kscope`.
  `serve.json` is deleted when the server stops; one left by a crash is ignored.

## `service`

Keeps several workspaces served by one process of yours, each as `kscope serve --watch` would. The `kscope` client and
`kscope mcp` find its servers through `.kscope/serve.json` of each workspace, as they find one you started by hand.

```
kscope service add|remove [--workspace DIR]
kscope service start|stop|run [--log]|status|page|install|uninstall
```

You choose workspaces with `add` and `remove` (kept in `service.toml`) and start the service in the background with
`start` or in the foreground with `run`.

| Subcommand | What it does | Exit code |
|---|---|---|
| `run [--log]` | Runs in the foreground until `kscope service stop` or Ctrl+C, printing state changes on standard error. `--log` writes them to `service.log` instead. Refused if a service of yours runs. | 0 after a stop, 1 when refused |
| `start` | Runs `run --log` as a process of its own, with the Java and `kscope` you ran, detached from the console on Windows or in its own session on Linux and macOS when `setsid` is on `PATH`, and returns when it runs. Without `setsid`, Linux and macOS redirect streams through a shell and keep the caller's session, but ignore hangup when `nohup` is on `PATH`. After 30 seconds without that it ends it, prints `The service did not start; see <service.log>.` and exits with 1. A `memory-mib` in `service.toml` becomes the maximum heap at the next `start`. | 0, 1 when it does not run in time |
| `stop` | Every workspace server finishes its request and closes, and the process ends. | 0, 1 when it does not answer |
| `status` | Says whether it runs, what it does with each workspace and whether it starts with your session. | 0 when it runs, 1 when not |
| `page` | Prints the address of the [status page](#status-page). | 0, 1 when it does not run or answer |
| `add [--workspace DIR]` | Adds the workspace to `service.toml` and tells a running service to take it up. The directory must exist and defaults to the current one. | 0, 1 when a running service does not answer |
| `remove [--workspace DIR]` | Removes the workspace and tells a running service to stop serving it. The directory may be gone. | 0, 1 when a running service does not answer |
| `install` | Registers one entry that starts the service with your session. Does not start it now. | 0, 1 when it cannot register |
| `uninstall` | Removes the entry and what `install` wrote, and stops the service if it runs. `service.toml` stays. | 0, 1 on failure |

A service that is alive but does not answer is not one that does not run: `add`, `remove` and `stop` then say so on
standard error and exit with 1 (`add` and `remove` have written the file by then).

### Status

`kscope service status` prints a line for the service, its limits (`Limits: idle 30 minutes, memory none.`), whether it
starts with your session, and a line for each workspace in the order of `service.toml`:

```
/work/app: served. Watcher: up to date; last update 2026-10-11T08:31:02.554Z; 3 automatic updates.
/work/old: not served, because the directory does not exist.
```

- `served`: with what the watcher says ([Watching](#watching)) and `Index level: <level>.` `served, not in memory` means
  the graph was [released](#releasing-memory).
- `starting`: its server is opening the index.
- `waiting`: a `kscope serve` or `kscope mcp` of yours serves it. The service retries every 30 seconds.
- `not served`: with the reason. It retries after 30 seconds, then at doubling intervals up to 10 minutes. `add` and
  `remove` make it retry at once.

If the session entry is stale (the Java or `kscope` it names moved or is gone, or the Linux unit is not enabled),
`status` says to run `kscope service install` again and does not repair it.

### Status page

`kscope service page` prints the address of a page that shows the service (version, process, start time, limits, heap
in use) and for each workspace its state, index and level, whether the graph is in memory, the last scan the service
ran, the watcher, the settings in effect and errors such as an invalid `kscope.toml`. It changes nothing and has no
form, script, link or external file. It is HTML only; `kscope service status` and `kscope info` are the machine-readable
views. It listens on `127.0.0.1` on its own port, the address holds a random token that is new at every start, other
paths get 404, only `GET` is accepted, and a wrong `Host` gets 403.

### Releasing memory

A served workspace holds its decoded graph in memory. The service releases a workspace when it has had no request for
the idle limit (automatic updates are not requests), or when the heap in use after a garbage collection is above the
ceiling, least recently used first. Only the graph leaves memory; the index on disk, the server and the watcher stay.
The next query loads the graph again, so it takes longer but prints the same output and exit code. A release never
interrupts a request or a scan.

### Starting with your session

`kscope service install` registers one entry that starts the service when you log in. It needs no administrator rights
and does not start the service now. Run it again after `kscope` or Java moved, or after `memory-mib` changed.

| System | What is registered |
|---|---|
| Windows | The value `KrosScope` in `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`. A command line over 260 characters registers nothing; use a shorter service directory. |
| Linux | The enabled systemd user unit `kscope.service` in `~/.config/systemd/user`. Without a systemd user manager, `install` exits with 1 and says to use `start`. |
| macOS | The launch agent `io.github.krosstack.kscope.plist` in `~/Library/LaunchAgents`, which starts at your next login. |
| Any other | Nothing; `install` and `uninstall` exit with 1 and say to use `start`. |

The service started this way has the environment of your session, not of your shell, so `KSCOPE_WORKERS`,
`KSCOPE_WORKER_HEAP` and `KSCOPE_GRADLE_JAVA_HOME` from a shell profile do not reach it. `uninstall` leaves
`service.toml` and `service.log`. `install` and `uninstall` are the only commands that register or remove anything with
the operating system. Windows `start` has been verified to survive closing its launching console; Windows startup at
login remains unverified. Session startup is implemented but not yet verified on a real session on Linux or macOS.

### The files of the service

They live in one directory: the system property `kscope.service.home`, else `KSCOPE_SERVICE_HOME`, else
`.kscope/service` in your home directory.

| File | What it holds |
|---|---|
| `service.toml` | The workspaces the service keeps and its two limits. You can edit it. |
| `trust.toml` | The workspaces you trusted, by real path. Written by [`trust`](#trust). |
| `workspaces.toml` | Registered repositories and artifact folders per workspace. Written by [`workspace`](#workspace) and [`workspace artifacts`](#workspace-artifacts). |
| `scanned-checkouts.json` | The registered checkouts the last scan indexed, so a removed repository stays readable until the next scan. |
| `service.json` | Process id, port, token and version of the running service. Deleted on stop; one left by a crash is ignored. |
| `service.log`, `service.log.1`, `launch.args` | The log of a service started with `--log` (each line starts with the UTC time; the file starts anew with the service and moves to `service.log.1` at 1 MiB), and the arguments of the session entry. |

`service.toml` has three keys, and `add` and `remove` keep them:

| Key | What it does | Default |
|---|---|---|
| `workspaces` | A list of absolute paths with `/` as the separator: the workspaces to keep. | None |
| `idle-minutes` | A whole number of minutes. A workspace with no request for this long is [released](#releasing-memory). 0 never releases for idleness. | 30 |
| `memory-mib` | A whole number of MiB: the ceiling for the heap of the service. The service also releases when the heap in use after a collection is above 80% of the smaller of this and the maximum heap, checked every 10 seconds. 0 means no ceiling of its own. | None |

- `add` and `remove` write the list sorted and without duplicates. A running service reads the file again after each,
  so a hand-edited limit applies then or at the next start. Entries are compared as the directory they name.
- A file that cannot be read, an unknown key, a wrong type, a negative number or a syntax error is reported with the
  file and line, and the command exits with 1. A running service then keeps its workspaces and limits.

### What the service is and is not

It is the same server as `serve --watch` for each workspace, with the same output and exit codes, and one scan at a
time in the whole service. A workspace it keeps cannot be stopped with `kscope serve --stop`: use `service remove` or
`service stop`. It runs the build of a workspace only if you trusted it with [`kscope trust`](#trust) and it is, or asks
to be, indexed at `exact`; in any other workspace no code of the workspace runs. It listens on `127.0.0.1` only and
answers only requests that carry the token in `service.json`, which only your account can read, with the limits of the
[server](#safety-of-the-server); it refuses to start where that file cannot be protected. The status page is the one
other thing it serves, on a second loopback port.

## `mcp`

Offers the commands as tools to a coding agent over the [Model Context Protocol](https://modelcontextprotocol.io).
Agent hosts start it as a child process and talk to it over standard input and output; every tool runs one of the
commands of this page and returns what it prints.

```
kscope mcp [--watch] [--no-server]
```

| Option | What it does | Default |
|---|---|---|
| `--watch` | Keeps the index up to date in the embedded server; see [Watching](#watching). | Off |
| `--no-server` | Starts and uses no server; every tool opens the index itself. | Off |

`kscope mcp` starts the server of [`kscope serve`](#serve) inside its own process when none runs for the workspace, and
stops it when the host closes standard input. When a server already runs, it is used.

### Setting it up

Run `kscope scan` once, then tell the agent host to start `kscope mcp`. Claude Code reads `.mcp.json` at the root of the
workspace:

```json
{ "mcpServers": { "kscope": { "command": "kscope", "args": ["mcp", "--watch"] } } }
```

Codex reads `~/.codex/config.toml`:

```toml
[mcp_servers.kscope]
command = "kscope"
args = ["mcp", "--watch", "--workspace", "/path/to/workspace"]
```

`kscope` has to be on the `PATH` of the host, or `command` has to be the full path of the launcher. A host that does
not start the server in the workspace directory needs `--workspace`.

### The tools

| Tool | Arguments | What the agent gets |
|---|---|---|
| `context` | `task`; `budget` (500 to 200,000 tokens, optional) | The source worth reading for the task, as Markdown. |
| `analyze` | `query`; `detail` (`brief`, `standard` or `full`, optional); `ml` (boolean, optional, default `true`), forwarded as `--ml on\|off`; `stage` (`full` or `lexical`, optional, default `full`), forwarded as `--stage full\|lexical` | The files, modules and tests a task touches, or the card of one name or file. |
| `symbol` | `name` | The declarations that match a name. |
| `impact` | `node` | What a change to a symbol, file or module can affect. |
| `related` | `node` | The files and symbols closest to a node. |
| `info` | none | What the index holds. |
| `scan` | none | Brings the index up to date at level `resolved`, deepening an index at `syntax`. |

A result is one block of text: what the command prints on standard output, in Markdown where it has that, followed by
any standard error line such as `Index is behind: ...`. A failing command gives a tool error with its message.

### What an agent can and cannot do

- **It cannot make KrosScope run build code.** The `scan` tool takes no arguments, so `--level`, `--trust-build`,
  `--rebuild` and every other option cannot be passed. It always scans at `resolved`, never runs the build, even in a
  trusted workspace, and on an `exact` index it is refused and names the command for you to run.
- **Arguments are data.** A task or name is passed as one argument after `--`, so text that starts with `-` or `@` is
  still the task. Undefined properties are rejected, strings are at most 20,000 characters, and the budget must be a
  whole number in range.
- **It reads and it scans, nothing else.** No tool writes to the workspace, runs a program or reads a file by path.

### Protocol

Messages are JSON-RPC 2.0, one per line. Standard output carries protocol messages only; progress and diagnostics go to
standard error. The only capability is `tools`. Revision `2026-07-28` and the earlier ones that open with `initialize`
(`2024-11-05` to `2025-11-25`) are served. Requests are answered in order; `notifications/cancelled` interrupts the
running call; a request beyond 64 waiting ones is answered at once with error `-32000`.

## `skill`

Installs the skill that teaches coding agents to use kscope, removes it, or prints it.

```
kscope skill install [--dir DIR]...
kscope skill uninstall [--dir DIR]...
kscope skill show
```

A skill is a folder of instructions, led by `SKILL.md`, that an agent loads when a request calls for it. The kscope
skill says when to use kscope, which commands to run, how to read their results, and what an agent must not run on its
own: the project's build (`--level exact`, `--trust-build`, `trust`) and `service install`. It ships inside the tool,
and every install writes the version that matches it. The [installers](install.md) run `skill install`.

| Agents | Found by | Skill folder |
|---|---|---|
| Claude Code | `~/.claude` | `~/.claude/skills/kscope` |
| Codex, Gemini CLI, GitHub Copilot and others that read the shared folder | `~/.agents`, `~/.codex`, `~/.gemini` or `~/.copilot` | `~/.agents/skills/kscope` |

| Option | What it does |
|---|---|
| `--dir DIR` | Installs into (or removes from) these skill folders instead, for example a project's `.claude/skills` or `.agents/skills`. Repeat it for several. |

- Running `install` again updates the skill. With no agent found it writes nothing and says so.
- A folder named `kscope` that holds something else is left alone: the command says so and exits with 1.
- `uninstall` removes the skill only where it holds the kscope skill. `show` prints `SKILL.md`, for example to paste
  into an agent that reads no skill folder.

## `doctor`

Checks the JDK and its compiler (17 or newer), the Kotlin analysis worker, Git, the Android SDK directory and the
index, and reports the running JDK's vendor and full version and the text encodings in use: `kscope doctor`.

- It runs `git --version` and never runs workspace build code. The worker and SDK checks look at installation paths;
  they do not start the Kotlin worker or validate an SDK platform. When the semantic worker and model are installed,
  an additional line reports the same bounded handshake and small-embedding self-test used by `components install`,
  including the failure reason and JDK requirement when it fails, with installation and JDK paths in the reason shown
  as `<components>` and `<jdk>`.
- A missing optional component shows as `missing` with a remedy and the exit code stays 0. An unreadable index or
  another I/O failure exits with 1. The JSON result holds no environment or installation paths.

## `benchmark`

A developer tool that scores file localization against tasks with known answers. It reads the existing index.

```
kscope benchmark --tasks DIR [--held-out PATH] [--original-only] [--out DIR]
```

Task files are JSON with `id`, `statement`, `mustEdit` and optional `mustRead`, `symbols`, `relevantTests` and
`changeScope`. It compares localization with raw-text BM25, BM25 with English stop words, lexical retrieval and path
order, and reports ablations. See the [benchmark policy](benchmark.md) and the [results](benchmarks/README.md).

| Option | What it does |
|---|---|
| `--tasks DIR` | Directory of task JSON files. Required. |
| `--held-out PATH` | A file, or a directory with `statements.json`, of `{id, statement}` records paired with the unchanged gold. |
| `--original-only` | Reports only the original statements. Not with `--held-out`. |
| `--out DIR` | Also writes `localization.json` and `localization.md` to this directory. |

`--format markdown` prints the full report and `--format json` the data. Exit code 1 means the corpus is invalid or the
recall@5, recall@10 and NDCG@10 improvement gate failed on either set against raw or English-stop-word BM25; the
combined gate stays incomplete until both sets are measured.

## Text encodings

Standard output and standard error are written as UTF-8 when redirected or captured, whatever the platform default, so
JSON and Markdown saved with `>` are valid UTF-8 on Windows too; on a console they use the character set of the
console. `kscope doctor` reports the encodings in use.

Command-line arguments are different. On Windows with JDK 17 the Java launcher converts arguments with the system code
page, and characters outside it, including emoji, can be replaced before `kscope` receives them. Pass such text
through a UTF-8 file:

```
kscope analyze --task-file task.txt --format json
kscope context --task-file task.txt --budget 4000 --format markdown
```

A leading byte-order mark is removed, and invalid UTF-8 is an input error (exit code 1).

## Settings file

A `kscope.toml` in the root of the workspace holds the settings of a scan. It is data, so it can be committed and
shared. It sets which files a scan reads, which analyzers run, how many Kotlin workers analyse at once and how much
memory each gets, and how the service keeps the index fresh.

| Key | Type and range | Default | What it does |
|---|---|---|---|
| `format` | Integer, only `1` | `1` | The version of the file format. |
| `level` | `"syntax"`, `"resolved"` or `"exact"` | `"resolved"` | How deep a scan goes; see [Levels](#levels). `exact` runs the build only in a workspace you [trusted](#trust); otherwise the scan runs at `resolved` and says so. |
| `scan.include` | Up to 256 patterns | Empty: every file | The paths a scan reads. Build files stay readable outside it. |
| `scan.exclude` | Up to 256 patterns | Empty | Paths a scan does not read, whatever `include` says. |
| `scan.tests` | Boolean | `true` | `false` leaves out the files of source sets whose role is not main (unit tests, instrumented tests, test fixtures). |
| `scan.generated` | Up to 256 patterns | Empty | Files that count as generated: they stay in the index but are not analysed. |
| `analyzers.android-resources` | Boolean | `true` | `false` switches off Android resources, the manifest, the `R`, view binding and `BuildConfig` stubs and their links. |
| `analyzers.aidl` | Boolean | `true` | `false` switches off AIDL contracts, their Java surface and links. |
| `analyzers.schemas` | Boolean | `true` | `false` switches off proto, SQLDelight, Room, OpenAPI and Ktor routes, and code generated from specs of other repositories. |
| `analyzers.native-bindings` | Boolean | `true` | `false` switches off JNI declarations and their links to native functions and targets. |
| `analyzers.git-history` | Boolean | `true` | `false` switches off the signals read from [Git history](#git-history). |
| `analyzers.compose-resources` | Boolean | `true` | `false` switches off Compose Multiplatform resources and the `Res` stub. |
| `analyzers.web-bundles` | Boolean | `true` | `false` switches off npm packages, TypeScript and JavaScript files, bundle files and source maps, and links from Gradle copy tasks to bundles. |
| `freshness.watch` | Boolean | `true` | `false` stops the [service](#service) from updating this workspace by itself. `kscope serve` ignores it. |
| `freshness.quiet-seconds` | Integer, 1 to 300 | `3` | How long files must stay the same before the service updates the index. |
| `resources.workers` | Integer, 1 to 8 | Automatic; see [Kotlin and Java analysis](#kotlin-and-java-analysis) | How many Kotlin workers may analyse at once. |
| `resources.worker-heap-mib` | Integer, 256 to 8192 | 2048 | The maximum heap of each Kotlin worker, in MiB. |

- **It is strict.** An unknown key or table, a wrong type, a value out of range and a syntax error make the file
  invalid. An error names the file, line and key, as in `kscope.toml:5: resources.workers: must be between 1 and 8`.
  Up to ten are reported.
- **An invalid file stops `scan` only** (also `update` and a scan through a server): the errors go to standard error,
  the exit code is 1 and the index is untouched. The queries do not read the file, and `info` reports the errors and
  exits with 0.
- **The environment wins.** For each setting: the [environment variable](#environment-variables), then the file, then
  the default. An unusable variable counts as not set. A worker count from the file is still limited to what the memory
  holds; one from the variable is used as given.
- `resources` changes how fast a scan runs and never what it finds. `scan` and `analyzers` change what it finds: the
  next scan redoes exactly the difference and matches a scan from scratch with those settings.
- The file is read only if it is a regular file of at most 64 KiB and not a symbolic link. Nothing in it is executed.
  A file of that name below the root is an ordinary file.

**Freshness.** Only the service reads `freshness`, again at every look, so changes apply without a restart. `kscope
serve` watches only with `--watch`, so a shared file cannot make a teammate's server watch. With watching off the
service still looks but starts no update, and a query says `Index is behind: watching is turned off in kscope.toml.`
With an invalid file it starts no update and a query says `Index is behind: kscope.toml is not valid and updates wait
until it is fixed.`

**Scan patterns.** A pattern is a path relative to the workspace root.

- Segments are separated by `/`. A pattern must not start with `/` or `./`, end with `/`, hold an empty, `.` or `..`
  segment, a `\` or a `:`, or exceed 512 characters.
- `*` matches any characters inside one segment and `?` one character. A segment `**` matches any number of segments
  and cannot be mixed with other characters. Everything else, brackets and dots included, matches itself. Matching is
  case sensitive, also on Windows. A pattern also matches everything below a directory it matches.
- A file is read when it matches `include` (or `include` is empty) and no `exclude` pattern matches it. Both only
  narrow the listing: a file Git ignores or a scan skips by itself, such as `build`, is never added by `include`.
- Build files (settings and build scripts, version catalogs, `gradle.properties`) stay readable outside `include`, so
  the build model of the modules you selected stays complete. An `exclude` pattern that names one removes it.
- `generated` only marks files the scan already lists. A pattern that matches no file gives the scan note
  `discovery.pattern-matches-nothing`.

**Analyzer switches.** Every group is on. A group that is off finds nothing: it leaves no fact, source stub or
relationship, so code that used what it produced, such as the `R` class or an AIDL interface, is analysed without it
and its references stay unresolved. `android-resources` covers the Android group except AIDL; AIDL and Compose
resources are separate groups. Switching a group off or on redoes exactly the units that depend on it.

## Environment variables

| Variable | What it does |
|---|---|
| `NO_COLOR` | When set, text output has no bold or colour even on a terminal. |
| `KSCOPE_NO_SERVER` | When `1`, queries are answered without a running [server](#serve), like `--no-server`. |
| `KSCOPE_SERVICE_HOME` | The [service directory](#the-files-of-the-service). The system property `kscope.service.home` takes precedence. Default `.kscope/service` in your home directory. |
| `KSCOPE_HOME` | The installation directory, when it cannot be found from the location of the `kscope` jars. The worker libraries are read from `workers/kotlin/lib` below it. |
| `KSCOPE_GRADLE_JAVA_HOME` | The home directory of the JDK that runs Gradle for `scan --trust-build`, for a build that needs a newer JDK than the one that runs `kscope`. Default: the JVM of `kscope`. |
| `KSCOPE_WORKER_HEAP` | The maximum heap of each Kotlin worker as an `-Xmx` value: digits with an optional `k`, `m` or `g`, such as `4g`. Overrides `resources.worker-heap-mib`. Default `2g`. |
| `KSCOPE_WORKERS` | The number of workers that may analyse at once. Overrides `resources.workers`. `1` analyses everything in one worker. Default: see [Kotlin and Java analysis](#kotlin-and-java-analysis). |

## Kotlin and Java analysis

A worker, a second JVM that a scan starts when source sets need analysing, analyses Kotlin and Java sources. It is part
of the distribution and runs on the JVM that runs `kscope`.

- **How many.** A scan with at least 2 MiB of source for each extra worker shares the modules out over several workers
  that run at once; a small scan uses one. Without `KSCOPE_WORKERS` or `resources.workers` the number is one worker for
  every two processors and for every worker heap plus 1 GiB of the memory left after 3 GiB for the client, at most 8.
  The index does not depend on the number of workers.
- **A worker that fails** fails only its own source sets, with an error diagnostic (`kotlin.worker-unavailable` or
  `kotlin.worker-failed`), and the scan goes on. When it ran out of memory or stopped answering, the error names
  `KSCOPE_WORKER_HEAP` and `KSCOPE_WORKERS`. A worker silent for ten minutes is ended and its request fails.

## Git history

In a Git work tree with at least two commits, a scan reads the last 2000 commits that are not merges. It stores per
file the number of commits (`history.commits`) and authors (`history.authors`) and a recency rank
(`history.recency-rank`, 0 for the file that changed last), and relates files that changed together in at least two
commits. Commits that touch more than 50 files are skipped, and a renamed file keeps the history it gained under its
current path. Otherwise the scan reports the informational diagnostic `history.not-available` and stores no signals.
`analyzers.git-history = false` switches it off.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Success. |
| 1 | Failure: no index (run `kscope scan`), a scan refused because of its [level](#levels), the workspace or index could not be read or written, an input such as a task file could not be used, an invalid [settings file](#settings-file) stopped `scan`, `benchmark` found its gate failed, or `skill` left alone a `kscope` folder it did not make. Run `kscope scan --rebuild` when the index is unreadable. |
| 2 | Usage error or ambiguous node: an unknown command or option, an invalid value, a missing workspace directory, `--detail` or `--sections` where they do not apply, or several exact node candidates. |
| 3 | `scan --verify` found that the incremental result differs from a clean rebuild. |

Results go to standard output; progress and errors go to standard error. The exit codes do not depend on the output
format or the progress options.

## Index

The index lives in `.kscope` inside the workspace. It is derived data: delete it at any time and `kscope scan`
rebuilds it. A scan that finds an index of another format discards and rebuilds it, while a command that only reads the
index reports that there is none and leaves it alone. Only `scan` changes the index.

| In `.kscope` | What it is |
|---|---|
| The index | What `scan` writes. |
| `gradle-model` | The cached build model of `--trust-build` scans. |
| `token-counts.bin` | A cache of token counts for `analyze` and `context`. Output is the same with or without it. |
| `serve.json`, `serve.lock` | The running [server](#safety-of-the-server) and its lock. |
| `repository-id` | The identity of a repository with no Git history. Keep it if that identity must stay stable. |
| `.gitignore` | Written when a scan creates `.kscope`, ignoring the whole directory so a Git work tree stays clean. |
