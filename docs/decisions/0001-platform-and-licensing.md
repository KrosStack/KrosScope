# ADR 0001: Platform and licensing

Status: Accepted · Date: 2026-10-05

## Problem

KrosScope analyses Kotlin, Android and Kotlin Multiplatform workspaces. The implementation language, build
system, module structure and licence policy decide which components can be reused and under what terms the
project is distributed. All are costly to change once code exists.

## Options considered

1. **Kotlin on the JVM.**
2. **Kotlin/Native.**
3. **A JVM codebase compiled with GraalVM native image.**
4. **A Rust or Go core** with a separate Kotlin analyzer behind a wire protocol.

## Evidence

- The Kotlin Analysis API in standalone mode is the maintained route to resolved references, calls and types
  outside an IDE. KSP2, Dokka (default since 2.1.0), Detekt 2.0 and Android Lint already run on it
  ([KSP2](https://github.com/google/ksp/blob/main/docs/ksp2.md),
  [Dokka v2.1.0](https://github.com/Kotlin/dokka/releases/tag/v2.1.0),
  [detekt #8021](https://github.com/detekt/detekt/issues/8021),
  [Android Lint API guide](https://googlesamples.github.io/android-custom-lint-rules/api-guide.html)).
  It is a JVM API, as are the Kotlin PSI parser and the Gradle Tooling API.
- The surrounding tooling (KSP, Detekt, Dokka, scip-java, Tree-sitter grammars) is Apache-2.0 or MIT, and
  SCIP is Apache-2.0 ([scip-code.org](https://scip-code.org)).
- The licence of the standalone Analysis API artifacts and of the shaded IntelliJ components they contain has
  not been confirmed against published POM metadata, and the artifacts are not on Maven Central
  ([KT-56203](https://youtrack.jetbrains.com/issue/KT-56203)). They need a licence review before
  distribution.
- Reasoning, not measurement: Kotlin/Native cannot load JVM-only libraries; native image of code that embeds
  compiler internals is likely to need reflection configuration; a Rust or Go core would still need a JVM for
  every Kotlin fact. The JVM 17 floor is a compatibility choice.

## Decision

- Owned code is Kotlin on JVM 17, built with Gradle Kotlin DSL and convention plugins.
- The modular layout in `docs/architecture.md` is adopted, and its dependency rules are enforced by the
  build, not by review.
- The project is licensed under Apache-2.0.
- Dependencies are limited to permissive licences: Apache-2.0, MIT and BSD, plus EPL-2.0 in test scope only.
  Any other licence requires an ADR.
- First-party production code in a language other than Kotlin requires an ADR.

## Consequences

- Running KrosScope needs a JVM 17 or newer runtime, and each CLI invocation pays JVM start-up cost (not
  measured).
- A module that imports across a forbidden boundary fails the build.
- Redistributed third-party code needs its licence text and a `NOTICE` entry.
- The Analysis API licence question gates distribution of anything that bundles it.

## Reversal cost

Language: high. All code, tests and build logic would be rewritten, and a non-JVM core would still need a
protocol to a JVM analyzer. Module boundaries: low, because they live in build files and can be split
or merged mechanically. Licence: high once external contributions exist, since relicensing needs contributor
agreement.
