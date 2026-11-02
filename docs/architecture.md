# KrosScope Architecture

KrosScope is a headless, local software-intelligence and context-optimization engine for Kotlin, Android
and Kotlin Multiplatform workspaces. It statically builds an evidence-tagged graph of a workspace and uses
it to answer repository questions, analyse development tasks and compile token-budgeted context packages.
No network, cloud AI or ML model is required.

## In short

- Support is defined per relationship, not per language. Every fact records who produced it, from which input
  and with what evidence, and absence is reported as `unresolved` or `not analysed`.
- The same inputs yield byte-identical facts. ML is an optional enrichment layer.
- A scan runs seven stages, from discovery to persistence. An update redoes only the ownership units whose
  inputs changed, and `kscope scan --verify` compares the result with a clean rebuild.
- The Kotlin and Java analyzer runs in a child JVM, so the engine and the frontends never link to compiler
  internals.
- The dependency rules between modules are enforced by the build. `service` is the only module that sees
  everything except `cli` and `benchmark`.
- The graph is never exposed as a query language. Public surfaces are the service API and its serializable
  results.

## Principles

1. **Depth belongs to relationships.** Support is defined per relationship (Kotlin<->Java, `expect`->`actual`,
   resource->generated accessor->consumer, manifest->class, AIDL->Stub->client, schema->generated API->consumer,
   catalog->module, `external`->JNI contract), not per language.
2. **Evidence belongs to edges.** Every fact records who produced it, from which input, by which class of
   evidence and with what precision. Uncertain relationships are never stored as certain.
3. **Absence is reported.** `unresolved` and `not analysed` are first-class states, with reason codes and
   coverage counters, so a clean result is distinguishable from an unexamined one.
4. **Deterministic first.** The same inputs yield byte-identical facts. ML is an optional enrichment layer.
5. **Extension through capabilities.** New artifact support means registering a detector and a producer or
   linker, not editing unrelated logic. File extensions are detection metadata only.
6. **Reuse over reimplementation.** Parsing, resolution, build modelling, storage and tokenization come
   from mature, permissively licensed components.
7. **The engine has no frontend.** The CLI is one client of a frontend-independent service API.

## Module map

```
core/model            Internal representation: identities, entities, facts, evidence. No I/O.
core/spi              Capability contracts and the analyzer wire protocol.
core/store            SQLite persistence, FTS index, ownership-based invalidation.
core/engine           Discovery, scan pipeline, incremental update, linking, verification.
analyzers/kotlin        Client adapter for the Kotlin/Java analyzer worker.
analyzers/kotlin-worker Out-of-process worker: Kotlin Analysis API (Kotlin + Java + build scripts).
analyzers/gradle        Build-model providers: static, and Gradle Tooling API behind a trust gate.
analyzers/android       Manifest, resources, generated Android surfaces, AIDL.
analyzers/compose       Compose Multiplatform resources and the generated `Res` class.
analyzers/jni           C, C++ and Rust `Java_*` functions, native library targets, library loads, and their linkers.
analyzers/schema        Proto, SQLDelight, Room, OpenAPI, Ktor routes, code generated from specs, and web bundles.
analyzers/history       Git history signals.
intelligence/retrieval  Lexical search, graph expansion, ranking, task and impact analysis.
intelligence/context    Context compiler, token estimation, renderers.
service               Frontend-independent use-case API with serializable results; default wiring.
cli                   The `kscope` binary.
benchmark             Analyzer conformance, task-localization benchmarks and agent benchmark harness.
tools/detekt-rules    The project's own Detekt rule, applied to every module by the build; not shipped.
fixtures/reference-workspace   Standalone Kotlin/Android/KMP reference project with gold sets.
```

Dependency rules (enforced by the build):

- `core/model` depends on nothing in the project. `core/spi` depends on `core/model` only.
- `analyzers/*` and `intelligence/*` depend on `core/model` and `core/spi` only, never on each other, on
  `core/store` or on `core/engine`.
- `core/engine` depends on `core/model`, `core/spi`, `core/store`.
- `service` is the only module that sees everything except `cli` and `benchmark`. Product frontends depend on
  `service`. The CLI also depends on `benchmark` for `kscope benchmark`; evaluation code and disposable lexical
  baselines live in `benchmark`, which reads the graph through `service`, `core/model` and `core/spi`.

## Internal representation

- **Structure.** `Workspace` -> `Repository` -> `BuildRoot` -> `Module` -> `SourceSet`, with `Target`
  (platform) and `Variant` (build type and flavors) attached to modules. Source sets carry `dependsOn`
  edges, so the canonical graph is per source set; per-target "as compiled" views are derived.
- **Published coordinates.** The attribute `maven.publications` of a module lists every coordinate it publishes as
  `group:artifact:version`, sorted and separated by commas. The static provider reads string literals and Gradle
  properties with a literal key (a provider read from the `gradle.properties` of the build root, a direct read from
  that of the module and then of the build root, and left unread when the module's scripts assign the same name), and
  marks coordinates from a convention plugin, and publications whose version is computed (recorded as `unspecified`),
  with `maven.heuristic` = `true`; the Tooling API provider reads the publications of the build. A module whose group
  or artifact id cannot be read gets the diagnostic `build.publication.not-analysed`, and so does one whose version is
  computed, as information.
- **Artifacts.** Every file is an `Artifact` with a normalized repository-relative path, content hash,
  `ArtifactType`, owning source set when known, and a generated flag with a pointer to its source of truth.
- **Symbols.** A `Symbol` is any addressable declaration or contract endpoint: classes, functions,
  properties, but also resources, manifest components, AIDL interfaces and methods, proto messages, SQL
  tables and queries, catalog aliases and JNI contracts. Kinds are open, namespaced values
  (`kotlin.class`, `android.resource.string`, `aidl.method`) grouped into a small closed set of categories
  that generic logic may depend on.
- **Identity.** Identity is never a bare path.
  - A `SymbolId` is a SCIP-style symbol string `kscope <ecosystem> <module-key> <source-set> <descriptor>`; the
    descriptor follows the SCIP descriptor grammar and disambiguates overloads by parameter types, so identities
    do not shift when siblings are added.
  - The module key embeds the repository and build root, which keeps identities unique across a multi-repository
    workspace.
  - Repository identity is derived from version-control metadata, not from its checkout location.
  - `Repository.directory` locates the checkout relative to the workspace root and is empty for a workspace of one
    repository. It is a location and not part of the identity.
- **Coordinate links.** After the build models of a workspace of several repositories are merged, a dependency on
  `maven:group:artifact` is matched against the `maven.publications` of the modules of the other repositories, never
  those of the consumer's own repository. A unique match adds a `DEPENDS_ON` edge with sub-kind `maven-coordinates`
  and the attributes `version`, `sourceVersion` and `versionMatch`. Every such edge is part of the model that the
  analyzers see; one whose versions do not match, or cannot be compared, is heuristic, and so is every reference
  that the compiler resolves across a heuristic edge. The qualified-name linker considers symbols of another
  repository only when a dependency path reaches their module.
- **JAR links.** A JVM module records the JAR files it builds in the attribute `jvm.archives`, from its archive name and
  version. A dependency on a JAR file, from `files(...)` or `fileTree(...)`, is a `DEPENDS_ON` edge to
  `file:<path in the repository>`, or `file:<directory>/` for a tree, which carries its literal `include` and `exclude`
  patterns in the attributes `tree.include` and `tree.exclude`; a tree whose patterns are not literals is not read.
  The JARs of a tree are those at any depth below the directory that the patterns select, as Gradle selects them
  (`*`, `?` and `**`, relative to the directory), without following symbolic links. The directory is read at every
  scan: the Gradle tooling model records a tree by its directory and patterns and not by the files it held when the
  model was fetched. A JAR name that exactly one module of another
  repository builds adds a `DEPENDS_ON` edge with sub-kind `jvm-library` and the attributes `file` and, when a JAR was
  found on disk, `content`. The evidence is `content` and the edge is exact when the classes of that JAR lie in the
  packages of the module's sources and name at least one of them; otherwise it is `build-model` and heuristic. Every
  such edge is part of the model that the analyzers see. A JAR name that several modules build is an unresolved edge
  with `artifact.ambiguous-producer`, and a JAR that no module builds stays a dependency on the file. The built
  archives of a module are read from `build/libs` for a JAR and from `build/outputs/aar` for an AAR, whose classes are
  those of its `classes.jar`.
- **Registered artifact folders.** The folders that the user registered for the workspace with
  `kscope workspace artifacts add`, such as a Maven local repository or a Gradle cache, are the one input of a scan that
  comes from outside the workspace and the registered repositories, and are read only because the user named them. The
  service hands them to the engine as `WorkspaceConfig.artifactFolders`, asked for once per scan. A dependency on
  `maven:group:artifact` with a literal version, which no module of another repository publishes, is looked up in them
  as `artifact-version.jar` or `.aar`, in the Maven layout and in the Gradle cache layout. A module of another
  repository that builds that file name is linked as for a JAR on a file path, with the archive found there compared
  with the module's sources. Folders that are missing hold nothing, and the folders with the size and modified time of
  each archive looked up are inputs of the unit that stores the links, so that an incremental scan agrees with a
  rebuild. See [ADR 0020](decisions/0020-links-through-built-artifacts.md).
- **Repositories without a build.** In a workspace of several repositories, a repository that no build model provider
  covers gets one module for all its files, with the attribute `kscope.repository-module` and the notice
  `build-model.repository-module`, so that its files can hold symbols. A workspace of one repository keeps the notice
  `build-model.not-available` and has no module.
- **Native library links.** CMake `add_library` with `SHARED`, `MODULE` or no type, Cargo `[lib]` with crate type
  `cdylib` or `dylib`, and ndk-build `LOCAL_MODULE` blocks that build a shared library are library targets with the
  symbol kinds `cmake.library`, `cargo.library` and `ndk.library`. A string literal given to `System.loadLibrary`,
  `System.load` (its file name without `lib` and extension), or JNA `Native.load` or `Native.loadLibrary` is a
  `jvm.load-library` symbol. A load is bound to the one target of its name in any repository by a `BINDS` edge with
  sub-kind `native-library` and evidence `convention`: probable when the build declares a shared library, heuristic
  when it leaves the type open. Several targets of the name are `artifact.ambiguous-producer`; none, in a workspace
  with native build files, is `artifact.producer-not-in-workspace`. The JNI linker binds Kotlin `external` and Java
  `native` declarations to `Java_` functions in C, C++ and Rust files of any repository.
- **Spec links.** OpenAPI 3 and Swagger 2 documents, in YAML or JSON with a top-level `openapi` or `swagger` key, have
  the symbols `openapi.document`, `openapi.operation` (named by its operation id, signature `GET /tasks`) and
  `openapi.schema`; every proto file has a `proto.file` symbol. Routes of the Ktor routing DSL with literal paths,
  with the prefixes of their `route` blocks, are `ktor.route` symbols named `GET /tasks`. An operation is bound to the
  one route of its method and path in any repository, parameters taken as placeholders and the relative paths of the
  document's servers tried as prefixes: `BINDS`, sub-kind `openapi-route`, evidence `convention`, probable. Several
  routes are `spec.ambiguous-implementation`.
- **Generator inputs.** A workspace synthesizer reads the build scripts, Kotlin or Groovy, for the literal
  `inputSpec` of `openApiGenerate` and the `sourcePath` and `srcDir` of `wire`, relative to `$rootDir`, `$projectDir`
  or the module. Each is a symbol `openapi-generator.input` or `wire.source-path` of the module, with a `DEPENDS_ON`
  edge of sub-kind `generator-input` and evidence `build-model` to the `openapi.document` or each `proto.file` it
  reaches in any repository, or `artifact.producer-not-in-workspace`. The module then gets stubs: the Wire classes of
  those proto files when it generates Kotlin with Wire, and for the `kotlin` generator of OpenAPI Generator an API
  class per tag and a data class per object schema, with `GENERATES` edges from the operations and schemas. Its one
  unit covers every build script and spec file, so a change to any of them makes the stubs again. The scripts are read
  as text at every level.
- **Web bundles.** A `package.json` is an `npm.package` symbol that records the directory its bundler writes to,
  from the Vite, webpack or Rollup config beside it or the default of the bundler, and whether a config declares it;
  the output of Vite lies below the top-level `root` of its config.
  Hand-written TypeScript and JavaScript files of a module are `typescript.file` and `javascript.file` symbols. The
  npm detector names that directory as an output root, so discovery reads its files from disk at every scan although
  Git ignores them. A bundle file with a source map is a `bundle.file` symbol with a `DEPENDS_ON` edge of sub-kind
  `source-map`, evidence `content`, exact, to each source file of the repository the map names, also one of another
  module. The unit of this producer is the repository. A workspace producer reads the literal
  `from` paths of `processResources`, `Copy`, `Sync`, `copy` and `sync` blocks of build scripts as `gradle.copy-source`
  symbols of the module. A linker joins each to what builds the bundle there: the npm package whose output directory
  holds the path, by `build-model` and probable when a config declares it, by `convention` and heuristic for a
  default; or a Kotlin/JS or Kotlin/Wasm module whose `build/dist`, `build/distributions` or `build/kotlin-webpack`
  holds it, by `convention` and heuristic. The edge is `DEPENDS_ON`, sub-kind `web-bundle`, and needs no bundle on
  disk. Several producers are `artifact.ambiguous-producer`.
- **Occurrences** tie a source range in an artifact to a symbol with a role (definition, reference, call,
  import, type use).
- **Relationships** are typed edges between nodes: `CONTAINS`, `REFERENCES`, `CALLS`,
  `EXTENDS`, `IMPLEMENTS`, `OVERRIDES`, `IMPORTS`, `TYPE_USES`, `ACTUALIZES`, `GENERATES`, `BINDS`,
  `DEPENDS_ON`, `TESTS`, `CO_CHANGES_WITH`. The edge vocabulary is closed because ranking and impact logic
  assign meaning to it; producers may add a namespaced sub-kind.
- **Symbolic references.** A producer reads one file at a time and cannot look a target up in another: an
  `@string/app_name` in a layout, a class named in a manifest. It records a symbolic reference instead, a
  namespaced key with the node it comes from and the place it appears.
  - The boundary linker that claims the namespace answers each reference with exactly one relationship,
    resolved, unresolved or not analysed.
  - The engine reports any reference that a linker left out or answered twice.
- **Metrics.** A metric is a named number on a node, such as `history.commits` on an artifact. The batch that
  holds it names its producer; a metric is never a point in time, so it cannot make a fact depend on the clock.

**Evidence.** Each fact carries:

| Field | Values |
|---|---|
| Evidence class | `compiler`, `build-model`, `generated-artifact`, `content`, `convention`, `structural`, `textual`, `historical`, `ml` |
| Resolution state | `exact`, `probable`, `heuristic`, `unresolved(reason)`, `not-analysed(reason)` |
| Provenance | producer id and version, ownership unit, input content hash or revision |
| Scope | source set, target and variant for which the fact holds |

## Pipeline

```
discover -> build model -> surface synthesis -> fact production -> boundary linking -> history -> persist
```

1. **Discover.** Find the repositories of the root (one, or the checkouts side by side below a plain root), walk
   each, apply its ignore rules, hash content and classify artifacts through the
   detector registry. Once the build model names generated source roots, the files below those that exist on disk
   are inventoried as well, although Git ignores build output; they are marked generated and, for now, not handed
   to producers. A detector may also name output roots, such as the directory a web bundler writes to; their files
   are read the same way, labelled `output:<generator>`, and are handed to producers.
2. **Build model.** `BuildModelProvider`s yield modules, source sets, targets, variants, dependencies and
   generated roots.
   - The static provider needs no build execution and labels what it infers.
   - The Gradle Tooling API provider is exact but executes build logic, so it runs only when explicitly trusted.
   - Compile classpath entries that lie inside the workspace and are not files of the repository (module build
     output, generated `R.jar`) are dropped as they enter the engine, because workspace code is analysed from
     source. A binary that the repository keeps, such as a vendored jar, stays.
3. **Surface synthesis.** Generated APIs (R, BuildConfig, binding classes, AIDL Stub/Proxy, schema APIs)
   become signature-level symbols with `GENERATES` edges to their source of truth, using the generator's
   documented rules (`convention`). Files under modeled generated roots are inventoried but are not used for
   synthesis or fact production, so fresh output does not override the signatures derived from conventions.
4. **Fact production.** `FactProducer`s turn artifacts into symbols, occurrences and relationships.
   Lightweight producers run in-process. Kotlin and Java are analysed by a separate worker process.
5. **Boundary linking.** `BoundaryLinker`s join endpoints across artifacts (resource to consumer, manifest
   to class, `expect` to `actual`, contract to implementer, test to subject) and account for what could not
   be joined.
6. **History.** Churn and co-change signals from Git, when a repository is present: a producer with one repository
   unit, stale when HEAD or the set of file paths changes, that emits per-file metrics and `CO_CHANGES_WITH` edges.
7. **Persist.** Facts are written per ownership unit in one transaction; the search index is refreshed.

### Levels

A scan has a level, and the pipeline above runs at each of them; only what differs is listed.

| Stage | `syntax` | `resolved` (default) | `exact` |
|---|---|---|---|
| Build model | static providers | static providers | Gradle Tooling first, static as the fallback |
| Surface synthesis, in-process producers, linkers | run | run | run |
| Kotlin and Java | `KotlinSyntaxProducer`: declarations only, no analysis session | `KotlinAnalyzerProducer`: declarations and resolution, without classpaths | the same, with the classpaths of Gradle |
| API digests | none | run | run |

An index at `syntax` holds one empty `kscope.level` unit owned by the workspace; the other levels leave no unit, and
`exact` shows in the build model it stores. Units are keyed by producer, so changing the level reworks exactly the
difference and the linkers run again.

## Progress

The engine and the service report which stage runs and how far it is through a `ProgressListener`.

- The engine has its own, and the service has one of its own (`io.github.krosstack.kscope.service`) that it
  adapts to the engine's, so that a frontend depends on `service` alone.
- A scan reports its stages, from discovery to the search index, with counts of files, source sets and units;
  the queries of `service` report loading the index, ranking, estimating or compiling context.
- The listener is optional and does nothing by default, so it never changes a result, and its events may arrive
  from any thread.
- A frontend decides how to show them: the CLI draws a status line on a terminal and writes plain lines
  elsewhere, always on standard error.

## Query cost

A query decodes the symbols, relationships, artifacts and structure of the index once and keeps them for the
rest of the command; a scan drops them.

- Lists come back in the canonical order of `core/model`, which is the order of the canonical JSON of each fact.
  The store computes that key with one serializer for all facts and sorts on it, and no order is stored, so the
  schema is unchanged.
- Ranking runs on arrays over the same neighbourhood, with the same sums in the same order, and the facts around
  the top files are gathered in one pass over the relationships.
- A lookup by name does not decode them:
  - the node a command names is found with the indexed queries of the reader;
  - the card of a name or file and the impact of a node are computed on the rows around it (the analysis runs
    again on the rows of every node it newly reached until nothing new is reached, and gives up for the full
    read beyond 2,000 nodes);
  - `info` counts the structure and the diagnostics in the database.
- Whether a workspace is indexed is answered from the index file alone (`IndexProbe`): a missing, empty or
  other-version index is reported as `NotIndexedException` by every query of `service` before anything is opened
  or created.

`kscope serve` removes the part of that cost that is the same for every query. It is the command line kept alive: one
process holds one open `service` instance for a workspace, and a query command that finds it sends its arguments, its
working directory and the properties of its terminal, and prints what comes back.

- The server parses and runs those arguments with the same commands as the command line, in a command tree that
  holds only the served commands, so there is one implementation of every command and its output is the same on
  both paths.
- Requests run one at a time.
- Before each one the server asks SQLite for the data version of the database (`PRAGMA data_version`) and
  forgets what it decoded when another process committed in between. It also lets go of the database when the
  index file is no longer the file it read, because it was deleted or replaced.
- For a scan it closes its instance, lets the scan run as the command line runs it, and opens a new one.
- It is local by construction:
  - the socket is bound to `127.0.0.1`;
  - every request carries a random token that the server wrote to `.kscope/serve.json`;
  - a file lock in the index directory admits one server per workspace
    ([`serve`](cli.md#serve), [security policy](https://github.com/KrosStack/KrosScope/blob/main/SECURITY.md)).

With `--watch` the server also keeps the index up to date.

- An observer thread takes a stamp of the workspace at intervals: a digest of the file listing that a scan would
  use, with the size and modification time of each file, computed in `core/engine` without reading a file or
  opening the database. It only observes.
- When the stamp differs from the one taken before the last scan and has been stable for the quiet period, and
  Git is not in the middle of an operation, the serving thread runs the incremental scan between two requests.
- Automatic scans use stored trust: `level = "exact"` in `kscope.toml` can deepen an existing index to `exact`,
  and an `exact` index stays at that level while trusted. Changed build inputs can run Gradle build logic.
- Trust is read again when the scan starts, including after waiting for a scan slot. If trust is revoked, scans
  of a shallower index stay at or below `resolved`; an `exact` index is left unchanged and reported as behind
  until trust is restored or the user explicitly rebuilds at a lower level.
- The baseline is the stamp from before a scan, so an edit made during a scan is seen as a difference
  afterwards.
- Whether the index was built with trust is read from the producer of its stored build model.
- The state machine has an injected clock and injected sources for the stamp and the Git state, which is how its
  timing, its back-off and its ordering with requests are tested.

## Capability contracts (`core/spi`)

| Contract | Responsibility |
|---|---|
| `ArtifactDetector` | Classify a file into an `ArtifactType`. The only place file names and extensions are inspected. Optionally name the output roots of a build, which discovery reads from disk. |
| `BuildModelProvider` | Produce the module/source-set/target/variant model for a build root. |
| `FactProducer` | Declare consumed artifact types, capabilities and ownership granularity; emit facts. |
| `ApiDigester` | Optional companion of a producer: digest what the analysis of other files can read from a source. |
| `SurfaceSynthesizer` | Derive generated API signatures from a source of truth. |
| `BoundaryLinker` | Join contract endpoints across artifacts; report unresolved boundaries. |
| `SignalProvider` | Contribute a relevance signal (lexical, structural, historical, learned) to ranking. |
| `ContextRenderer` | Render a node at a given level of detail (signature, declaration, body, outline). |
| `TokenEstimator` | Count tokens and truncate a Unicode-safe prefix for a named tokenizer. |

Capabilities are registered in a `CapabilityRegistry` assembled by `service`. Adding support for an
artifact means: register detection, provide a producer and/or linker, declare capabilities, add corpus
tests.

## Analyzer hosting

The Kotlin/Java analyzer links against compiler internals that are large, version-coupled and prone to
classpath conflicts. It therefore runs as a child JVM (`analyzers/kotlin-worker`) that speaks a versioned,
line-delimited JSON protocol carrying the same fact model as in-process producers. Consequences:

- the engine and frontends never link to compiler internals;
- analyzer memory and failures are isolated, and a failed analyzer degrades to `not-analysed` facts
  rather than a failed scan;
- the facts of a source set travel in messages of at most 500 facts, which the client joins again, so that the size of
  a protocol line does not grow with the size of the source set;
- a scan with enough source shares the source sets that need analysis over several worker JVMs that run at the same
  time (`KSCOPE_WORKERS`);
- every worker is given its source sets with all modules they refer to, so each source set is analysed against
  the same modules whatever the number of workers and the index is byte for byte the same;
- version-matched analyzer bundles and third-party indexers can be added without changing the core.

The worker also answers a request that needs no analysis: the **API digest** of a Kotlin source.

- It parses the file without resolving anything and hashes its tokens, leaving out whitespace, comments and the
  bodies that no other file can see: the block bodies of named functions, of property accessors and of secondary
  constructors, and `init` blocks.
- Everything a signature can depend on stays in: expression bodies, property initializers and delegates, default
  values, annotations, lambdas and anonymous functions, and the bodies of `inline` functions and of functions
  and accessors that begin with a `contract` call, under that name or under an alias the file imports it by.
- Two versions of a file with the same digest therefore give every other file the same facts, which a test
  checks against the real analysis for each kind of body.
- A script, a Java source and a file that does not parse have no digest and count with their whole content. The
  worker says so for each of them, so that a source it could not look at is not mistaken for one.
- The engine reads these digests when it decides what an update has to analyse again; see
  [Persistence and incrementality](#persistence-and-incrementality).

## Persistence and incrementality

- SQLite holds the graph, an FTS5 index and history signals.
- Context token costs are computed on demand and cached in the compiler by content hash, source range and
  tokenizer.
- Every fact belongs to an **ownership unit** (artifact or module, per producer).
- An update:
  - hashes the workspace;
  - invalidates the units whose inputs changed, plus conservative dependents (the semantic facts of a changed
    source set, and of the source sets that depend on it when something they can see changed; all linker
    output);
  - reproduces them.
- Build-file changes invalidate the build model and everything derived from it.
- `kscope scan --verify` compares the incremental result with a clean rebuild and fails on any difference.

The fingerprint of a source set unit decides what "can see" means.

- For a producer that names an `ApiDigester`, it holds the content of the unit's own source set and, for every
  other source set in its dependency scope, the API digest of each file instead of its content, together with
  the version of the digester.
- A file without a digest counts with its content, and so does every file for a producer without a digester,
  whose fingerprints are unchanged.
- It also holds the dependencies between the modules of its scope, each with the source set it is scoped to and its
  configuration, because they decide which of those modules the unit sees: `api` becoming `implementation` ends
  the exposure of the modules behind it.
- See [ADR 0014](decisions/0014-api-digests.md).

<details>
<summary>How the API digests are asked for and stored</summary>

- The digests are asked for once per update, before the units are planned, and only for the file contents that
  `api-digests.json` in the index directory does not know yet.
- The file records a digest, or that a content has none, by producer, digester version and content hash.
- A source the digester could not look at is not recorded: it counts with its content in this update and is
  asked about again in the next.
- The file is derived data, pruned to the contents in use, and deleted by a rebuild.

</details>

Token counts are also cached on disk, in `token-counts.bin` in the index directory. The file is derived data,
versioned and independent of the store schema, and `scan --verify` never reads it.

<details>
<summary>What the token-count cache holds and how it is kept</summary>

- It maps the content hash of a file, a source range, a rendering, an estimator id and a digest of the counted
  text to a count. The digest is what makes an entry safe for renderings that are not a function of the file
  alone, such as signatures and outlines.
- Counts of whole rendered packages are kept under the digest of their text.
- A missing, unreadable or foreign-version file counts as empty, and a scan prunes the entries of content that
  is gone.

</details>

The structure report (`kscope info --structure`) is a read-only walk of the directory in `core/engine`:

- file names are recognised only by the marker detectors there;
- the `.git` entries and Gradle settings scripts are read as text;
- nothing is executed, and the service draws the conclusion.

The FTS5 index (`porter unicode61`) holds one document per symbol and one per file, in text that the shared
`CodeTokenizer` split into identifier parts.

The symbol documents of the units an update produced or removed and the file documents of the files whose hash
changed are rebuilt in the same update, and verify mode compares them as well.

## Retrieval, analysis and context

- Task analysis is deterministic: code-aware lexical retrieval (BM25F with symbol and path boosts) seeds a
  weighted graph expansion; structural proximity and history signals are fused into ranked modules, files,
  symbols and tests, each with the signals and paths that justify it.
- Change scope, contract boundaries, exposure and unresolved areas are reported as explainable facts.
- The context compiler selects renderings (signature, declaration, body, outline) by relevance per token under
  an explicit budget, at `minimal`, `recommended`, `deep` and `full` levels, and emits a machine-readable
  package and a readable summary.
- Generated code contributes provenance and signatures; the package points to its source of truth.

The context input and output types live in `core/model`; the `TokenEstimator` contract lives in `core/spi`.

- The service translates retrieval results into neutral context candidates, so the context module has no
  dependency on retrieval.
- The default tokenizer is JTokkit `o200k_base`, with `cl100k_base` and an explicitly named character-count
  heuristic available offline.
- Budgets include the complete rendered Markdown.
- Source snippets use the indexed content hash and exclusive UTF-16 ranges; stale, oversized, unreadable and
  binary sources are omitted with explicit reasons.

Budgets below 8,000 tokens use a compact policy:

- the package holds raw source only, packed in rank order as whole files and declarations, greedily while the
  budget holds;
- test candidates below the `deep` level, generated candidates, signatures, outlines, boundary maps and warning
  lines are left out;
- the remainder is filled with a line prefix of the best candidate that did not fit.

Budgets of 8,000 tokens and above use the offer-and-upgrade selection described above.

The graph is never exposed as a query language. Public surfaces are the service API and its
serializable results.

## ML layering

| Level | Content |
|---|---|
| L0 | No ML. Always available, always benchmarked. |
| L1 | Cheap local static embeddings as an additional `SignalProvider`. |
| L2 | Optional local code-embedding model. |
| L3 | Optional local reranker or small model. |

Embedding model, vector size and inference runtime are behind `SignalProvider`; nothing in the core
assumes them.

## Scale tiers

| Tier | Scope | In 0.1.0 |
|---|---|---|
| 1 Project | One build workspace | Fully supported |
| 2 Monorepo | Many modules, applications, composite builds | Architecturally supported, validated on real repositories |
| 3 Workspace | Several repositories with cross-repository contracts | Repositories side by side under one root, or registered from elsewhere with `kscope workspace add`, are indexed together and linked by Maven coordinates ([ADR 0016](decisions/0016-multi-repository-workspace.md)), JAR files, native libraries, specs and web bundles ([ADR 0020](decisions/0020-links-through-built-artifacts.md)); each class of link is scored against gold data on a small constructed fixture ([support matrix](support-matrix.md#links-between-repositories)) |

## Frontends

`service` exposes scan, update, info, analyze, context, impact, symbol, related and graph use cases with
serializable request and result types. The CLI renders them as text or JSON. An MCP server, IDE plugin,
CI check or daemon would be further clients of the same API.
