# ADR 0004: Symbol identity

Status: Accepted · Date: 2026-10-05

## Problem

Every fact refers to symbols, and producers of different precision (parse-only, compiler-resolved,
generators) describe the same declarations. Identifiers must be agreed by all producers, stable when
unrelated code changes, unique across modules, source sets and repositories, and independent of the checkout
location.

## Options considered

1. **Path and range identifiers.**
2. **Plain fully qualified names.**
3. **SCIP symbol strings verbatim.**
4. **JVM descriptors** to separate overloads.
5. **SCIP-style strings with a syntax-computable disambiguator and a source-set part.** Chosen.

## Evidence

- SCIP symbol strings and the occurrence model are the identity and interchange format to adopt. SCIP is a
  small Apache-2.0 protobuf with independent consumers, including Glean, Mozilla Searchfox and rust-analyzer
  ([scip-code.org](https://scip-code.org)), and Glean ingests it
  ([Meta Engineering](https://engineering.fb.com/2024/12/19/developer-tools/glean-open-source-code-indexing/)).
- CodeNib joins mixed-precision sources on a repository-relative source address plus the commit, with any
  backend symbol ID as metadata ([CodeNib](https://arxiv.org/html/2607.25431)). That ties identity to a
  location.
- Reasoning, not measurement: path and range ids change on an edit above the declaration; plain names
  collide across overloads and `expect`/`actual`; SCIP has no source-set notion and its index-based
  disambiguators shift when a sibling is added; JVM descriptors need resolved types, which a parse-only
  producer lacks.

## Decision

A `SymbolId` is `kscope <ecosystem> <unit> <sourceSet> <descriptor>`, for example
`kscope gradle repo//:core:model commonMain com/example/Repo#load(String,Int).`.

- The descriptor follows the SCIP descriptor grammar.
- Overloads are disambiguated by the simple names of the declared parameter types as written in source, with
  type arguments, nullability and annotations stripped. This is computable from syntax alone, so syntactic and
  compiler-resolved producers agree.
- The source set is part of identity, so `expect` and `actual` declarations are distinct symbols.
- The module key embeds the repository and build root, so identities are unique across repositories.
- Artifacts are located by repository-relative path; the store assigns their durable identity.
- Repository identity comes from version-control metadata, not from the checkout location.

## Consequences

- Adding a sibling overload, or moving a declaration between files, does not change existing identities.
- The disambiguator contains `,` and `:`, outside SCIP's simple-identifier charset: a deliberate divergence
  from strict SCIP. Export to strict SCIP can hash the disambiguator, which only has to be unique among
  siblings.
- Overloads whose parameter types share a simple name but differ by package collide. This known limitation
  must surface as a reported collision, never a silent merge. The same holds for overloads that differ only by
  nullability, such as `f(Int)` and `f(Int?)`. Only one of the declarations is recorded, so a relationship never
  names a shared identity as exact: it is `unresolved` with the reason `identity.collision`.
- Repositories without version-control metadata need a fallback rule, not defined here.

## Reversal cost

Medium to high. The store can be rebuilt from source, so a new format costs a rescan, but identifiers appear
in every fact, serialized result and gold set.
