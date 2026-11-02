# ADR 0006: Persistence and incrementality

Status: Accepted · Date: 2026-10-05

## Problem

The graph must persist locally, support lexical search, and update after small edits without a full rescan.
An incremental result that silently differs from a clean rebuild is worse than a slow correct one.

## Options considered

1. **An embedded graph database.**
2. **A key-value store such as RocksDB or LMDB.**
3. **In-memory graph with JSON files.**
4. **SQLite with FTS5** and a relational property-graph schema. Chosen.

Separately, the repair strategy: symbol-level repair, or replacement of whole ownership units.

## Evidence

- Embedded graph databases are unstable. Kuzu was archived on 2025-10-10
  ([kuzudb/kuzu](https://github.com/kuzudb/kuzu)), CozoDB has had no upstream updates since 2024
  ([dbdb.io](https://dbdb.io/db/cozodb)), and the continuation LadybugDB is a young fork
  ([LadybugDB](https://github.com/LadybugDB/ladybug)).
- Mature systems use plain stores: Glean uses RocksDB
  ([Meta Engineering](https://engineering.fb.com/2024/12/19/developer-tools/glean-open-source-code-indexing/)),
  clangd uses on-disk shards
  ([Chromium clangd docs](https://chromium.googlesource.com/chromium/src/+/master/docs/clangd.md)) and
  codebase-memory-mcp uses SQLite, so a graph database appears optional (inference).
- Glean owns every fact by a unit such as a file or module, so invalidation is a set operation
  ([Glean incrementality](https://glean.software/docs/implementation/incrementality/)).
- CodeNib's symbol-level incremental repair always completed without error but matched a full rebuild in only
  13 of 24 held-out changes ([CodeNib](https://arxiv.org/html/2607.25431)). Completion does not imply
  correctness.
- Reasoning: SQLite supplies transactions, FTS5 and recursive CTEs in one engine. A key-value store would
  need hand-built indexes and search. Plain files lack atomic per-unit replacement.

## Decision

- Storage is SQLite through `org.xerial:sqlite-jdbc`, with FTS5 for lexical search and a relational
  property-graph schema.
- Every fact belongs to an ownership unit (artifact, source set or module, per producer). An update replaces
  whole units in one transaction.
- Invalidation is conservative: a changed source set plus the semantic facts of the source sets that depend
  on it; all linker output; and, on a build-file change, the build model and everything derived from it.
- `kscope update --verify` compares the incremental result with a clean rebuild and fails on any difference.
- Facts are serialized in a canonical sorted order, which makes that comparison byte-exact.

## Consequences

- Updates re-analyse more than strictly necessary. Invalidation can be narrowed later, with verify mode
  showing whether a narrower rule still matches a rebuild.
- Verify mode costs a full rebuild, so it suits CI and benchmarks.
- The store is derived data and can be deleted and rebuilt.
- The bundled SQLite library must provide FTS5; store tests assert it. Deep graph expansion through
  recursive CTEs has not been measured.

## Reversal cost

Medium. Only the store module touches SQLite; the rest of the system uses the `GraphReader` and writer
contracts. The ownership-unit model is harder to change: producers, invalidation and verification depend
on it.
