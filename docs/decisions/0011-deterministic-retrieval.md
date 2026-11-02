# ADR 0011: Deterministic retrieval

Status: Accepted · Date: 2026-10-07

## In short

- Retrieval is deterministic and local. A code-aware lexical stage seeds a graph expansion, and the signals are
  fused by reciprocal rank with fixed weights. No model, network or random value is involved.
- The full ranking is the default of `analyze`, `related` and the context compiler. The lexical stage remains
  available as the reference.
- It beats plain text ranking on most groups. It is not yet shown to beat the lexical stage on real
  repositories.
- Weak spots are known and measured: test selection, symbol recall and the loss on one real repository.

## Problem

Given a task written in plain language, KrosScope must say which files, symbols, modules and tests are likely to be
involved, and it must be able to show why.

The answer feeds `analyze`, `related` and the context compiler, so it must be reproducible, explainable and
computable on a developer machine without a network or a model
([architecture principle 4](../architecture.md#principles)).

Task descriptions rarely share words with the code they affect, so text matching alone is expected to miss files
that the graph can reach.

## Options considered

1. **Plain text ranking over file contents.** The baseline that every claim is measured against: BM25 over the raw text
   of files, and BM25 with common English words removed.
2. **Code-aware lexical retrieval alone.** Identifiers are split into words, symbols and paths are indexed as fields
   of their own and weighted above file text, and queries are stemmed.
3. **Code-aware lexical retrieval, expanded through the graph and fused with history.** The lexical hits become seeds
   that spread along the relationships of the index; hints about the kind of artifact and the commit history of a file
   add two smaller signals. Chosen.
4. **Learned or embedding-based retrieval.** Needs a model, so it cannot be the default. It stays an optional
   enrichment layer ([ADR 0008](0008-capability-registry.md) describes where a signal would plug in) and is not
   built.

## Evidence

What the code does, in the order a query meets it:

- **Lexical stage.** Symbols and files are indexed in two SQLite FTS5 tables with the `porter unicode61` tokenizer.
  - The shared `CodeTokenizer` splits identifiers into parts before indexing.
  - Queries drop a fixed list of 33 English stop words ([normalization policy](../retrieval-normalization.md)).
  - Artifact hints come from a documented [vocabulary](../retrieval-vocabulary.md).
  - Each column has its own `bm25` weight. For symbols: 10.0 for the name, 6.0 for name parts, 3.0 for
    qualified-name parts, 2.0 for the signature and for path parts, 1.5 for documentation, 1.0 for metadata
    parts and 0.5 for the kind.
  - For files: 4.0 for path parts and 1.0 for content.
- **Graph expansion.** The symbol hits and the file hits seed a personalized PageRank over the relationships of the
  index: three hops around the seeds, 20 iterations, restart probability 0.35. A seed's weight is its reciprocal rank
  in its own hit list, so the scale of the `bm25` score does not matter.
- **Fusion.** Each signal ranks the candidate files and contributes `weight / (20 + rank)`.
  - The weights are lexical symbol 1.0, lexical file 1.0, graph 0.8, category hint 0.35, history 0.1 and an
    exact mention of a name or path 2.0.
  - The history signal is the `history.commits` count of a file.
  - Ties break on the path, so the same index and task give the same ranking.
  - The constants live in `RankingWeights`; the benchmark policy requires them to be frozen before held-out
    scoring.
- **Stages.** `RetrievalStage` lets the ranking stop early (`LEXICAL`, `GRAPH`, `PROJECTION`, `CATEGORY`, `FULL`), so
  every signal can be measured on its own. Each ranked file keeps the signals that produced its score.

What was measured, with the exact numbers on the linked pages. Metrics are explained in
[Reading the numbers](../benchmarks/README.md#reading-the-numbers).

- **Against plain text ranking, the fixture.**
  Localization under the two-tier policy (14 tasks in two statement sets):
  Tier 1 passes on both sets.
  - On the original statements the full ranking reaches recall@5 0.5540, recall@10 0.6587 and NDCG@10 0.5515,
    against 0.0595, 0.1577 and 0.0863 for raw-text BM25 and 0.1934, 0.5475 and 0.2907 for BM25 with stop words
    removed.
  - The held-out set gives 0.5219, 0.6449 and 0.5556, against 0.2089, 0.5708 and 0.3013 for the stronger
    baseline.
- **Against plain text ranking, real repositories.** [Real-repository benchmark](../benchmarks/real-repositories.md)
  (120 tasks from the histories of two public repositories):
  - The full ranking beats both baselines in five of six groups.
  - It loses recall@5 and recall@10 on one test group (0.3556 against 0.3742, and 0.4484 against 0.4659).
  - The overall result for that benchmark is Tier 1 FAIL, because of that loss and of the token precision of
    context packing ([ADR 0012](0012-context-package-and-budget.md)).
- **Against its own lexical stage.** This is the comparison that tells whether the graph and history signals pay for
  themselves.
  - On the fixture one of six differences is
    significant: held-out NDCG@10 rises by +0.0231 (paired 95% interval 0.0019 to 0.0484). The held-out recall@5
    and recall@10 are lower by 0.0114.
  - On the real repositories the pooled test split gives recall@5 0.3744 against 0.3802 for the lexical stage,
    recall@10 0.4650 against 0.4627 and NDCG@10 0.3476 against 0.3553. The benchmark page states that on the
    test split the graph, projection and history signals add nothing measurable over the lexical ranker.
  - On the pooled development split the full ranking leads (recall@10 0.6468 against 0.5927, a significant
    gain).
- **What retrieval does not yet do well.** The targets for test selection and for symbol recall are missed on both
  fixture sets: test precision 0.5072 with recall 0.6726 on the original set, and symbol recall@10 of 0.2619 and
  0.3214, against targets of precision 0.5 with recall 0.8, and 0.5.

## Decision

- Retrieval is deterministic and local: a code-aware lexical stage seeds a graph expansion, and the signals are fused
  by reciprocal rank with fixed weights, as described above. No model, network or random value is involved.
- The full ranking is the default of `analyze`, `related` and the context compiler. The lexical stage remains
  available and is the reference for every claim about what the graph and history add.
- Every result carries the signals and paths behind each score, so a ranking can be audited.
- The ranking constants are not tuned against the fixture's spent held-out statements. Changing them needs a fresh
  task set from outside the project, under the [benchmark policy](../benchmark.md).
- Public statements about retrieval quality say what the pages above say: it beats plain text ranking on most groups,
  and it is not yet shown to beat the lexical stage on real repositories.

## Consequences

- Results are reproducible and can be explained signal by signal, and a query costs no model inference.
- The extra signals add code and index size that the real-repository measurements do not yet justify. If the next
  measurement on fresh tasks still shows no gain over the lexical stage, that is a reason to simplify them, not to tune
  them.
- Quality depends on the words of the task: text that shares no word with the code reaches it only through the graph.
  Localization on real repositories is lower than on the fixture, and the fixture's paraphrases are spent.
- Weak spots are known and measured: test selection, symbol recall and the loss on one real repository.

## Reversal cost

Low to medium. The fusion weights and stages are local to `intelligence/retrieval`, and results expose which signals
produced them, so a signal can be removed or replaced without changing the model, the store or the command line. A
learned signal would be added through the same ranking without replacing it.
