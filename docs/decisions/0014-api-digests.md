# ADR 0014: API digests for incremental analysis

Status: Accepted · Date: 2026-10-08

## In short

- A producer may name an `ApiDigester`. Two versions of a source with the same digest give every other file the
  same facts.
- The fingerprint of a source set unit holds the content of its own source set and the API digests of the other
  source sets in its dependency scope.
- An edit inside a function body analyses one source set again instead of the edited one and all its dependents.
  The tables under Evidence give the counts and the wall time of `scan`.
- The first scan after this change analyses everything once. `scan --verify` remains the check that an updated
  index equals a clean one.

## Problem

An update analyses again every unit whose fingerprint changed ([ADR 0006](0006-persistence-and-incrementality.md)).
The facts of a source set depend on the sources it is analysed against, so the fingerprint of a source set unit held
the content of every file of every source set in its dependency scope.

That is safe and wasteful. Editing one function body in a module near the bottom of the dependency graph
analysed again every source set above it, although nothing those source sets can see had changed. On a large
workspace one edited file cost almost as much as a scan, which rules out keeping an index fresh while someone
works.

The fingerprint has to stay safe. An index that silently keeps facts that a clean scan would not produce is worse
than a slow one.

## Options considered

1. **Keep content fingerprints.** Correct by construction, and the cost described above.
2. **Compare the facts of a dependency before and after.** Analyse the edited source set, and analyse its dependents
   only when its declarations changed. It needs units planned and produced in dependency order, a cascade through
   the layers, and a rule for which facts count as visible; cycles between source sets need a special case.
3. **A digest of each source file that covers what other files can read from it, computed from the text alone.**
   Chosen. It is a function of one file, so nothing has to be ordered and nothing cascades: the fingerprint of a unit
   takes the content of its own source set and the digests of the others.
4. **Snapshots of compiled classes**, as build tools use for compilation avoidance. KrosScope does not compile, and
   never runs the build.

## Evidence

What the code does:

- **Contract.** `ApiDigester` in `core/spi` has a `version`, the artifact types it `consumes`, and
  `digest(sources, files)`. The result names the digest of each source that has one and the sources that were looked
  at and have none; a source in neither part was not looked at.
- **Kotlin.** The analyzer worker parses the file without resolving anything and hashes its tokens. See
  [Analyzer hosting](../architecture.md#analyzer-hosting) for what is left out and what is kept.
- **Engine.** Digests are asked for once per update and cached by producer, digester version and content hash in
  `api-digests.json` in the index directory. A producer without a digester has the fingerprints it had before, which
  a test pins.

What was tested:

- A table of edits that must not change the digest and a table of edits that must.
- The promise itself against the real analysis: a module that calls and references another one is analysed, each
  kind of body-only edit is applied to the other module, including one that moves every later declaration down and
  one that documents a referenced declaration, and the complete fact list of the dependent module is compared.
- On a copy of the reference workspace with the real analyzer: after an edit inside a function body one Kotlin unit
  is produced and the index equals a clean rebuild; after a new public declaration the dependents are produced too
  and the index again equals a clean rebuild.

What was measured, on a clone of Now in Android (715 files, 211 work units), for three statements inserted into the
body of one function of a repository class in `core/data`:

| | Work units redone | Source sets analysed | Wall time of `scan` |
|---|---:|---:|---:|
| Content fingerprints | 48 | 37 | 15.3 s |
| API digests | 12 | 1 | 8.6 s |

`scan --verify` after the edit reported that the incremental index matches a clean rebuild. The first scan with the
new fingerprints analysed all 68 source sets once (19.7 s).

On a private Kotlin codebase roughly twelve times larger, the same kind of edit in a file that most of the codebase
depends on:

| | Work units redone | Source sets analysed | Wall time of `scan` |
|---|---:|---:|---:|
| Content fingerprints | 51 | 40 | 212 s |
| API digests | 12 | 1 | 20 s |

`scan --verify` reported a match there as well. Both builds were timed one after the other on the same machine; the
times of one run are not a benchmark, and the counts are exact.

## Decision

- A producer may name an `ApiDigester`. Its promise: two versions of a source with the same digest give every other
  file the same facts. A source without a digest counts with its whole content.
- For a producer with a digester, the fingerprint of a source set unit holds the content of the unit's own source
  set and, for every other source set in its dependency scope, the API digests of its files. The version of the
  digester is part of the fingerprint.
- The Kotlin digest is conservative by rule:
  - Only these are left out: the block bodies of named functions, of property accessors and of secondary
    constructors, and `init` blocks, with whitespace and comments.
  - These stay in: expression bodies, initializers, delegates, default values, annotations, lambdas, anonymous
    functions, the bodies of `inline` functions and of functions that begin with a `contract` call.
  - When it is unclear whether another file can see something, it stays in.
- Digests are derived data, cached in the index directory by producer, digester version and content hash, and
  deleted by a rebuild.
- `scan --verify` remains the check that an updated index equals a clean one.

## Consequences

- An edit inside a function body analyses one source set again instead of the edited one and all its dependents.
- An edit to anything that stays in the digest behaves as before.
- The first scan after this change analyses everything once, because every fingerprint of the Kotlin analyzer is
  built differently.
- Java sources, scripts and files that do not parse have no digest; an edit to one of them still analyses its
  dependents again.
- The promise is tested for the listed kinds of bodies against the real analysis, not proved for every construct of
  the language. A rule that turns out to be wrong is corrected by changing the rule and the version of the digester,
  which analyses everything again once.
- What one changed source set costs is now bounded by the analysis session it needs, which still loads the modules it
  refers to; keeping a worker alive between updates is the next step.
