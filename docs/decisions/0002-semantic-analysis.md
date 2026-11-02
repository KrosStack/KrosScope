# ADR 0002: Semantic analysis of Kotlin and Java

Status: Accepted · Date: 2026-10-05

## Problem

Impact analysis needs the declaration each reference binds to, across Kotlin, Java, modules and Kotlin
Multiplatform (KMP) source sets. Name matching cannot choose overloads, follow supertypes or pair `expect` with
`actual`. Analysis must run headlessly, without requiring the project to build.

## Options considered

1. **In-process hosting.** Duplicate coroutine classes between the IntelliJ fork and official
   kotlinx.coroutines; a non-daemon thread keeps the JVM alive.
2. **A parse-only syntax tree plus a home-grown resolver.** Duplicates the compiler.
3. **Build-time indexers** (SemanticDB or SCIP compiler plugins, a KSP processor). They need a successful build
   of the project and are version-coupled to it.
4. **Tree-sitter.** No resolution.
5. **Language-server extraction.** Alpha, not designed for batch extraction.
6. **Kotlin Analysis API in standalone mode, in a child JVM.** Chosen.

## Evidence

A feasibility experiment on Windows 11 / JDK 17 with Kotlin Analysis API 2.4.10 ran headlessly.

- Resolved: Kotlin to Kotlin across modules, Kotlin to Java and Java to Kotlin; overloads at all 8 tested call
  sites; supertypes and overrides, including a Java class overriding a Kotlin property; `actual` to `expect`
  (the reverse must be derived).
- Version: 2.4.10 works. The next patch release, 2.4.20, failed at session creation with a `NoSuchMethodError`.
  That supports pinning and isolation.
- Availability: the `-for-ide` artifacts are not on Maven Central; they come from JetBrains'
  `intellij-dependencies` repository.
- Hosting: the JVM stayed alive after analysis (still running after 150 s). The coroutine fork and official
  library share 829 classes; either alone broke a dependency.
- Modelling: without each dependency's `dependsOn` closure, 13 references stayed unresolved.
- Degradation: 20 unresolved references at baseline, 41 without the standard library, 65 without it and the JDK.
  No crash.
- Measurements, noisy because other builds were running: 400 files and 136,000 references took 24.6 s cold,
  12.8 s warm and 4.4 s on 8 threads, peak heap 1.3-1.5 GB. Parallel output matched sequential.
- Licences: artifacts declare Apache-2.0. The compiler jar embeds third-party code with no declared licence
  (for example JNA, Guava, ANTLR); it needs a `NOTICE` review before binary distribution.

## Decision

- Kotlin and Java are analysed with the Kotlin Analysis API in standalone mode (the engine behind KSP2, Dokka 2
  and Detekt 2), pinned to one exact version behind an adapter inside `analyzers/kotlin-worker`.
- The analyzer runs in a child JVM speaking a versioned, line-delimited JSON fact protocol; the engine never
  links compiler internals. The process terminates itself explicitly after flushing output.
- One analysis module per source set, with multiplatform language support and `dependsOn` edges kept; regular
  dependencies expand with each dependency's `dependsOn` closure. Java and Kotlin files of a source set
  share a module. The JDK and libraries are binary modules when a classpath is known.
- Certainty is the compiler's verdict: a resolved reference or call is `exact`; an erroneous call with
  candidates is at most `probable` or `heuristic`; anything else is `unresolved` with a reason code.
  Declarations come from the syntax tree independently of resolution, so they survive failures. A missing
  classpath yields more unresolved references, not a failed scan.
- `expect` to `actual` links invert the compiler's `actual` to `expect` lookup.
- The pinned version changes only after the conformance suite (the reference-workspace gold tests) passes. The
  supported window is stated, not assumed.

## Consequences

- Sessions are per source set; large workspaces need a multi-GB heap budget.
- Each Kotlin line carries a maintenance obligation to re-test and re-pin.
- Non-JVM KMP targets are best-effort.
- Android SDK classes and compiler plugins are not yet validated and are tracked as follow-up.

## Reversal cost

Medium. The adapter and protocol isolate the choice, but all Kotlin and Java facts depend on it.
