# ADR 0007: Generated API surfaces

Status: Accepted, implemented with `convention` evidence only: files under generated roots are inventoried but not used for synthesis, so fresh output does not take precedence over the rules · Date: 2026-10-05

## Problem

Hand-written code calls APIs that exist only after a generator runs: Android `R`, `BuildConfig`, view-binding
classes, AIDL `Stub` and `Proxy`, and schema-generated APIs such as SQLDelight and proto. Without them, the
references dangle and errors cascade through member access. Their bodies are noise for an AI reader; what
matters is the source of truth that produced them.

## Options considered

1. **Require a successful build and index the generated sources.** Needs a build, a toolchain and an SDK, and is
   stale after edits.
2. **Ignore generated code.** References dangle and errors cascade through member access.
3. **Textual name matching** between consumers and sources of truth. Heuristic, with fan-out risk.
4. **Binary or virtual declaration providers inside the compiler session.** Tighter coupling to unstable
   internals.
5. **Signature-level surfaces synthesised from the source of truth and registered as stub sources.** Chosen.

## Evidence

The feasibility experiment behind [ADR 0002](0002-semantic-analysis.md), run on Windows 11 / JDK 17 with Kotlin
Analysis API 2.4.10, tested this mechanism. Without stubs, references to `R.string.*`, `BuildConfig.*` and an
AIDL `Stub.asInterface` were unresolved (8 of 9 references in a Kotlin caller, 11 in a Java caller). After
signature-only stubs were registered, none were unresolved, for three arrangements: Java stubs in a separate
module, Java stubs as an extra source root, and Kotlin stubs. Kotlin and Java callers both resolved.

## Decision

- Generated APIs become signature-level symbols with a `Generated` origin and a `GENERATES` edge from their
  source of truth: a resource file, `.aidl`, `.sq`, `.proto` or build script.
- `SurfaceSynthesizer`s derive them from fresh generator output found in the workspace (`generated-artifact`
  evidence) or, failing that, from the generator's documented naming rules (`convention` evidence). The
  evidence class always says which.
- Synthesizers also emit signature-only stub sources, registered as an additional source root of the analyzer
  session. The compiler binds consumer references to them; names are not matched textually.
- Bodies of generated code never enter AI context. Context shows the source of truth and, at most, the
  generated signature. Generated files on disk are inventoried as generated artifacts.
- A reference to a generated API is a two-hop path with separate evidence: consumer to generated symbol
  (`compiler`), then generated symbol to source of truth (`convention` or `generated-artifact`). A reader can
  tell a compiler-bound hop from a convention-derived one.

## Consequences

- Every generator needs a rule set owned across generator versions, plus a corpus test.
- A surface is claimed only for generators that have such a test. For others, references stay unresolved with
  a reason code instead of being guessed.
- Rule drift is a known maintenance cost: a generator release can change names or shapes that a convention
  rule assumes. Fresh generator output, when present, takes precedence over the rules.
- Stub sources reach the analyzer through the session contract in ADR 0002, so no new channel is needed.

## Reversal cost

Low to medium. Synthesizers sit behind the `SurfaceSynthesizer` contract and stubs are one additional source
root, so the mechanism can be replaced or narrowed generator by generator.
