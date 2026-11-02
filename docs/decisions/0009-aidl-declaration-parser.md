# ADR 0009: Owned AIDL declaration parser

Status: Accepted · Date: 2026-10-05

## Problem

Architecture principle 6 prefers a mature component to new parsing code. AIDL is the one place where the
graph needs a language that no component in the project reads: `.aidl` files declare the interfaces, methods
and parcelables of Android IPC contracts, and the Java surface of [ADR 0007](0007-generated-surfaces.md)
(`IFoo`, `IFoo.Stub`) is derived from them. Symbols, stubs and links need declaration names with exact
positions, the parameter directions and the types as written.

## Options considered

1. **The reference implementation**, the C++ `aidl` tool of the platform build. It is not packaged as a
   library, and owned code outside Kotlin on the JVM is excluded by
   [ADR 0001](0001-platform-and-licensing.md).
2. **The SDK `aidl` binary.** It needs an Android SDK installation and a build step, produces Java rather than
   declarations, and drops source positions.
3. **A standalone JVM parser library.** None under a permissive licence exists.
4. **A parser generator with a grammar of our own.** It adds a build dependency and generated code for a
   grammar that is small, and the reference grammar is flex and bison, not a grammar a generator can load.
5. **An owned declaration parser** for the subset the graph uses. Chosen.

## Evidence

- The reference implementation is the C++ tool in the platform build; the SDK binary is a build-time step that
  needs an SDK. Neither is available to a scan of a checked-out repository, which is the case this engine serves.
- The declaration subset is tiny: a package, imports, interfaces with methods and constants, and unstructured
  or structured parcelables. Annotations, comments and constant expressions need skipping, not understanding.
- The reference workspace and the corpus tests exercise valid files and the malformed inputs a half-edited
  repository contains.

## Decision

- `AidlParser` in `analyzers/android` (package `aidl`) tokenizes and reads declarations by recursive descent:
  package, imports, `interface` with `oneway` and methods (`in`, `out`, `inout`, generic and array types,
  constants), `parcelable` unstructured and structured with fields and constants. Annotations are skipped and
  constant expressions are kept as text.
- The result is sealed. A file that cannot be read yields a diagnostic with the line and column of the problem,
  never an exception. `enum` and `union` declarations are skipped and reported at info level.
- The parser reads declarations and nothing else: it does not check that types exist, assign transaction
  numbers, read versioned interface snapshots or evaluate expressions.

## Consequences

- Language drift is ours to follow. A construct the parser does not know produces a diagnostic and missing
  symbols for that file, never a wrong symbol.
- The parser accepts some files the reference tool rejects, because it does not validate.
- The code is a tokenizer and a handful of declaration readers; the project owns and tests it like any other
  extractor, with the checked-in corpus as the specification of what is supported.

## Reversal cost

Low. Producer, synthesizer and linker depend on the syntax model (`AidlFile` and its declarations), not on the
parser. If a permissively licensed JVM parser appears, or the supported subset grows towards enums, unions and
evaluated expressions, the parser can be replaced behind `AidlParseResult` without touching them.
