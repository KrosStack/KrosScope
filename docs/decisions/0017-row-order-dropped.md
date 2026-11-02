# ADR 0017: Ordering on the query path without stored row order

Status: Accepted · Date: 2026-10-18 · Supersedes one item of decision 2 of [ADR 0015](0015-resident-service-and-settings.md)

## In short

- ADR 0015 asked that rows be stored and read in their canonical order, so that the query path does not encode
  them again to sort. That item is withdrawn.
- Ordering on the query path is bounded by the budgeted selection instead, which orders files and nodes once and
  proposes without copying the files already present.
- A view of the graph for retrieval, kept per index generation, is the next step if ordering grows again.
- The other items of decision 2 stand: the estimate of `analyze` from block measures, and stored block measures.

## Problem

ADR 0015, decision 2, lists three changes that remove repeated work from the query path. The third, rows stored and
read in their canonical order, was decided before it was measured.

## Options considered

1. **Store rows in canonical order.** Every table that a query reads in order keeps its rows sorted by the
   canonical key. Either the index stores a second, ordered copy of those rows, or every incremental scan maintains
   the rank of each row as rows are inserted and removed.
2. **Bound the sorting where it happens.** The budgeted selection orders the candidate files and nodes once per
   query and stops comparing as soon as the budget is decided.
3. **A retrieval view per index generation.** The resident service prepares, once per generation of the index, the
   ordered structures that retrieval reads, and every query of that generation reuses them.

## Evidence

- On the large codebase of the timing baseline, decoding and sorting rows for order took about 0.1% of a served
  query after the budgeted selection was merged.
- The budgeted selection reduced the comparator calls on the measured input from about 8.3 million to about 400,
  with output byte for byte the same.
- Option 1 either doubles the stored rows of the affected tables or needs rank maintenance in every incremental
  write path, which the store does not have today.

## Decision

Option 2 is taken, and it is merged. Option 1 is dropped from ADR 0015. Option 3 is recorded as the next step and is
taken only if a measurement shows ordering above a few percent of a served query again.

## Consequences

- The index format does not change for this item.
- ADR 0015 keeps its other decisions. Its decision 2 now has two items.

## Reversal cost

Low. Nothing was built for option 1. Taking it later is an index change with a migration, decided by a new ADR.
