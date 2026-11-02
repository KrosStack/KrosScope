# Code Guidelines

The goal is code a newcomer can read top to bottom without commentary. Detekt enforces what it can;
review enforces the rest. There is no Detekt baseline.

## Readability without comments

- Names state intent. A function is named for what it returns or achieves, a boolean for the question it
  answers. If a comment would explain *what* code does, rename or extract instead.
- Comments are reserved for a non-obvious *why*: a constraint, an upstream bug, a measured trade-off.
  KDoc is written only for public API whose contract is not evident from its name and types.
- No commented-out code. No `TODO` without a linked issue; none at all for required behaviour.
- One level of abstraction per function. Extract until each function reads as a short list of steps.
- Limits: function body <= 40 lines, <= 5 parameters, nesting depth <= 3, cyclomatic complexity <= 10,
  file <= 400 lines. Hitting a limit is a design signal, not a formatting problem.
- Prefer expressions and early returns to mutable accumulators and nested branches.

## Files and packages

- A class, object or interface with behaviour has its own file, named after it. "With behaviour" means that it
  declares two or more functions.
- Types without behaviour may share a file named for the family: a sealed hierarchy, a group of small data or
  value classes, the nodes of one syntax tree. Private helpers stay in the file of their only user, and types
  that share a file-private declaration stay together.
- A package directory holds at most 20 Kotlin files, in sources and in tests. Beyond that it is split by
  feature, into packages named for what the code does. No `util`, `common`, `helpers`, `models` or `impl`
  packages.
- A test lives in the package of what it tests.
- A `public` declaration of a library module does not change package: its package is part of the contract.
  For that reason the root package of `core/model` is the one directory allowed more, at most 25 files.
- The build enforces the directory limit (`verifyPackageSize`) and the one-type rule (the Detekt rule
  `SingleBehavingType`).

## Types

- Value classes for identifiers and units (`SymbolId`, `TokenCount`); never pass raw `String`/`Int` across
  module boundaries for these.
- Sealed hierarchies for finite states and outcomes. `when` over them is exhaustive without `else`.
- Immutable data: `val`, read-only collection types, `data class` for values. No public mutable state.
- Expected failures are values (sealed results), not exceptions. Exceptions signal programmer error or
  unrecoverable I/O and are caught at process boundaries only.
- No nullable to mean "failed"; `null` means "absent" and nothing else.

## Architecture

- Library modules use Kotlin explicit API mode. Default visibility is `internal`.
- Module dependency rules in `docs/architecture.md` are binding.
- File names and extensions are inspected only inside `ArtifactDetector` implementations. Everything else
  works on `ArtifactType`, facts and symbols.
- No speculative abstraction: an interface exists when there are two implementations or a real boundary
  (process, module, capability).
- No reflection outside the composition root. No service locators; dependencies are constructor parameters.
- Coroutines only where concurrency is measured to matter, always structured, never `GlobalScope`.

## Determinism

- Facts and outputs are sorted by a stable key before they leave a component. Never rely on hash iteration
  order.
- No wall-clock time, random values, absolute paths or host names inside facts.
- Paths in facts are normalized, repository-relative, `/`-separated.

## Evidence discipline

- Every emitted fact names its producer, evidence class and resolution state.
- A producer never upgrades precision it did not establish. When unsure, emit `heuristic`, or `unresolved`
  with a reason code.

## Tests

- JUnit 5 with `kotlin.test` assertions. Test names are sentences in backticks stating behaviour.
- Extractors are tested against checked-in inputs with golden outputs; relationship claims are tested
  against the reference workspace gold sets.
- Tests are deterministic and offline. No sleeps, no network, no dependence on execution order.

## Dependencies

- Declared only through the version catalog. Permissive licences only (Apache-2.0, MIT, BSD, EPL-2.0 for
  test scope); anything else needs an ADR.
- Prefer an existing library to new code for parsing, resolution, storage and tokenization.

## Build hygiene

- Kotlin warnings are errors. Detekt (with formatting rules) and tests must pass before a change is done.
- Owned production code is Kotlin. Any other language needs an ADR.
