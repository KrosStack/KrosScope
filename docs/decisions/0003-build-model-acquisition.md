# ADR 0003: Build model acquisition

Status: Accepted · Date: 2026-10-05

## Problem

Much of a Gradle build's structure is computed by plugins, so reading it exactly means executing settings, build
scripts, build logic and user init scripts. A default scan must not run workspace code.

## Options considered

1. **Built-in Tooling API models only.**
2. **An init script that dumps JSON through a task or file side channel.**
3. **Typed access to plugin classes** from injected code.
4. **Static parsing only.**
5. **Deserialising IDE-internal model classes**, which skew with IDE versions.
6. **Two providers:** static by default, plus a trust-gated Tooling API provider. Chosen.

## Evidence

A feasibility experiment succeeded on Gradle 9.5.1, JDK 17, Android Gradle plugin (AGP) 9.3.3 and Kotlin Gradle
plugin (KGP) 2.4.20; with KGP 2.2.21 instead; and with AGP 9.4.1 on Gradle 9.7.0.

- Built-in models were empty for KMP and Android modules; five requests each reconfigured the build.
- The Kotlin plugin and a Groovy reference returned identical output.
- The side-channel script did not run on configuration-cache hits. Typed plugin access works but is pinned to
  plugin versions at compile time.
- The init-script classloader cannot see KGP or AGP, and Gradle's Kotlin runtime overrides a bundled one.
- On Gradle 9.5.1, one project failing to configure fails the whole request, with no partial project
  models. A missing Android SDK did not fail model fetch.
- Configuration dominates latency; extractor work was 2-16 ms per project. Warm runs had a median of 16 s
  (12.8-37.7 s, n=14); cold single-use daemons took 11.5 s and 18.7 s, measured under load.
- Static output matched on 22 facts and differed on 4. It misses transitively applied plugins (3 found against 8
  in one module) and convention-plugin contributions, and computed a custom hierarchy group wrongly.

## Decision

- **Static provider**: always available, executes nothing. It reads settings, build-script outlines, version
  catalogs, source-visible included-build convention plugins, and directory conventions (Android
  `src/<sourceSet>/`, the KMP default hierarchy template). Literal facts are `exact`; convention- or
  template-derived facts are `heuristic`. Transitive plugins, convention contributions, custom hierarchy groups,
  resolved versions and KSP or generated roots are reported `not-analysed`, never guessed.
- **Gradle Tooling API provider**: `build-model` evidence, opt-in behind an explicit workspace trust gate. A
  Tooling API client injects, by init script, a thin Kotlin plugin jar (Gradle API only; no kotlin-reflect,
  coroutines or serialization; low Kotlin API level) that reaches KGP and AGP by reflection. It registers a
  `ToolingModelBuilder`; one `BuildAction` returns per-project, schema-versioned JSON with per-section error
  lists. Once trusted, the build's own Gradle distribution is used. The init script is inert unless its
  activation property is set.
- The model is cached by a fingerprint of settings and build scripts, `gradle.properties`, version catalogs,
  wrapper properties, included-build and `buildSrc` sources, Gradle version, JDK, extractor hash and schema
  version.
- On configuration failure the Gradle message becomes a diagnostic and the static model is used.

## Consequences

- The supported window is the tested combinations above. Older Gradle and AGP lines are reported untested until
  tests cover them.
- The injected jar is a tiny separate module with its own compatibility constraints.
- Default operation never executes a build.

## Reversal cost

Low to medium. Both providers sit behind the `BuildModelProvider` contract, so either can be replaced without
touching consumers.
