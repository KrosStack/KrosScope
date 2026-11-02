# ADR 0016: A workspace of several repositories, linked by Maven coordinates

Status: Accepted, implemented in 0.1.0. The gold classes and support-matrix rows for coordinate links came with the public fixture of [ADR 0020](0020-links-through-built-artifacts.md) · Date: 2026-10-18

## In short

- A workspace root may hold several repositories side by side. One scan indexes all of them into one index, and each
  keeps its identity: its repository id, its build roots and modules, and its repository-relative paths.
- A dependency on Maven coordinates (`group:artifact:version`) is resolved to the workspace module that publishes
  those coordinates, read from its `group`, `version` and publishing configuration.
- When the consumer pins another version than the one the producer builds from source, the scan reports
  `workspace.coordinate-version-mismatch`. The source is still what the consumer is compiled against, and every
  reference across the dependency is heuristic.
- References cross a repository boundary only through such a resolved dependency. The linker that matches a unique
  qualified name no longer answers across repositories by the name alone.
- A workspace of one repository is indexed and printed as today, byte for byte.

## Problem

Tasks of level 4 change files in two repositories ([results by task level](../benchmarks/task-levels.md)). The usual
case is a library repository and an application repository that depends on the published library by its coordinates.
KrosScope cannot answer such tasks.

- **One repository per scan.** `ScanEngine` resolves one `Repository` for the root and runs every stage for it. A root
  that is not a Git work tree is walked as a single repository with a generated id. Two checkouts below it lose their
  identity and their ignore rules, and their build roots end up in one repository.
- **Coordinates lead nowhere.** The static build model already turns a library dependency into a `DEPENDS_ON` edge to
  the external node `maven:group:artifact`, with the version as an attribute. No module knows the coordinates it
  publishes, so the edge can never reach a workspace module.
- **Names are not evidence of a dependency.** `QualifiedNameLinker` answers an unresolved import or qualified
  reference with the one workspace symbol that has the hinted qualified name. Inside one repository this covers a
  module dependency that the build model missed. Across repositories it would link code that does not depend on the
  other repository at all, or that depends on another version of it.
- **A stale pin is invisible.** A consumer that pins an older release than the producer's source sees an API that
  differs from the source in the workspace. Nothing says so.

## Options considered

1. **One index per repository, joined at query time.** Each index stays as it is, but no reference is ever resolved
   across the boundary, because the analysis of the consumer never sees the producer. Impact and context would stop
   at the boundary. Rejected.
2. **Extend the qualified-name linker to the whole workspace.** Cheap, but the match is textual. It ignores whether
   the consumer depends on the producer and at which version, and two copies of one library break it. Rejected.
3. **Read the metadata of local Maven repositories** (POM and Gradle module files) to learn what a published version
   contains. Such metadata names dependencies, not declarations, and often comes without jars. The source of truth is
   in the workspace. Not now.
4. **Only Gradle composite substitution** (`includeBuild` of the other repository). It requires an edit of the
   consumer's settings that most teams do not make. Not now.
5. **One index for several repositories, with dependencies resolved by coordinates.** Chosen.

## Evidence

Two sample repositories model the case: a producer repository that publishes a library, and a consumer repository that
depends on it by coordinates. They are private and are described here only by their shape.

- The producer is a Gradle build of seven modules, Kotlin Multiplatform and Compose Multiplatform. Four modules
  publish. They apply a convention plugin by its id, and the plugin, a class in an included build, applies
  `maven-publish` and sets `group` and `version` to string literals. The artifact id is the project name.
- The consumer is an Android application. Its version catalog declares the four libraries with `module =
  "group:artifact"` and one `version.ref`, and its build script depends on them through catalog accessors.
- The two lie side by side under one parent directory, and each is its own Git repository.
- Eight tasks of the consumer reach into both repositories:
  - two ask for the impact of an API change in the producer on call sites in the consumer;
  - one asks to detect that the consumer pins an older version than the producer's source;
  - one adds a Gradle composite substitution;
  - two follow calls across the boundary;
  - one aligns a feature in both applications;
  - one indexes the whole workspace.
  Their expected files are paths relative to the parent directory: the directory of the repository, then the path in
  it.

What the code already provides:

- `ArtifactRef` carries a `RepositoryId` beside the repository-relative path. Module keys and symbol identities embed
  the repository ([ADR 0004](0004-symbol-identity.md)). Ownership units of the repository kind are keyed by repository
  id, and those of artifacts by repository id and path.
- The static reader resolves catalog accessors and `version.ref` to `maven:group:artifact` with a `version` attribute,
  and the Tooling API mapper does the same for resolved dependencies.
- The static reader translates convention plugin classes into script calls (`ClassPluginTranslator`), and marks what a
  convention contributes as heuristic.
- The Kotlin planner wires the source sets of every module that a `DEPENDS_ON` edge resolves to into the analysis of
  the dependent module (`ModuleEdges`). The engine puts the API digests of that dependency scope into the
  fingerprint of a source set ([ADR 0014](0014-api-digests.md)).

Nothing about this design has been measured. No figure here is a claim about it.

## Decision

### 1. Repositories of a workspace

A scan finds the repositories of its root before discovery.

- **The root is in a Git work tree:** one repository, as today, nested checkouts included as they are today.
- **Otherwise:** every direct subdirectory of the root whose `.git` entry is a directory or a pointer file is a
  candidate. Hidden directories, symbolic links and the index directory are skipped. With two or more candidates, the
  workspace has several repositories. With fewer, the root is walked as today.
- **Identity:** the id of each repository is the root commit of its Git history, as `RepositoryIdentity` computes it
  today.
  - A candidate without a commit is skipped and reported as `workspace.repository-without-commit`.
  - Two candidates with the same id, such as two clones or two worktrees of one repository, cannot both be indexed.
    The first in directory order is kept and each other one is reported as `workspace.duplicate-repository`.
- **Files:** each repository is listed by its own Git, so its own ignore rules apply. Files of the root that belong to
  no repository are not indexed, and one `workspace.files-outside-repositories` notice counts them.
- **Order:** repositories are processed in the byte order of their directory names.

The model gains one field. `Repository.directory` is the directory of the checkout relative to the workspace root, a
`RepoPath`, and empty for a workspace of one repository. It locates the checkout and is not part of its identity.

### 2. The scan

- `ScanContext` holds the list of repositories, each a `RepositoryCheckout` (the existing type in `core/spi`) with its
  root, in place of one `Repository`.
- Discovery and the build model run once per repository, with their own ownership units, as today. A
  `BuildModelRequest` still names one repository, so providers do not change.
- The engine then merges the build models into one workspace model and resolves coordinates in it (section 4).
  Synthesis, production, linking and the search index run once over the merged model.
- The engine's `WorkspaceFiles` maps each `ArtifactRef` to the root of its repository. A producer with repository
  units, such as Git history, is given the checkout of that repository.
- The watcher's stamp covers the listing of every repository, and an operation in progress in any of them defers the
  scan.
- With `--trust-build`, the trusted provider runs in each repository that has a build. Compile classpath entries that
  lie inside any repository of the workspace are dropped, as entries inside the root are today.

### 3. Published coordinates of a module

A module may publish several coordinates: a Kotlin Multiplatform module has one publication per target besides its
root publication. The Gradle providers record every one of them in one documented key of `Module.attributes`:

| Key | Value |
|---|---|
| `maven.publications` | Every coordinate the module publishes, as `group:artifact:version`, sorted and separated by commas |

- **Which modules.** A module publishes when it applies `maven-publish` or `com.vanniktech.maven.publish`, directly or
  through a convention plugin that the static reader can translate.
- **Where the static provider reads coordinates**, each only as a string literal:
  - `group = "…"` and `version = "…"` in the module's build script, or in a convention plugin it applies;
  - otherwise the `group` and `version` properties in the `gradle.properties` of its build root;
  - `groupId`, `artifactId` and `version` of each `MavenPublication` in `publishing { publications { } }`, at the top
    of a script or inside `afterEvaluate { }`, each one entry, a missing part taken from the module;
  - `mavenPublishing { coordinates("group", "artifact", "version") }`.
- **Gradle properties.** A part written as `findProperty("key")`, `property("key")` or
  `providers.gradleProperty("key").get()`, with a literal key, is read as Gradle reads it. A provider read sees the
  properties of the build and takes the `gradle.properties` of the build root. A direct read sees those of the project
  and takes the `gradle.properties` of the module directory and, failing that, of the build root; it is left unread
  when the module's scripts assign the same name (`key = ...`, `ext.key = ...`, `ext.set("key", ...)`,
  `setProperty("key", ...)` and the like, at the top of a script or in an `afterEvaluate { }` callback), because Gradle then gives the assigned value, which is
  not resolved. A `group` or `version` that a publication inherits takes the last assignment in the order Gradle runs
  them: the top-level statements of every script of the module, convention plugins first, then the callbacks in the
  same order, a callback that a callback registers after those already registered. When a callback both assigns a property and sets it by a call, or assigns it under two spellings such as
  `version` and `project.version`, the order is not known and the value counts as computed, as it does after a
  compound assignment such as `version += '-SNAPSHOT'`. The
  `gradle.properties` of the user home and the environment are never read, so that the index does not depend on the
  machine.
- **The artifact id** is otherwise the last segment of the Gradle path, or the root project name for the root module.
  Without an explicit publication, the module records this one entry.
- **Missing values.** A publishing module whose group or artifact id cannot be read gets no coordinates, and its
  producer reports `build.publication.not-analysed` with what could not be read. When only the version is computed,
  such as a property plus a suffix that a function computes, the publication is recorded with the version
  `unspecified` and `maven.heuristic` = `true`, and the producer reports `build.publication.not-analysed` as
  information: it is linked by group and artifact only, and every link to it is heuristic.
- **Certainty.** Coordinates from a convention plugin, and publications with an unread version, are heuristic, as
  dependencies from convention plugins already are; `maven.heuristic` is then `true`.
- **The Tooling API provider.** The model plugin writes an additional `publication` section with the project's
  `group`, `name` and `version` and the coordinates of each `MavenPublication`. The mapper turns it into the same
  attribute, one entry per publication, with exact certainty.

### 4. Resolving a dependency by coordinates

After the merge, the engine resolves every `DEPENDS_ON` edge to `maven:group:artifact`.

- **Matching.** A module of another repository matches when one entry of its `maven.publications` has the group and the
  artifact of the dependency. For a Kotlin Multiplatform module whose target publications could not be read, the
  artifact of a target matches its root entry as well: the artifact id, a hyphen and the lowercase name of one of its
  targets, such as `-jvm` or `-android`.
- **Ambiguity.** When two or more modules match, nothing is linked, and `workspace.coordinate-ambiguous` names them.
  Coordinates are never matched within the consumer's own repository, where project dependencies already apply.
- **The edge.** A match adds a `DEPENDS_ON` edge from the consumer module to the producer module:
  - sub-kind `maven-coordinates` and evidence class `build-model`;
  - the site, scope and `configuration` of the declaration;
  - the attributes `version` (the pinned version), `sourceVersion` (the version of the matching entry) and `versionMatch`
    (`true`, `false` or `unknown`).
  The edge to the external node stays, as the provider's own account of the declaration.
- **Versions** are compared as strings. A version that is missing, dynamic (`+`, `latest.*`) or a range is `unknown`, and so is any version
  compared with a built version that is `unspecified`.

| `versionMatch` | Certainty of the edge | Part of the model that analysis sees | Diagnostic |
|---|---|---|---|
| `true` | Exact, or heuristic if either side is | Yes | None |
| `false` | Heuristic | Yes | `workspace.coordinate-version-mismatch`, warning |
| `unknown` | Heuristic | Yes | `workspace.coordinate-version-unknown`, info |

- **Matched versions.** The producer's source is what the consumer compiles against. The edge is added to the
  dependencies of the merged model, the Kotlin planner wires the producer's source sets into the analysis of the
  consumer as for any module dependency, and the compiler resolves the references with its own evidence.
- **Other versions.** The source in the workspace is not what the consumer compiles against, but it is the closest
  thing the scan has to it. The edge is added to the model that analysis sees all the same, so that the compiler
  resolves the types and members that the consumer uses; the edge is heuristic, and so is every reference that
  resolves across it (section 5).
- **Storage.** The resolved edges and their diagnostics are one batch: producer `kscope.engine.coordinates`, an
  ownership unit of the workspace kind, and a fingerprint over the build model fingerprints of all repositories.

The text of the mismatch, with the location of the declaration:

```
<consumer module> pins <group>:<artifact>:<pinned>, but <producer module> builds <sourceVersion> from source.
References across this dependency are heuristic.
```

A module is named by its workspace path and Gradle path, for example
`app (:app) pins org.example:model:1.0.0, but library (:model) builds 1.2.0 from source.`

### 5. The qualified-name linker

`QualifiedNameLinker` keeps its input, the unresolved analyzer edges whose hint is a qualified name, and it chooses
among the symbols of that name in two steps.

1. **The repository of the reference.** Candidates are the symbols in the same repository as the reference. Exactly
   one gives today's answer: sub-kind `qualified-name-match`, heuristic, textual.
2. **Repositories reached by coordinates.** Only when the first step finds none: candidates are the symbols declared in
   modules that the module of the reference reaches over `DEPENDS_ON` edges. That reach always includes at least one
   `maven-coordinates` edge, because coordinates are never matched inside a repository. Exactly one gives an answer
   with sub-kind `coordinate-match`, heuristic and textual.

A symbol in another repository that no coordinate dependency reaches is never an answer. In a workspace of one
repository the first step sees every symbol, so the output is unchanged.

This is how references cross a version mismatch. The compiler resolves the references of the consumer into the source
of the producer, because the edge is part of the model that analysis sees. Every such reference, whatever it resolved
to (a type, a call, an inheritance), is heuristic: the Kotlin producer lowers a reference that the compiler resolved
to a symbol of a module that the source set of the reference sees only through a heuristic `DEPENDS_ON` edge, meaning
that its analysis module would not be wired to that module if the heuristic edges were left out. The wiring follows
the same rules either way, so an exact path through another module's `implementation` dependency, which the compiler
does not see, does not count. Its sub-kind and its evidence class stay. A
dependency from a convention plugin is heuristic as well, so the same holds across it. What the compiler still leaves
unresolved is answered by name, as above.

### 6. The command line and MCP

- **Paths.** In a workspace of several repositories, every file path in text output is a workspace path: the directory
  of the repository, a slash, and the repository-relative path. Every argument that names a file accepts one. In a
  workspace of one repository the directory is empty, and output and arguments do not change.
- **JSON.** Results keep `ArtifactRef` as it is, `{repository, path}`. In a workspace of several repositories the `info`
  result gains the optional field `repositories`, a list of `{id, name, directory, headRevision}` in directory order,
  so a program can build workspace paths. Its schema is extended with the optional field. In a workspace of one
  repository the field is absent and the JSON does not change.
- **`info` text.** In a workspace of several repositories, a `Repositories` section lists each repository with its
  directory, its short id and its number of modules, then the number of dependencies linked by coordinates and how
  many of them do not match versions. A workspace of one repository prints no such section.
- **The new diagnostics** appear where diagnostics appear today: in the report of `scan` and in the count of `info`.
- **MCP.** No tool and no argument is added. The text of the tools carries workspace paths, and `info` lists the
  repositories. `kscope service add` takes a workspace root of several repositories like any other.

### 7. Store and migration

- The `repository` table gains the column `directory TEXT NOT NULL`, and the schema version goes from 5 to 6.
- An index of version 5 is `Incompatible`. As for every schema change, it is deleted and rebuilt by the next scan, and
  until then queries report that the workspace is not indexed. No data is migrated, because the index is derived.
- Nothing else in the schema changes. Coordinates are module attributes, the edges are relationships and the
  diagnostics are diagnostics.

### 8. Determinism

- Repositories, modules and candidates are sorted by stable keys before they are used. A match does not depend on
  the order in which repositories were found.
- No absolute path enters a fact. `Repository.directory` is relative to the root.
- `scan --verify` compares the coordinate batch like every other batch.
- Ties that the ranking and the analysis break by path are broken by the workspace path. In a workspace of one
  repository that is the same order as today.

## Out of scope

- Repositories deeper than one directory below the root, a list of repositories in the settings file, and Git
  submodules inside a repository, which stay part of it.
- Gradle composite substitution across repositories (`includeBuild`) as a source of links, and dependency substitution
  rules.
- `module.yaml` files of the Kotlin toolchain, and consumers that are not Gradle builds.
- Coordinates that are computed, or that are set in `allprojects` or `subprojects` blocks; BOMs and platforms;
  classifiers; relocation.
- Metadata of local Maven repositories, and what a published version contained.
- Co-change history across repositories, settings per repository, and workspaces of many repositories (level 5).

## Consequences

- **Every index is rebuilt once**, because the schema version changes.
- **A root that was walked as one repository**, a plain directory with two or more checkouts in it, is indexed as
  several from now on. Files outside the checkouts leave its index.
- **Support is claimed per relationship.**
  - "Maven coordinates to workspace module" gets a row in the [support matrix](../support-matrix.md) and a gold class
    in a public fixture of two repositories.
  - References across a matched dependency carry compiler evidence and are scored like other references.
  - Answers of the `coordinate-match` kind are heuristic and counted as such.
- **Level 4 becomes measurable.** The real-repository harness learns a workspace root and workspace paths in its
  gold. The level-4 row of the task-level page changes only after a run, and no result is claimed before.
- **Cost** is the sum of the repositories, plus one resolution step over the build models, which reads no file.
- **The architecture page** changes the "Workspace" scale tier from "not implemented" to two or more repositories side
  by side, linked by coordinates.

## Reversal cost

- **Low for the linker.** Restricting it is an internal rule. Single-repository output does not change, so it can be
  reverted on its own.
- **Medium for coordinates and their resolution.** The attribute keys, the sub-kinds `maven-coordinates` and
  `coordinate-match`, and the diagnostic ids become names that programs read. Removing them changes results without a
  schema change.
- **Higher for the workspace layout.** `Repository.directory`, schema version 6, workspace paths in text output and the
  `repositories` field of `info` are contracts with users and with programs. Withdrawing them needs another schema
  version and a rebuild of every index.
