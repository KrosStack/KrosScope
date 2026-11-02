# ADR 0020: Repositories registered anywhere, linked through what they build for each other

Status: Accepted; registration, JAR files, registered artifact folders, native library loads, OpenAPI and Wire specs, and web bundles implemented · Date: 2026-10-26 · Extends [ADR 0016](0016-multi-repository-workspace.md)

## In short

- **Registering.** `kscope workspace add <path or Git URL>` adds a repository from anywhere on disk to a workspace,
  cloning a Git URL with the user's Git. `remove` and `list` complete the group, and an agent can run all three. The
  list is kept per workspace in a file of the user, never in the workspace, as trust is
  ([ADR 0018](0018-stored-trust-for-exact.md)).
- **Indexing.** Registered repositories join the side-by-side ones of ADR 0016, with the same identity, ignore rules
  and repository-relative paths.
- **Four new links.** A consumer is linked to the source that builds what it consumes:
  - JVM libraries;
  - native libraries in C, C++ or Rust;
  - JS or Wasm bundles;
  - code generated from a spec that lives in another repository.

  Maven coordinates stay the fifth.
- **No build runs to find them.** kscope reads the build declarations of both sides. When a build has already left
  outputs on disk, it also reads them as content: the class list of a JAR, a source map, generated sources. The agent
  or the user builds. Folders of built archives outside the workspace, such as a Maven local repository or a Gradle
  cache, are read only after the user registers them for the workspace.
- **Every link names its evidence.** It is `build-model` for declared inputs and outputs, `content` for an output
  that matches, and `convention` for a name rule. A link is `exact` only where content proves it. A consumer whose
  producer is missing or ambiguous is `unresolved` with a reason, never guessed.

## Problem

ADR 0016 links repositories in one way only: a Gradle module that depends on Maven coordinates which a module of
another repository publishes. It also requires every repository to sit side by side under one parent directory.
Real products cross repositories in other ways:

- a JAR built in one repository and dropped into another's `libs`, or read from `mavenLocal`;
- a C, C++ or Rust library loaded through JNI or JNA;
- web assets built by one project and packaged or served by another;
- clients and models generated from an OpenAPI, proto or GraphQL file kept in a separate repository.

None of these reaches the producer today. `impact` and `context` stop at the boundary, and the qualified-name linker
deliberately does not cross it (ADR 0016).

## Options considered

1. **Run each repository's build to learn its outputs.** Precise, but it executes the code of every repository.
   Rejected for the default level; under `exact` in a trusted workspace it can refine the links later.
2. **Match names across repositories as text.** Cheap, but it links code that does not depend on the other
   repository at all. Rejected as sole evidence, as in ADR 0016.
3. **Declarations of both sides, confirmed by outputs already on disk.** Chosen.
4. **Package metadata only** (POM, npm lock files, `Cargo.lock`). It names versions, not contents. Used to match names
   and versions, never as the link itself.

## Decision

### 1. Registering repositories

| Command | Effect |
|---|---|
| `kscope workspace add PATH` | Registers the repository at `PATH` with the workspace. |
| `kscope workspace add URL [--ref REF] [--dir DIR]` | Clones `URL` with the user's Git into `DIR`, by default beside the workspace root, then registers it. The one network action of kscope, taken only on this command. |
| `kscope workspace remove NAME` | Unregisters it. The checkout is left on disk. |
| `kscope workspace list` | Lists the repositories of the workspace and where each comes from. |
| `kscope workspace artifacts add DIR` or `add --maven-local` | Registers a folder of built JAR and AAR files; `--maven-local` is `.m2/repository` in the home directory of the user. |
| `kscope workspace artifacts remove DIR` and `list` | Unregisters a folder, or lists the folders in the order they are looked in. |

- **Where the list lives.** In `workspaces.toml` in the directory of the service, keyed by the real path of the
  workspace. A repository could otherwise register private directories of the user through a settings file it
  ships.
- **Names.** Each registered repository prints under a name in the workspace: its folder name, made unique, so its
  files read `native-core/src/jni.cpp`. The root repository keeps its paths as today.
- **Identity, duplicates, ignore rules and order** follow ADR 0016.
- **Trust.** A registered repository's build runs only where the workspace is trusted and at level `exact`
  (ADR 0018).
- **Freshness.** The watcher and `serve --watch` watch every registered repository.

### 1a. Registered artifact folders

A dependency on a Maven coordinate can be linked through a folder of built archives, as `files(...)` is linked through
a JAR. This is the `mavenLocal` and Gradle-cache row of the table below.

- **Why opt-in.** kscope never reads state that depends on the machine by itself: not `~/.gradle/gradle.properties`,
  not the environment for build values. The index must not depend on the machine, and kscope must not come near the
  credentials that those places can hold. A folder of artifacts is read only when the user registers it for the
  workspace, and only for the coordinates that the build files of the workspace name.
- **Where it is kept.** Per workspace, beside the registered repositories, as the key `artifact-folders` of the table
  of the workspace in `workspaces.toml`: absolute, normalized paths, the real path when the folder exists, in
  registration order. The key is written only when a folder is registered. The reader of the file is strict about every
  key, so a version of kscope that does not know this one reads the file as invalid, registers nothing from it and
  refuses to change it until the key is gone.
- **Paths on every system.** A path is written with `/` as the separator, and any backslash that remains is escaped,
  so a Windows path with a drive letter, or a backslash in a name on another system, reads back unchanged. `~` is
  never expanded; the shell does that. Two paths are the same folder when `Files.isSameFile` says so, which makes the
  comparison case-insensitive only where the file system is. `--maven-local` is `.m2/repository` below the `user.home`
  of Java, on every system. A folder must exist when it is registered; one that goes away later is listed as missing
  and holds nothing.
- **Layouts.** In each registered folder, in this order, the Maven layout
  `group/as/directories/artifact/version/artifact-version.jar` (or `.aar`), then the Gradle cache layout
  `files-2.1/group/artifact/version/<hash>/artifact-version.jar` (or `.aar`). The folder may be the Gradle user home,
  its `caches/modules-2` or `modules-2` directory, a directory that holds `files-2.1`, or `files-2.1` itself. Of several
  hash directories those in name order are tried in turn. Classifier archives, such as `-sources.jar`, are not read.
- **Which dependencies.** A module dependency on an external coordinate `group:artifact:version` whose version is a
  literal (not a range, a `+` version or `latest.*`) and that no module of another repository publishes, so that no
  coordinate link ([ADR 0016](0016-multi-repository-workspace.md)) exists for it. The parts are plain names; a coordinate
  with a part that could reach outside the folder is not looked up.
- **Linking.** The modules of other repositories that build `artifact-version.jar` or `artifact-version.aar` are found
  by archive name, as for a `files(...)` dependency. When exactly one does, the archive from the first folder that has
  it is compared with that module as a JAR on a file path is: its class list against the sources of the module,
  `content` evidence and `exact` when they match, otherwise `build-model` and heuristic. If that archive cannot be read,
  the build outputs of the module are read instead. Several such modules give `artifact.ambiguous-producer`. A module
  whose archive no registered folder holds gives `artifact.output-missing`, until it is published. An archive that no
  module builds is a library of someone else and stays an external dependency, as a JAR on a file path does.
- **AAR.** An Android library that is not multiplatform builds `<archive name>-<version>.aar`, named as a JVM
  module's JAR is, by both build providers. The classes of an AAR are those of its `classes.jar`. Among the build
  outputs of a module, `build/libs`
  is joined by `build/outputs/aar`: an AAR there whose name starts with the name of the module, of its directory or of
  the archive confirms a link by content, for a coordinate and for a JAR on a file path alike.
- **Incremental scans.** The registered folders, and the path, size and modified time of every archive that was looked
  up in them, are inputs of the unit that stores these links, with its facts. A scan after a new `publishToMavenLocal`,
  after a folder is registered or after one is removed gives what a rebuild gives. A change takes effect at the next
  scan, as for repositories.

### 2. Links through built artifacts

| Kind | Producer, read from | Consumer, read from | Link | Evidence |
|---|---|---|---|---|
| JVM library | archive name, `group` and `version` of a module; its built JAR or AAR if present | `files(...)`, `fileTree`, `flatDir`, `mavenLocal` and Gradle-cache coordinates | consumer module to producer module, then references across, as for coordinates | `build-model`; `content` when the JAR's class list matches the module's declarations |
| Native library | CMake `add_library`, Cargo `[lib]` with its crate type, ndk-build `LOCAL_MODULE` | `System.loadLibrary`, `System.load`, JNA `Native.load`, `jniLibs` directories, `externalNativeBuild` paths into the other repository | consumer to library target; Kotlin `external` and Java `native` declarations to `Java_` functions in C, C++ and Rust | `build-model` for paths; `convention` for library and JNI names |
| JS or Wasm bundle | `package.json` with the output directory of its Vite, webpack or Rollup config; Kotlin/JS and Wasm executables | Gradle `Copy` and `processResources` sources, Ktor and Spring static resources, npm `file:` dependencies, imports by package name | consumer to the project that builds the bundle; bundle files to source files where a source map exists | `build-model`; `content` for a source map; `convention` for default output directories |
| Code from a spec | the spec file: OpenAPI, `.proto`, GraphQL schema | generator inputs that point into another repository: OpenAPI Generator `inputSpec`, protobuf and Wire source directories, Apollo schema files | generated surfaces ([ADR 0007](0007-generated-surfaces.md)) to the spec; spec operations and services to server implementations named by `operationId`, route or service | `build-model` for inputs; `convention` for names |

- **Reason codes.** A consumer whose producer cannot be found is `unresolved` with
  `artifact.producer-not-in-workspace`, `artifact.ambiguous-producer` or `artifact.output-missing`.
- **Other languages.** C, C++, Rust and JS or TS files are read for the names in this table only, by pattern, with no
  full parser. `package.json`, `Cargo.toml` and `CMakeLists.txt` are read by their grammars. The files stay
  searchable as text.

### 3. What users see

- `info` lists the repositories and their origin.
- `impact`, `related`, `analyze` and `context` follow the new links.
- JSON names the link kind and its evidence.
- Each link class gets a row in the [support matrix](../support-matrix.md) once a gold-backed test proves it, as for
  every other link.

## Evidence

Nothing is measured yet, and no figure here is a claim. The public fixture
[`fixtures/workspace-of-repositories`](../../fixtures/workspace-of-repositories/README.md) carries one producer and
one consumer for each kind. Its gold links are in `benchmark/workspace/gold/relationships.json`:

- a Kotlin library;
- a C++ and a Rust JNI library;
- a web project;
- a spec repository;
- an application that consumes all of them.

The build outputs it needs are written by its tests from `benchmark/workspace/outputs.json`. Level 4 tasks in the
benchmark come from that fixture.

## Consequences

- **New contracts.** A CLI command group (`workspace`, with the sub-group `artifacts`), a user file
  (`workspaces.toml`), new relation kinds and evidence notes in JSON, and a store schema version.
- **Network.** kscope still needs no network, except to clone a URL that the user or agent passes to `workspace
  add`.
- **Limits.** Outputs that only a build produces, such as a symbol table of a stripped binary or code generated
  without a spec, stay `unresolved` until the build has run.

## Reversal cost

Medium. The links are new relation kinds behind the capability registry; removing them leaves the ADR 0016 workspace.
Registered repositories are a user file. An older version reads a file without artifact folders as before and refuses
one that has them.
