# Real-repository validation

How `kscope` behaves on two public repositories it was not developed against, what was fixed because of it, and what
is still wrong. Everything below was measured; nothing is extrapolated unless it says so.

## Summary

- **Scans complete.** Both default scans complete without a crash, a hang, a timeout or a protocol failure, before
  and after the changes described here. A rebuild of the 310 Kotlin files of Now in Android takes 20 to 29 seconds
  on the machine described below (one cold start took 60 seconds).
- **Defects.** The scans did expose defects. They are fixed and covered by regression tests (see
  [Defects found](#defects-found-and-fixed)).
  - Two of them silently dropped references because a compiler session had no Java support (667 references in one
    source set of the default scan of KotlinConf, 2,028 in files of its trusted scan).
  - One dropped symbols and attached references to the wrong declaration (private top-level names shared by two
    files).
  - Four made the module graph or the source set hierarchy wrong without a diagnostic.
- **The most important limitation** was a build whose conventions live in plugin classes, as in Now in Android.
  - None of its Android modules got an Android trait, namespace, target or variant, and the project dependencies
    that its convention plugins add were missing, so references from one module into another were unresolved (112
    imports of workspace declarations).
  - The static build model now reads such classes from their syntax, see
    [Convention plugins implemented as classes](#convention-plugins-implemented-as-classes): 30 Android modules
    with namespace and target, 19 more project dependencies, 3 such unresolved imports.
  - What a plugin computes stays unread and is reported. The sections before that one describe the state before
    the change.
- **Without `--trust-build`** the compiler has no library classpath: 58 to 61 percent of the stored relationships
  are `unresolved` with the reason `no-classpath`.
  - The trusted scan, tried on one repository, lowers that to 21 percent.
  - It needs the JDK the build requires, three to four times the analysis time and about 3 GB for Gradle and the
    worker together.

## Environment

| | |
|---|---|
| OS | Windows 11 Pro, build 26300 |
| CPU, memory | 8 cores / 16 threads; 32 GB, shared with other builds (1 to 7 GB were free during the runs) |
| JDK running `kscope` | Oracle JDK 17.0.12 for the default scans; Amazon Corretto 21.0.11 for the trusted scans |
| Worker | Kotlin Analysis API 2.4.10, default heap `-Xmx2g` |
| `kscope` | 0.1.0-SNAPSHOT, commit `92a78ec` ("before") and the same commit with the changes of this validation ("after") |
| Gradle (trusted scan only) | 9.6.1, the distribution the repository's wrapper declares |

"Before" and "after" builds were made from clean exports of the commit, so no unrelated work is in them. Wall times
are those of a shared machine and vary by a factor of two between runs of the same build; the worker memory figures
are the peak working set of the worker process, sampled every 250 ms.

## Repositories

Both were cloned with `--depth 1`.

| | Now in Android | KotlinConf app |
|---|---|---|
| Source | `android/nowinandroid` | `JetBrains/kotlinconf-app` |
| Commit | `a49ed253d75e61a2b6ab80a8da677b57437b08eb` | `248474dd27e1f84dadf309618a4e0fa82d7e9bfb` |
| Kind | Android, 35 included projects, convention plugins in an included build | Kotlin Multiplatform: Android, JVM, iOS (arm64, simulator), JS, Wasm; one JVM server module |
| Tracked files | 715 | 766 |
| Kotlin files / lines | 310 / 29,269 | 260 / 21,879 |
| Java files | 0 | 0 |
| Kotlin scripts / lines | 40 / 1,748 | 11 / 774 |
| Modules in the index | 37 (root, 35 projects, the `build-logic` build) | 8 |

## Default scan

`kscope scan --rebuild`, then `kscope info --format json`.

<details>
<summary>Measurements of each scan, before and after the changes</summary>

Where a cell has two values they are two consecutive runs (first / second).

| | NiA before | NiA after | KotlinConf before | KotlinConf after |
|---|---|---|---|---|
| Result | success | success | success | success |
| Wall time, s | 23.2 / 28.9 | 59.9 (cold) / 20.2 | 43.6 / 51.4 | 18.1 / 21.9 |
| discovery, ms | 501 / 298 | 514 / 290 | 565 / 558 | 356 / 149 |
| build-model, ms | 1,624 / 1,585 | 4,151 / 1,381 | 1,356 / 4,885 | 1,107 / 1,115 |
| source-sets + synthesis, ms | 42 / 45 | 55 / 42 | 35 / 148 | 33 / 34 |
| production (worker), ms | 18,958 / 23,153 | 51,614 / 16,627 | 37,801 / 34,797 | 14,884 / 18,760 |
| Worker peak, MB | 826 / 916 | 693 / 740 | 1,171 / 914 | 774 / 751 |
| `kscope` peak, MB | 448 / 482 | 421 / 437 | 465 / 463 | 417 / 422 |
| Units produced | 70 | 70 | 26 | 26 |
| Artifacts | 715 | 715 | 766 | 766 |
| Modules / source sets | 37 / 72 | 37 / 72 | 8 / 48 | 8 / 48 |
| Targets / variants | 2 / 0 | 2 / 0 | 24 / 2 | 24 / 2 |
| Symbols | 2,323 | 2,324 | 2,299 | 2,303 |
| Relationships | 19,462 | 19,462 | 19,658 | 20,010 |
| exact / probable | 8,010 / 78 | 8,010 / 78 | 7,389 / 59 | 7,746 / 64 |
| unresolved `no-classpath` | 11,358 | 11,358 | 12,193 | 12,183 |
| unresolved `ambiguous-reference` | 16 | 16 | 17 | 17 |
| Diagnostics: errors / warnings / infos | 0 / 1 / 126 | 0 / 0 / 126 | 0 / 6 / 17 | 0 / 0 / 18 |

</details>

- The time differences between the "before" and "after" columns are the load of the machine, not an effect of the
  changes: the worker does the same analysis.
- The cold 59.9 s is the first run after the build of a new `kscope`.
- The worker peak is 130 to 400 MB lower after the change, probably because the worker now sends each source set
  when it is finished instead of holding the batches of the whole request. With samples this few and a loaded
  machine that is an observation, not a measurement of the effect.

Relationships cover compiler facts and build-model facts (module dependencies, catalog references). The unresolved
share is therefore 58 percent (Now in Android) and 61 percent (KotlinConf) of everything stored.

Diagnostics by code (after):

| Code | NiA | KotlinConf |
|---|---|---|
| `gradle.static.plugin-not-analysed` (info) | 91 | 15 |
| `gradle.static.layout-inferred` (info) | 34 | 0 |
| `gradle.static.dependency-not-analysed` (info) | 0 | 2 |
| `discovery.artifact-too-large` (info; a 26 MB PDF) | 1 | 1 |
| `identity.collision` (warning), before | 1 | 4 |
| `kotlin.reference-resolution-failed` (warning), before | 0 | 2 files, 667 references |

Protocol lines:

<details>
<summary>Line sizes before and after the change</summary>

- Before, a source set travelled as one line: the largest were 8.47 MB (9,834 facts, KotlinConf `:app:shared`
  `commonMain`) and 2.22 MB (2,413 facts, Now in Android `:core:designsystem`), about 860 bytes per fact.
- That is not a problem at this size. It grows linearly, so a source set of 300,000 facts would be a line of about
  260 MB, built three times over in a worker whose default heap is 2 GB; that was not reproduced.
- After the change no message holds more than 500 facts: the largest line is 0.65 MB, and 86 and 61 messages carry
  the 21,051 and 21,909 facts of the two scans.

</details>

## Incremental behaviour

`kscope scan` with nothing changed, then a comment appended to one Kotlin file, `kscope scan --verify`, and the file
restored. The wall time of a `--verify` run includes the clean rebuild that verifies the result.

| | Units produced / skipped | Production, ms | Wall, s | Verification |
|---|---|---|---|---|
| NiA, unchanged | 0 / 70 | 13 | 2.3 | n/a |
| NiA, leaf file (`feature:search:impl`) | 7 / 63 | 9,868 | 64.3 | matches |
| NiA, widely used file (`core:model`) | 50 / 20 | 18,978 | 46.7 | matches |
| KotlinConf, unchanged | 0 / 26 | 8 | 3.3 | n/a |
| KotlinConf, leaf file (`:app:webApp`) | 2 / 24 | 4,430 | 26.3 | matches |
| KotlinConf, widely used file (`:core`) | 19 / 7 | 20,864 | 45.8 | matches |

All eight verifications matched. A change that does not alter any declaration still re-produces every source set that
depends on the changed one (50 of 70 units for a comment in `core:model`), because dependents are invalidated by the
content hash of their inputs, not by the public surface of what changed.

## Static build model

Without `--trust-build` the modules and source sets come from the files.

Module discovery. Now in Android: all 35 `include` calls of `settings.gradle.kts` are modules, plus the root project
and the included `build-logic` build. KotlinConf: all 8 includes. No module is missing and none is invented.

Spot checks against build scripts and convention plugins (the plugins of Now in Android were read from
`build-logic/convention`):

<details>
<summary>Module-by-module spot checks</summary>

| Module | Right | Wrong or missing |
|---|---|---|
| NiA `:core:data` | 4 plugin ids (three aliases and `id("kotlinx-serialization")` as the serialization plugin); 6 main and 4 test dependencies with the right configuration and scope, catalog versions resolved | Android library trait, the namespace that the script states literally, the Android target: `nowinandroid.android.library` is a plugin class, "plugin not analysed" (info) |
| NiA `:feature:foryou:impl` | plugins, the project dependencies in the script | `core:ui` and `core:designsystem`, which the `AndroidFeatureImplConventionPlugin` class adds with `project(...)`; all references into them are unresolved |
| NiA `:app` | source sets `main`, `androidTest`, `benchmark`, `debug`, `prod`, `testDemo` equal to the directories | flavors, build types and variants (0 variants), namespace, application id |
| NiA `:lint` | `java-library` and `kotlin("jvm")` give traits `kotlin-jvm`, `java`, certainty exact | nothing |
| NiA `:core:datastore-proto` | `proto` source root | the protobuf-generated sources |
| KotlinConf `:core` | 3 plugins, traits, namespace `...kotlinconf.core`, 6 targets, default hierarchy | nothing |
| KotlinConf `:app:androidApp` | plugins, trait, namespace, 2 variants | nothing |
| KotlinConf `:app:shared` | 8 plugins, 6 targets, namespace, custom `nonAndroidMain` and `nonWebMain` | before the changes: the `dependsOn` edges of `configure(listOf(iosMain, jvmMain, webMain)) { get().dependsOn(...) }` were dropped without a diagnostic and both sets had no targets; `npm(...)` notation not analysed (info) |
| KotlinConf `:app:desktopApp` | plugins, traits | before the changes: `project(":app:shared")` inside `kotlin { dependencies { } }` was dropped |
| KotlinConf `:backend` | 4 plugins, `kotlin-jvm` | seven plugin ids that other plugins apply (`application`, two `shadow` ids, `jib`, `distribution`, `distribution-base`, `java`) |

</details>

Labels:

- In Now in Android 35 of 37 modules are `heuristic` (34 projects had their layout inferred from directories and say
  so in an info diagnostic); `build-logic` and `:lint` are `exact`.
- In KotlinConf every module is `exact`, its template source sets `heuristic` and the two declared source sets
  `exact`.
- With plugin classes read, 36 of the 37 modules of Now in Android are `heuristic`: every module that applies a
  convention plugin is.

Static model against the Gradle model of the trusted scan (KotlinConf, after the changes):

- Modules 8 of 8 equal, targets 24 of 24, variants 2 of 2, project dependencies 7 of 7.
- Of the 29 source sets Gradle reports, 28 are in the static model; the hierarchy edges are equal for 27 of 28 and
  the targets for 27 of 28.
- Differences:
  - `:app:adminApp` has only a JS target, where Gradle adds an implicit `webMain` that the static template does
    not.
  - `commonTest` of `:app:shared` is given an Android target that Gradle does not give it.
- The static model adds 20 template source sets that have no directory and that Gradle does not report.
- Of the distinct (module, dependency) pairs, 112 are in both models, 2 only in the static one and 22 only in the
  Gradle one (dependencies that plugins add, such as the Metro runtime and the Kotlin standard library).
- The plugin lists differ in the same way: ids that other plugins apply appear only in the Gradle model.

## Convention plugins implemented as classes

Now in Android implements its conventions as `Plugin<Project>` classes of an included build. Each is registered with
`gradlePlugin { plugins { register(...) { id = ...; implementationClass = ... } } }`, with ids that come from the
version catalog (`libs.plugins.x.asProvider().get().pluginId`). The static build model reads them as follows, and
labels everything it derives from a class `heuristic`:

- Ids to classes: the `gradlePlugin { }` block of the build scripts of included builds and `buildSrc` (`register` and
  `create`; ids as strings or as catalog entries) and `META-INF/gradle-plugins/<id>.properties` descriptors.
- Class to statements: the worker outlines the Kotlin sources of the registering project.
  - For each class whose supertypes name `Plugin<...>` it records the body of `apply`, with `with(target) { }`,
    `target.run { }` and `target.apply { }` unwrapped.
  - It inlines the functions of the same project that the body calls (parameters declared with their types), to a
    depth of two.
  - Nothing is resolved or run.
- Statements to the model:
  - `apply(plugin = "id")`, `pluginManager.apply("id")`, `plugins.apply("id")`, `apply<Other>()` and
    `apply(Other::class.java)` for a class of the same build.
  - `extensions.configure<T> { }` and `extensions.getByType<T>().apply { }` for the Android extensions (the
    `android { }` rules) and `KotlinMultiplatformExtension` (the `kotlin { }` rules); other extensions are ignored.
  - `dependencies { }` with `"conf"(project(":x"))`, `add("conf", ...)` and `libs.findLibrary("alias").get()` or
    `findBundle`, with locals such as a bill of materials.
- What is not read: a computed plugin id or dependency, a conditional or loop that applies or configures something,
  `pluginManager.withPlugin(...)` blocks, a plugin class that is not part of the build, a call deeper than two
  functions. Each plugin that has such a part gets one info diagnostic,
  `gradle.static.convention-plugin-partially-analysed`, that names it. Nothing in it is guessed.
- Computed product flavors: when a script computes its flavors, the source set directories that are not `main`, a test
  source set or a build type are taken to be flavors (a variant or test directory such as `demoDebug` or `testDemo`
  names the flavor it is made of). One info diagnostic per module,
  `gradle.static.flavors-inferred-from-directories`.

Default scan of Now in Android, `kscope scan --rebuild`, the same tree built without and with the change:

<details>
<summary>Now in Android before and after plugin classes were read</summary>

| | Before | After |
|---|---|---|
| Modules with an Android trait / with a namespace | 0 / 0 | 30 / 30 |
| Targets | 2 | 35 |
| Variants | 0 | 72 |
| Source sets | 72 | 72 |
| Project dependency edges (distinct module pairs) | 105 (97) | 124 (115) |
| External dependency edges | 162 | 448 |
| Relationships | 19,595 | 20,146 |
| exact | 8,123 (41.5%) | 9,112 (45.2%) |
| unresolved | 11,378 (58.1%) | 10,586 (52.5%) |
| unresolved imports / imports | 2,696 / 3,559 | 2,553 / 3,589 |
| unresolved imports that name a declaration of the workspace | 112 | 3 |
| `gradle.static.plugin-not-analysed` | 91 | 18 |
| `gradle.static.layout-inferred` | 34 | 1 (`:benchmarks`, a `com.android.test` module) |
| `gradle.static.convention-plugin-partially-analysed` | 0 | 5 |
| `gradle.static.flavors-inferred-from-directories` | 0 | 5 |
| `kotlin.dependencies-assumed` | 0 | 0 |
| Wall time, s (runs) | 21.3 to 25.0 (3) | 21.9 to 30.6 (6) |
| build-model stage, ms | 1,334 | 2,734 |

</details>

- The wall times are those of a shared machine and overlap. The build-model stage is longer because a second worker
  process outlines the Kotlin sources of the build logic.
- Most of what stays unresolved is the missing library classpath (`no-classpath`: 11,358 before, 10,555 after).
- The five partial plugins are `AndroidLintConventionPlugin` (a `when` on the plugins present),
  `HiltConventionPlugin` (two `withPlugin` blocks), `RootPlugin` (an `if`) and the two Jacoco plugins
  (`apply<JacocoPlugin>()`, a Gradle class).
- The remaining 18 `plugin-not-analysed` are plugins of libraries and of Android (Roborazzi, Spotless, the baseline
  profile and Firebase plugins, `com.android.test`, Room and similar).

KotlinConf does not use plugin classes. Its static build model is identical before and after: 8 modules, 48 source
sets, 24 targets, 2 variants, 7 project dependencies, 111 external dependency edges.

Spot checks of five modules of Now in Android after the change, read against their build scripts and plugin classes:

<details>
<summary>Module-by-module spot checks after the change</summary>

| Module | Right | Wrong or missing |
|---|---|---|
| `:app` | 19 plugin ids, including those that the application, Firebase and Hilt classes apply; application trait; namespace; compile SDK 36 from `configureKotlinAndroid`; Compose feature; 28 project dependencies, equal to the 28 `projects.` references of the script; `ksp` processors | flavors: `benchmark` (a build type that the baseline profile plugin adds), `demo` (from `testDemo`) and `prod` are taken as flavors, so 2 of its 6 variants are wrong |
| `:core:data` | library trait and namespace; 8 project dependencies with the right configurations, equal to the script; `junit`, `kotlin-test`, tracing and desugaring libraries that `AndroidLibraryConventionPlugin` adds | |
| `:feature:foryou:impl` | `core:ui` and `core:designsystem`, which `AndroidFeatureImplConventionPlugin` adds with `project(...)`; 9 project dependencies in all, each right | |
| `:core:database` | `androidx.room` and `com.google.devtools.ksp` applied by the Room plugin; processors `room-compiler` and `hilt-compiler` | |
| `:lint` | `java-library`, `kotlin("jvm")` and the lint alias; no Android trait | whether `AndroidLintConventionPlugin` applies `com.android.lint` to a JVM module: its `when` is not read and is reported |

</details>

A project dependency that both a script and a plugin class declare is two edges: one `exact` at the script and one
`heuristic` at the class (`:feature:foryou:api` to `:core:navigation`).

Remaining differences from the Gradle model, from reading the classes:

- Flavors. The 25 Android modules whose flavors are computed and have no flavor directory keep two variants, where
  Gradle gives them four; the flavors are only as good as the directories.
- A build type or flavor that a plugin adds, or a source set that a script names by hand, is taken for a flavor when it
  has a directory and the script computes its flavors.
- `withPlugin` blocks and conditionals in plugin classes are not read (Hilt's library dependencies, the lint plugin).
- Classes of Gradle itself (`JacocoPlugin`) and plugins of libraries are not analysed.

## Trusted scan

Tried on KotlinConf only: Now in Android configures its Gradle daemon with `-Xmx4g -Xms4g` and needs a distribution
(9.7.1) that was not installed, and no more than 3 to 5 GB were free.

| Attempt | Result |
|---|---|
| `kscope` on JDK 17 | 63.8 s. The Gradle 9.6.1 distribution was downloaded (the download progress was written to standard output, ahead of the JSON result; fixed). Configuration failed because the Metro Gradle plugin requires Java 21 and the build ran on 17. A warning `gradle.tooling.failed` carries the message, and the scan completed with the static model. Gradle daemon peak 1.6 GB. |
| `kscope` on JDK 21 | 145.7 s: build-model 94.0 s (first resolution of plugin and library artifacts), production 45.9 s; worker peak 1,429 MB, Gradle daemon 1,437 MB, `kscope` 423 MB. Exact model of 8 modules, 29 source sets, 24 targets, 2 variants. 24 files reported `kotlin.reference-resolution-failed` (2,028 references); fixed, see below. |
| `kscope` on JDK 21, after the fix | 71.1 s with the cached model: production 66.8 s, worker peak 1,435 MB, 0 warnings. |

Classpath coverage:

- The model plugin reports classpaths of JVM and Android compilations only.
- The analysis groups of the Android, JVM and common source sets receive 62 to 170 entries; the JS, Wasm and Native
  groups receive none, so their libraries stay unresolved (920 relationships with `no-classpath`, 498 of them in
  `:app:adminApp`).
- A common source set receives the union of the JVM and Android classpaths that include it.

Unresolved after the fix (compiler relationships; edges to library declarations are not stored, only 31 Maven and 76
other-ecosystem edges were):

| | Static | Trusted |
|---|---|---|
| Compiler relationships | 19,732 | 10,479 |
| exact | 7,468 (37.8%) | 8,153 (77.8%) |
| probable | 64 | 11 |
| unresolved | 12,200 (61.8%) | 2,188 (20.9%): 1,268 `unresolved-reference`, 920 `no-classpath` |

The 1,268 unresolved references with a classpath are mostly generated code: `Res`, `UiRes`, `string` and `drawable`,
the accessors that the Compose resources plugin generates. The totals differ because resolved library references are
not stored.

## Precision of workspace references

Six files, three from each repository: a view model, a data-layer class and a file of shared model code. Every
reported `exact` edge to a workspace declaration was checked against the source by reading it; missing references were
looked for by comparing identifiers with the names of workspace symbols.

| File | `exact` workspace edges | Wrong | Missing workspace references |
|---|---|---|---|
| NiA `SearchViewModel.kt` | 56 | 0 | 14: `AnalyticsHelper` (2), `AnalyticsEvent`, `Param`, `logEvent`, `topics`, `newsResources` and 3 imports, all unresolved; 4 named arguments of unresolved constructors with no edge at all |
| NiA `OfflineFirstNewsRepository.kt` | 76 | 0 | 2: `shouldHideOnboarding`, `followedTopics`, unresolved because the type came from an unresolved `Flow.first()` |
| NiA `UserNewsResource.kt` | 35 | 0 | 0 |
| KotlinConf `SessionViewModel.kt` | 27 | 0 | 0 |
| KotlinConf `YearlyStorageImpl.kt` | 100 | 0 | 0 |
| KotlinConf `ConferenceInfo.kt` | 12 | 0 | 0 |

- 167 of 167 `exact` edges in Now in Android and 139 of 139 in KotlinConf were correct, including overload choice
  (`asEntity(this:NetworkNewsResource)` against `asEntity(this:NetworkTopic)`) and private top-level names of the
  same file.
- Of the workspace references that the sample files contain, 16 of 183 in Now in Android were missing, all of them
  through the module dependency that the convention plugin adds (`core:analytics` through `core:ui`) or through a
  library type that the compiler could not resolve without a classpath; none in KotlinConf.
- The sample is small and chosen from files with many references.

Across the whole index, unresolved imports whose name is a workspace declaration number:

- 112 in Now in Android (mostly from feature modules into `core:designsystem`, `core:ui`, `core:analytics` and
  `core:navigation`; the others are from flavor source sets that cannot see their own `main` and from `:app` into
  `:ui-test-hilt-manifest`), and 3 after plugin classes were read.
- 41 in KotlinConf before the changes and 2 after.
  - The 39 that were fixed were 36 from `:backend` to `:core` and 3 from `:app:desktopApp`.
  - The 2 left are from `webMain` of `:app:webApp` into `webMain` of its dependencies.

## Defects found and fixed

Each has a regression test built from a minimal sample.

| Defect | Effect | Fix |
|---|---|---|
| JS, Wasm and Native source sets were given the JDK | The compiler session of such a platform has no Java support. A reference that reached a Java class through a standard library alias (`HashMap`, `ArrayList`) failed with a missing `FirSyntheticPropertiesStorage`, and every later reference through a Java class failed too: 667 references in `:app:adminApp`. | The JDK is a dependency of JVM and Android modules only. |
| Common source sets with a classpath | The classpath of a common source set holds JVM libraries with Java classes. The same compiler failure: 2,028 references in 24 files of the trusted scan. | A common module with a classpath is analysed as JVM code. `expect`/`actual` pairing is unchanged (tested). |
| Private top-level functions and properties with one name in two files | `identity.collision`: the second declaration was dropped, and its references pointed at the first (exact, wrong). 1 and 4 cases. | Their identity carries the file (`NameKt`), as the JVM facade class does. |
| `kotlin { dependencies { } }` | The project dependencies of a module were dropped without a diagnostic. | Read like the top-level `dependencies` block. |
| `configure(listOf(a, b)) { get().dependsOn(c) }` | Hierarchy edges dropped without a diagnostic, and the declared source sets had no targets, so they were analysed as JVM. | The form is read. A `dependsOn` that no source set block owns now raises `gradle.static.custom-hierarchy-not-analysed`. A declared source set gets the targets of the source sets that depend on it. |
| A JVM module depending on a Kotlin Multiplatform module with no `jvmMain` files | No dependency at all: 36 imports from `:backend` to `:core` unresolved. | The JVM target of such a module compiles `commonMain`, which is now the fallback. |
| One protocol line per source set; unbounded line reads | See the line sizes above. | A batch travels in messages of at most 500 facts (protocol version 2) that the client joins; the worker sends each source set when it is done; a line over 64 Mi characters is rejected as unreadable instead of being read into memory. |
| Download progress of the Gradle distribution on standard output | `--trust-build --format json` printed `Fetching distribution.`, a URL and a line of dots before the JSON. | Standard output is diverted to standard error while the Tooling API runs. Covered by a unit test of the diversion; not re-checked against a live download. |

## What works

- No scan failed, hung or ran out of memory; the worker process stayed below 1.2 GB (default) and 1.5 GB (trusted)
  with a 2 GB heap.
- An unchanged second scan skips every unit (2 to 4 seconds), and all eight `--verify` runs matched a clean rebuild.
- Every checked `exact` workspace edge was correct.
- A failing Gradle configuration degrades to the static model with a diagnostic that names the cause.

## Known limitations observed

- Convention plugins implemented as classes are read from their syntax only: what they compute, branch on or apply
  through `withPlugin` is not read and is reported, and flavors that a script computes are guessed from directories,
  see [Convention plugins implemented as classes](#convention-plugins-implemented-as-classes).
- The reason `no-classpath` is given both for a library that is not on a classpath and for a workspace declaration in a
  module that the model does not connect; the two cannot be told apart in the index.
- Without a trusted scan, more than half of the relationships are unresolved.
- Trusted scans run Gradle on the JVM of `kscope`; a build that needs another Java fails and falls back.
- JS, Wasm and Native libraries never reach the analyzer, and generated code (Compose resources, and in general
  anything under `build/`) is not available.
- A common or intermediate source set of a consumer sees the `commonMain` of its dependencies, not their intermediate
  source sets (2 imports in KotlinConf).
- Analysis throughput is about 1,200 to 1,800 Kotlin lines per second on the default path and about 330 to 480 with
  classpaths (production stage only).
- The request that carries every module with its file list and classpath is still one line; from the paths it
  contains it is of the order of 0.3 MB for KotlinConf.

## Reproduction

```
./gradlew :cli:installDist
git clone --depth 1 https://github.com/android/nowinandroid
git clone --depth 1 https://github.com/JetBrains/kotlinconf-app
kscope --workspace nowinandroid scan --rebuild
kscope --workspace nowinandroid --format json info
kscope --workspace nowinandroid scan --verify
JAVA_HOME=<a JDK 21> kscope --workspace kotlinconf-app scan --rebuild --trust-build
```

The index of a workspace is read with any SQLite client from `<workspace>/.kscope/index.db`.
