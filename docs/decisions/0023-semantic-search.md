# ADR 0023: Semantic search and ranking

Status: Accepted on DEV; TEST scored 2026-11-22 · Date: 2026-11-20

## In short

- **Ship optional semantic retrieval.** The maintainer accepts CodeRankEmbed through an isolated inference
  process. The structural ranking remains available without components or with `--no-ml`.
- **Fuse finished file lists.** Semantic similarity augments structural ranking for `analyze` and `context`.
  Hybrid search combines lexical and semantic file ranks. Its design is accepted; this build exposes no standalone
  `search` command.
- **Decide on DEV.** The method was frozen before TEST scoring. The DEV gains support shipping, with the context
  and cost exceptions stated below. TEST, scored once, reached the same verdicts with slightly larger gains.
- **Keep the failures visible.** Hybrid search gained about half of the +10 points first proposed. The pooled
  context result decides acceptance despite per-repository failures. Full embedding exceeds the memory limit;
  Linux context latency exceeds its limit with ranking both on and off.

## Problem

Lexical retrieval matches words and identifiers. A task can describe the right code without sharing its names.
Structural ranking expands lexical matches through the graph, but cannot always recover from that mismatch.
Semantic retrieval compares learned vectors of queries and code chunks. It must earn its download, memory and
latency costs without making structural analysis depend on an inference runtime.

## Options considered

1. **Keep structural and lexical retrieval alone.** Retained as the fallback, but leaves vocabulary mismatch
   unresolved. Shelving semantic retrieval was considered before the full DEV comparison.
2. **A static embedding tier plus an optional transformer.** Rejected after the initial comparison lost recall
   with the static tier. On 2026-11-06 the maintainer chose transformer-only semantic search.
3. **Other models or multi-vector retrieval.** Rejected for added machinery, size or licence restrictions.
4. **Semantic seeds inside graph expansion, or tuned fusion weights.** Replaced by the measured late fusion of
   complete file lists. Equal weights were frozen without tuning; approximate vector retrieval was not selected.
5. **Context from only the fused ten files.** Rejected because the package exhausted its candidates at about
   4,000 tokens. Ranking only in `analyze`, an uncut fused order and changing the limit were also considered.

## Decision

- **Optional installation.** Use the component mechanism of [ADR 0022](0022-optional-components.md).
  The `components.json` manifest shipped with the tool names `semantic` (Apache-2.0) and
  `semantic-model` (MIT). Its pinned download sizes are 94,200,270 bytes for the runtime archive, and
  210,877,074 bytes for the model, 711,649 bytes for its tokenizer, 588 bytes for its descriptor and 1,294 bytes
  for its licence. These are file download sizes, not installed disk measurements. The runtime archive carries
  native libraries for Windows x64, Linux x64, Linux aarch64 and macOS arm64; this record measured Windows x64
  and Linux x64 only. Installation is explicit; analysis never downloads them.
- **Isolate inference.** CodeRankEmbed runs through ONNX Runtime in a child JVM, behind `EmbeddingProvider`.
  The native runtime stays outside the main runtime classpath. The model uses an int8 export; stored vectors and
  the query vector are also quantized to signed bytes with a scale per vector. Dot products accumulate integer
  products, then apply both scales. There is no separate float rescoring pass.
- **Build derived data during scans.** Chunk builder version 1 makes declaration, type-outline and file-outline
  chunks, grouping small sibling declarations. Each text is capped at 32 KiB of UTF-8, then 512 tokenizer tokens,
  and keyed by its text digest. Reuse vectors for matching keys and model identity. A foreground scan finishes
  embedding before returning; `serve` advances bounded batches between requests. Failed or interrupted embedding
  leaves the previous generation in use and does not fail the structural scan.
- **Publish immutable generations.** Files have the form `embeddings-<model>-<hash>.bin`; the model part is the
  provider identifier and the hash covers the generation bytes. Publication uses a temporary file, atomic rename
  and a manifest. Keep the selected generation and older generations with live reader leases. Defer publication
  when three other generations still have live leases; remove unleased obsolete files on publication.
- **Rank every admitted current chunk.** Check the provider, model, graph, component and source inventory
  identities. Scan all admitted vectors with scaled dot products. Project chunk scores to their artifact paths;
  a file takes its maximum chunk score. No approximate index, similarity threshold or vector cap replaces this.
- **Use reciprocal rank fusion.** Each list contributes the reciprocal of its rank plus offset 60, with equal
  weights. Deduplicate each list before fusion; break score ties by path, then repository identity. Full `analyze`
  fuses structural and semantic file lists and returns the top ten. The accepted hybrid `search` design fuses
  lexical and semantic lists.
- **Keep context input as fused head, then structural tail.** On 2026-11-18 the maintainer retained the ten fused
  files, their symbols and tests, then appended remaining positive structural candidates. Preserve tail score
  proportions, scaling its peak below the lowest fused file score. This fixes candidate exhaustion at about
  4,000 tokens without changing the ten-file ranking result. It is not a TEST rescore.
- **Preserve fallback and observability.** Learned ranking is on by default when usable components and vectors
  exist. `--no-ml` wins over `--ml on`; off starts no query process and keeps structural output. Missing, disabled
  or unavailable components and unusable generations also leave structural ranking available. A behind generation
  can rank with its current vectors and report missing chunks; stale vectors are not admitted. Analysis JSON
  exposes semantic states `off`, `not-installed`, `disabled`, `used`, `behind`, `worker-unavailable` and
  `static-fallback`. The last is a schema value; no static tier ships. Context JSON has no semantic status.
  See the [CLI contract](../cli.md) and [architecture](../architecture.md).
- **Freeze and accept on DEV.** On 2026-11-10 the maintainer froze the method after the DEV gate and before any
  TEST scoring: ranking first, hybrid search built on it. The proposed search gain of +10 percentage points was
  replaced after DEV measurement by a significant paired gain over lexical-only on paraphrases, with no significant
  loss on originals. Later context and cost decisions complete acceptance of the optional component.

## Evidence

The [measurement protocol](../benchmarks/semantic-ranking-protocol.md) records the frozen method, int8 model hashes
and predeclared boundaries, and carries dated amendments for the changed readings of 2026-11-19 and 2026-11-20.
The exceptions below state how the maintainer accepted departures from those boundaries.
DEV is the development split used for the decision; TEST is reserved for scoring after it.

R@10 is the share of relevant files found in the first ten; hit@10 counts tasks with any relevant file there.
NDCG@10 measures ranking quality in the first ten, giving more credit to relevant files placed earlier.
DEV result this freeze rests on (gains in percentage points; brackets are task-paired bootstrap 95 % intervals):

| Comparison | Query set | Metric | Gain [95 % interval] |
|---|---|---|---|
| Structural + semantic minus structural | Original | R@10 | +5.5 [+4.7, +6.4] |
| Structural + semantic minus structural | Original | NDCG@10 | +5.3 [+4.7, +5.9] |
| Hybrid search minus lexical | Paraphrase | R@10 | +5.2 [+4.4, +6.0] |
| Hybrid search minus lexical | Paraphrase | hit@10 | +5.6 [+4.6, +6.6] |

Search originals gained on every metric.
Intervals use task-paired percentile bootstrap: 10,000 resamples with seed 20261005. The full gate used fp16;
the int8 CPU subset reached the same verdicts on its paired tasks.

Context ON against OFF used 40 public repositories of the DEV split, 1,200 tasks, the FULL context compiler and the
recommended context level. ON enabled semantic ranking with the installed int8 components; OFF disabled it.
Gold-context recall is the share of files a task really changed that the package contains as verified source.
Token precision is deduplicated source tokens from those files divided by all rendered package tokens.
These are pooled ON minus OFF differences, in metric units:

| Budget (tokens) | Recall, original | Recall, held-out | Precision, original | Precision, held-out |
|---|---|---|---|---|
| 2,000 | +0.1004 | +0.0961 | +0.0446 | +0.0355 |
| 4,000 | +0.0867 | +0.0919 | +0.0392 | +0.0241 |
| 8,000 | +0.1118 | +0.1134 | +0.0060 | +0.0031 |
| 16,000 | +0.0942 | +0.0898 | +0.0082 | +0.0050 |

Held-out queries here are paraphrases of DEV tasks, not TEST tasks. All pooled cells meet the -0.01 limit.
Under the original per-repository rule, 16 of 40 repositories had no cell below the limit; 24 had at least one.
Of 640 cells, 73 were below -0.01, 436 above +0.01 and 131 between. Paired bootstrap with 10,000 resamples and
seed 20261005 put every 95 % recall interval above +0.065. The lowest precision bounds were -0.0022 at 8,000 tokens
and -0.0002 at 16,000 tokens, both held-out. These diagnostics do not erase the per-repository failures.

Cost used Now in Android and the build measured for both DEV items. Windows was measured on 2026-11-19;
Linux on 2026-11-20. Linux used four pinned physical cores of the Windows reference machine inside a virtual
machine. It was not a separate slower machine. p50 is the median; p95 is the 95th percentile.

| Measurement | Windows reference | Linux, four cores |
|---|---|---|
| Full scan and embedding, process start to exit | 359.7 s | 467.0 s |
| Embedding worker peak resident memory | 2,372 MB | 2,277 MB |
| Query worker peak resident memory | 391 MB | 526 MB |
| Warm analyze ON, p50/p95 | 61.0/67.3 ms | 93.7/141.9 ms |
| Warm analyze OFF, p50/p95 | 33.9/37.4 ms | 56.0/94.5 ms |
| Warm analyze with estimates ON, p50/p95 | 186.1/195.2 ms | 121.6/137.4 ms |
| Warm analyze with estimates OFF, p50/p95 | 160.0/170.3 ms | 91.4/105.1 ms |
| Warm context at 8,000 tokens ON, p50/p95 | 162.2/189.8 ms | 211.3/308.4 ms |
| Warm context at 8,000 tokens OFF, p50/p95 | 178.4/210.2 ms | 209.8/297.8 ms |
| One-shot info, median launch to exit | 876 ms | 538 ms |
| One-shot analyze ON, median launch to exit | 3,898 ms | 3,346 ms |
| One-shot analyze OFF, median launch to exit | 2,206 ms | 1,525 ms |

Warm API measurements were idle, after 20 warmups and with 200 calls per API and arm, cycling 30 DEV paraphrases;
each environment retained 1,206 samples with 0 rejected. One-shot medians cover ten runs and include launch costs.
Windows memory is peak working set; Linux embedding memory is kernel VmHWM. Linux concurrent host load met the
prewritten load rule; it was not a completely idle host.

A second machine was measured on 2026-11-20, after the acceptance: Windows 11 with 31.5 GB of memory and real-time
malware scanning on, pinned to four performance cores with one thread each,
Corretto 21.0.11, the same build built there from the same commit, Now in Android at the same commit and the same
30 queries. It retained 1,206 samples with 0 rejected; other load during the steps stayed below 2 %.

| Measurement | Second machine, four performance cores |
|---|---|
| Full scan and embedding, process start to exit | 559.3 s |
| Embedding worker peak working set | 2,376 MB |
| Query worker peak working set | 388 MB |
| Warm analyze ON / OFF, p50/p95 | 104.6/116.0 ms / 61.9/70.0 ms |
| Warm analyze with estimates ON / OFF, p50/p95 | 243.6/264.0 ms / 205.6/226.0 ms |
| Warm context at 8,000 tokens ON / OFF, p50/p95 | 223.8/279.3 ms / 235.9/288.3 ms |
| One-shot info, analyze ON, analyze OFF, medians | 1,496 ms, 6,316 ms, 3,834 ms |

Against the 200 ms ceiling, warm analyze stays within it there; analyze with estimates and context at 8,000 tokens
exceed it with ranking on and off alike. With Java runtimes that bundle the Visual C++ runtime 14.29 or 14.36 the
worker crashed inside ONNX Runtime, and the component self-test reported the requirement of 14.40 or newer; with a
runtime bundling 14.44 it passed. int8 vector generations built from the same inputs were not bit-identical across
the two machines' processors, so TEST ran on one machine.

## Exceptions accepted

- **Search bar, 2026-11-10.** The bar first proposed for hybrid search was +10 points of R@10 or hit@10 over
  lexical-only search on paraphrases. DEV measured +5.2 and +5.6: a real gain, about half the bar. The maintainer
  replaced the bar with a significant paired gain on paraphrases and no significant loss on originals. This
  was decided after the DEV measurement and before any TEST scoring.
- **Context boundary, 2026-11-19.** The maintainer decided that the pooled comparison determines the context
  regression verdict. The predeclared per-repository clause was not met and no longer decides acceptance.
  This changed how a predeclared boundary is read after its DEV measurement and before any TEST scoring.
  The measured values and the -0.01 limit did not change.
- **Cost boundaries, 2026-11-20.** The maintainer accepted cost as measured with two exceptions. Full embedding
  peaks at 2.3 to 2.4 GB in the embedding worker, exceeding 1.5 GB; the 1.5 GB limit holds for workers serving
  queries. On Linux four cores, context at 8,000 tokens has a warm p95 above 200 ms with ranking on and off:
  308 and 298 ms respectively. Ranking is not the cause of crossing the limit. Full embedding of Now in Android
  is within 30 minutes. These ceilings were accepted after measurement, before TEST scoring.
- **Unmeasured scope.** A separate, slower four-core Linux machine; replicated 100,000- and 300,000-vector
  generations; retained generation disk; and timings with indexing active remain unmeasured. The maintainer
  accepted these gaps rather than treating the available measurements as proof of those boundaries. A second
  Windows machine was measured after the acceptance (see Evidence); it narrows the gap but is not Linux.

## Consequences

Semantic retrieval adds a separately installed model, native runtime, derived vector storage and process lifecycle.
Full embedding needs substantially more memory than query serving. A resident service can reuse query resources;
one-shot calls pay startup costs. Structural retrieval remains useful offline and is the explicit fallback.
The DEV gain is pooled evidence, not a promise for every repository. The context tail preserves candidates beyond
the fused ten, while the frozen ranking result remains bounded. Model, chunking or fusion changes require a new
DEV freeze and a separately reported evaluation.

## Reversal cost

Moderate. Disable the learned path, remove optional components through their installer and discard derived
generations. The fact model does not depend on vectors. Removal must handle the public controls and semantic
status contract; replacing the model requires fresh vectors and another DEV decision.

## TEST

TEST was scored once, on 2026-11-22, on the pinned build (the build measured for both DEV items) with the frozen
method and identities. It does not change this decision.

All 6,392 TEST tasks of the 218 repositories ran with fp16 on the GPU and none failed; two paraphrases had no query
text and count as unresolved, not as zero. The int8 CPU subset ran all 1,200 of its tasks: 30 TEST tasks from each
of the same 40 repositories as the DEV subset, whose draw rule was not recorded. Gains in percentage points, with
task-paired bootstrap 95 % intervals (10,000 resamples, seed 20261005):

| Comparison | Query set | Metric | TEST [95 % interval] | DEV |
|---|---|---|---|---|
| Structural + semantic minus structural | Original | R@10 | +6.0 [+5.1, +6.9] | +5.5 |
| Structural + semantic minus structural | Original | NDCG@10 | +5.8 [+5.2, +6.3] | +5.3 |
| Hybrid search minus lexical | Paraphrase | R@10 | +5.8 [+5.0, +6.7] | +5.2 |
| Hybrid search minus lexical | Paraphrase | hit@10 | +6.5 [+5.5, +7.5] | +5.6 |
| Hybrid search minus lexical | Original | R@10 | +4.7 [+4.0, +5.5] | +4.4 to +4.9 on all metrics |

- **Ranking passes:** the gain is significant on R@10 and NDCG@10.
- **Hybrid search passes the bar as replaced on 2026-11-10:** a significant paired gain on paraphrases, and the
  originals gain on every metric. Against the +10 points first proposed it still falls short, as on DEV: the upper
  end of the R@10 interval is +6.7.
- **int8 against fp16 on the 1,200 shared tasks**, fp16 minus int8 in R@10: 0.0 [-0.7, +0.7] for hybrid search on
  originals, +1.3 [+0.4, +2.2] for structural + semantic on originals, and +1.6 [+0.3, +3.0] for semantic alone on
  paraphrases. int8 loses one to two points where the semantic list carries most weight and nothing measurable in
  the hybrid search arm. It reaches the same verdicts.

Each task's record is computed and saved on its own, so no result depends on the order in which tasks ran or on how
often a task was attempted.
