# Development guide

How to build, test and run KrosScope and its benchmarks. The rules for a change are in
[CONTRIBUTING.md](../CONTRIBUTING.md), the module map and dependency rules in
[architecture.md](architecture.md), and the command-line tool is described in [cli.md](cli.md).

## Prerequisites

| Requirement | Why |
|---|---|
| JDK 17 | The build selects a Java 17 toolchain and does not download one, so a JDK 17 must be installed. The `kscope` command runs on JDK 17 or newer. |
| Git | To clone the repository. KrosScope also reads Git history, and the real-repository benchmark works on clones. |
| Network access | The first build downloads Gradle and the dependencies. Running `kscope` needs no network. |
| Android SDK (optional) | Not needed to build or test KrosScope. Needed to run the Gradle build of the reference workspace, which the integration tests, the trusted conformance run and the agent benchmark do. Set `ANDROID_HOME` to the SDK root. |

The repository includes the Gradle wrapper (`./gradlew`, or `gradlew.bat` on Windows), so no separate Gradle
installation is needed.

## Repository layout

| Path | Contents |
|---|---|
| `core/` | The internal representation, capability contracts, SQLite store and scan engine |
| `analyzers/` | Producers and build-model providers, one module per kind of artifact. `kotlin-worker` is the out-of-process Kotlin and Java analyzer, and `gradle-model-plugin` is the small plugin that reports the exact build model |
| `intelligence/` | Retrieval and context compilation |
| `service/` | The frontend-independent API and the default wiring |
| `cli/` | The `kscope` command |
| `benchmark/` | The benchmarks. `reference/` holds the gold data and task packages, `freezes/` the frozen manifests of published measurements |
| `fixtures/reference-workspace/` | TaskTide, the project that the conformance benchmark scans |
| `build-logic/`, `config/detekt/` | Gradle convention plugins and the Detekt rules |
| `tools/detekt-rules/` | The project's own Detekt rule, `SingleBehavingType`, which the build applies to every module |
| `LICENSE`, `NOTICE`, `licenses/` | The licence, the required notices and the licence texts that some components require; they ship in the archives |
| `docs/` | The documentation, including the [third-party licences](third-party-licenses.md) |

The Gradle module paths are in `settings.gradle.kts`, for example `:core:engine`, `:analyzers:kotlin` and
`:cli`. The dependency rules between them are in [architecture.md](architecture.md#module-map).

## Build and check

```shell
./gradlew check
```

This is the command that must pass before a change is done.

- The pre-commit hook in `.githooks` runs it before every commit once you enable it with
  `git config core.hooksPath .githooks`.
- CI runs it once for every pull request into `main`, on Linux. A pull request that changes
  documentation only starts no run, and the Windows run is started by hand.
- A merge into `main` starts one short job that compiles, runs the static checks without the tests and saves
  the Gradle cache, so that the next pull request run starts from it.

In a trial on a clean clone with no extra configuration it built and passed in about 13 minutes; expect the time
to vary with the machine. For each module it:

- compiles with Kotlin warnings treated as errors;
- runs the unit tests, which use JUnit 5 and `kotlin.test`;
- runs Detekt with the rules in `config/detekt/detekt.yml`, both without and with type resolution. There is no
  Detekt baseline;
- runs `verifyModuleDependencies`, which fails when a module declares a project dependency that the rules in
  [architecture.md](architecture.md#module-map) do not allow;
- runs `verifyFileLength`, which fails when a Kotlin source file is longer than 400 lines;
- runs `verifyPackageSize`, which fails when a directory of `src/main` or `src/test` directly holds more than 20 Kotlin
  files (25 for the root package of `core/model`) and names the directory and the count.

The root `check` also checks `build-logic`.

To check one module, or only run its tests:

```shell
./gradlew :core:engine:check
./gradlew :cli:test
./gradlew :cli:test --tests "*ScanCommandTest"
```

The tests of `:cli` run against the installed distribution, so `:cli:test` builds it first.

### Integration tests

Tests tagged `gradle-integration` run the Gradle build of the reference workspace, take minutes, and are
skipped unless you pass the property `kscope.integration`. They live in `:analyzers:gradle`, `:service` and
`:benchmark`, and need the Android SDK and network access.

```shell
./gradlew test -Pkscope.integration=true
```

## Build and run the CLI

```shell
./gradlew :cli:installDist
cli/build/install/kscope/bin/kscope --help
```

On Windows the script is `cli\build\install\kscope\bin\kscope.bat`. The distribution holds the Kotlin analysis
worker in `workers/kotlin/lib`, beside the application. If `kscope` cannot find it, set `KSCOPE_HOME` to the
installation directory; `kscope doctor` reports whether the worker is found.

To run `kscope` on this repository, scan it from its root. The index is written to `.kscope`, which the
repository ignores and which also holds a `.gitignore` of its own:

```shell
cli/build/install/kscope/bin/kscope scan
cli/build/install/kscope/bin/kscope context "<task>" --budget 4000 --format markdown
```

The query commands need the index. Before the first `scan` they fail with exit code 1, print
``error: No index found. Run `kscope scan` first.`` on standard error, and create nothing in the workspace
([cli.md](cli.md#output-formats)). In the trial on a clean clone, the distribution that the build produced
scanned the reference workspace and then answered `analyze` and `context`.

To build an archive for distribution:

```shell
./gradlew :cli:distZip
./gradlew :cli:distTar
```

They write `kscope-<version>.zip` (121 MB) and a `.tar` to `cli/build/distributions/`. Each archive carries
`LICENSE`, `NOTICE`, the [third-party licences](third-party-licenses.md) as `THIRD-PARTY-LICENSES.md` and the
`licenses` directory.

### Refreshing the licences

When a dependency is added, removed or changes version, update [third-party-licenses.md](third-party-licenses.md)
in the same change: its component, version, licence and where it is used. Take the licence from the artifact
itself, as that page explains.

If the licence requires its text to accompany the distribution, add the text to `licenses/` and a notice to
`NOTICE`. A licence outside Apache-2.0, MIT and BSD for what is distributed, or EPL-2.0 in test scope, needs a
decision record ([ADR 0001](decisions/0001-platform-and-licensing.md)).

## The reference workspace

`fixtures/reference-workspace` is TaskTide, a small original project made to contain every relationship that
KrosScope claims to follow:

- Kotlin and Java in the same modules,
- a version catalog and convention plugins,
- Kotlin Multiplatform source sets with `expect` and `actual`,
- Android resources and manifest components,
- product flavors,
- AIDL, SQLDelight, Wire and Room,
- a JNI binding, and
- tests at several levels.

It is a standalone Gradle project with its own wrapper; its
[README](../fixtures/reference-workspace/README.md) lists its modules and requirements.

Its gold data lives outside the scanned tree, in `benchmark/reference/`, and holds the expected build model,
relationships, closed-world edges and development tasks. Do not change the workspace without updating the gold
data, because the conformance benchmark and the test suite compare the two.

## Running the benchmarks

The benchmarks run through `:benchmark:run`, which starts in the repository root and reads the reference
workspace and its gold data from there. The methodology, metrics and policy are in
[benchmark.md](benchmark.md).

| Benchmark | Command | Output |
|---|---|---|
| Conformance, default mode | `./gradlew :benchmark:run --args="conformance --mode default --out <dir>"` | `metrics.json`, `report.md` |
| Localization | `./gradlew :benchmark:run --args="localization --mode default --out <dir>"` | `localization.json`, `localization.md` |
| Context | `./gradlew :benchmark:run --args="context-benchmark --mode default --out <dir>"` | `context-benchmark.json`, `context-benchmark.md` |
| JSON schemas | `./gradlew :benchmark:run --args="schemas --out docs/schemas"` | The files in [schemas](schemas/README.md) |

- Default mode executes no build code. `--mode trusted` runs the Gradle build of the reference workspace so that
  every file compiles against the Android classpath. It needs the Android SDK, and the matching test needs
  `-Pkscope.integration=true`.
- The conformance and localization commands exit with 1 when their gate is not met.
- `ApiSchemasTest` fails when the checked-in schemas differ from the serialized results, so regenerate the
  schemas after you change a result.
- To measure localization on your own tasks, build the CLI, scan the workspace and run
  `kscope benchmark --tasks <dir>`; see [cli.md](cli.md#benchmark).
- The real-repository benchmark (`real-repos-prepare`, `real-repos-run`, `real-repos-report`) and the agent
  benchmark (`agent-tasks-validate`, `agent-run`, `agent-report`) have their own procedures in
  [benchmark.md](benchmark.md#historical-commit-evaluation) and [benchmark.md](benchmark.md#agent-benchmark).
- To time `kscope` on a clone of a real project, see [performance](benchmarks/performance.md#reproducing).

The published results are in [benchmarks](benchmarks/README.md). Their charts are drawn from
`docs/benchmarks/charts/data.json`; after changing a figure there, redraw them with
`python docs/benchmarks/charts/charts.py`, which needs Python 3.9 or newer and no package.

## Troubleshooting

| Symptom | Cause and remedy |
|---|---|
| The build fails on a Kotlin warning | Every warning is an error. Fix the code. |
| Detekt reports a finding | Fix it. There is no baseline, and a suppression needs a stated reason in the change. |
| `verifyPackageSize` fails | A directory holds too many Kotlin files. Split it by feature; see the [code guidelines](code-guidelines.md#files-and-packages). |
| Detekt reports `SingleBehavingType` | A file holds two classes, objects or interfaces that each have two or more functions. Move one into its own file; see the [code guidelines](code-guidelines.md#files-and-packages). |
| `verifyModuleDependencies` fails | A module depends on one that the dependency rules forbid. See [architecture.md](architecture.md#module-map). |
| Gradle cannot find a Java 17 toolchain | Install a JDK 17 where Gradle can find it. |
| A query fails with `No index found` and exit code 1 | The workspace has no usable index. Run `kscope scan` in it first. |
| `scan --trust-build` falls back to the static model with a Java version warning | The build needs a newer JDK than the one running `kscope`. Run `kscope` on that JDK, or set `KSCOPE_GRADLE_JAVA_HOME` to its home directory; see [cli.md](cli.md#--trust-build). |
| The index is unreadable | Run `kscope scan --rebuild`. The index is derived data and can be deleted at any time. |
| A scan fails with a worker error | Run `kscope doctor`. For an out-of-memory error, set `KSCOPE_WORKER_HEAP` (default `2g`) or lower `KSCOPE_WORKERS`; see [cli.md](cli.md#environment-variables). |
| A Gradle run runs out of memory | The repository sets 1,536 MB for the Gradle and Kotlin daemons in `gradle.properties`. Raise it in your own Gradle user properties, not in the repository. |
| Non-ASCII task text is garbled on Windows | Use `--task-file`; see [cli.md](cli.md#text-encodings). |
| Integration tests do not run | They are skipped without `-Pkscope.integration=true`. |
