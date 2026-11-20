# Semantic ranking measurement protocol

This protocol defines measurements and decision boundaries. It contains no measured results.
Keep raw queries, source packages, process samples and task records in a private output directory.
Publish only scrubbed aggregates together with the complete method and identity checklist.

The `semantic-cost` command accepts `--corpus`, `--index`, `--out` and `--identity`, plus optional
`--machine` and `--workload` JSON files. The index must already be prepared. The identity file is
the complete context pipeline identity. Machine fields are `reference` (`windows` or
`linux-four-core`), `cpu`, `cores`, `ram`, `os` and `jvm`. Workload fields are ordered `queries`,
their `identity` (SHA-256 of the canonical JSON array), and optional `activeIndexing`.
It measures service-cold and idle API calls and, when requested and pending, owner-thread indexing.
Its exit code describes the requested service probe; formal unmeasured S8 boundaries remain NOT RUN.

## Frozen method

Use the frozen model export, descriptor, tokenizer, query prefix, normalized CLS pooling,
quantization and chunk builder version 1. Preserve the 32 KiB source and 512-token caps.
Scan every admitted current chunk with scaled dot products. Project each chunk to its own file
and artifact references, taking the maximum score for each file. Deduplicate complete lists,
then fuse structural and semantic file ranks with reciprocal rank fusion, offset 60 and equal
weights. Break ties by repository-relative path; `analyze` returns the final top ten files.
For applied semantic ranking, `context` keeps those candidates first, then appends remaining structural
candidates with their score proportions preserved, scaled so that the strongest stays just below the lowest fused
file score.
Do not substitute approximate retrieval, a vector cap, thresholds or a different model.
The frozen int8 export is identified by these SHA-256 values:

| Artifact | SHA-256 |
|---|---|
| model.onnx | `99704fb95a7cdbcabb25d8b4eaaab788f0420cb9254cc50172ea103d014758f2` |
| tokenizer.json | `91f1def9b9391fdabe028cd3f3fcc4efd34e5d1f08c3bf2de513ebb5911a1854` |
| model.json | `9fd4222004b18e2e4cc0e4e5eb4055e58118bbc976c652c5c9654131440cbbc4` |

Reproduce the immutable saved DEV top-ten lists through the product before measuring quality.
Changes to the ranking method require a new DEV freeze; TEST cannot tune or repair a decision.

## S9 quality boundaries

Pair FULL compiler ON and explicit FULL compiler OFF on the same parent, graph, corpus,
recommended compiler level, estimator and budgets: 2,000, 4,000, 8,000 and 16,000 tokens.
Measure original and held-out reference tasks and chronological DEV original/paraphrase tasks.
Enumerate expected tasks before loading completed records. A null paraphrase is unresolved.
Missing tasks, arms, budgets, source proof or pipeline identity fail completeness.
ON requires actual USED evidence, zero behind chunks, matching provider/model and a selected
generation identity. An ON label or installed component alone supplies no evidence of use.
Include every complete real-repository task, including gold files absent at the parent with
zero recall for both arms. Do not discard tasks based on task or budget validity labels.

Report two separate verdicts. Regression requires ON minus OFF to be at least exactly -0.01
for both gold-context recall and token precision at every budget on each query set, pooled over
every complete task of the split. Make the same comparison per repository and report the number
of repositories and of cells below the limit; these cells are evidence and do not change the
verdict (amendment of 2026-11-19 below). Compare decimal representations of the serialized means
without tolerance.
ON Tier 1 strictly exceeds both raw and English BM25 in both metrics at every budget on both
reference sets. The existing Tier 1 flag continues to describe OFF. Report paired bootstrap
diagnostics with seed 20261005, 10,000 resamples and linear 95% percentile intervals; intervals
do not replace either gate. Report incomplete evidence and failures alongside the aggregates.

## S8 timing and resource boundaries

Use actual service `contextResult` and FULL `analyze` calls. Measure analyze with estimates
disabled and with default estimates separately, with paired OFF calls for each query.
Load the graph before warm measurements. Discard 20 warmups per API and arm, then retain 200
actual paraphrase queries, cycling the frozen ordered workload when necessary. Report raw
nanoseconds, p50 and nearest-rank p95 separately by API, arm and idle/active-indexing boundary.
Time immediately around each API call with a monotonic clock. Record owner-thread indexing
work before the API separately in the client boundary. Preserve returned semantic status and
the audited pipeline identity. Fallback requests and changed identities cannot enter successful
warm ON percentiles; an expired query is recognized by its returned fallback status. Report rejected
counts; do not hide them in a smaller denominator.

A fresh service's first API call is service-cold; its API timer excludes service opening,
provenance auditing and closing. Keep these samples out of warm percentiles. Formal cold S8
requires ten fresh one-shot process runs, from launch through exit, minus paired `kscope info`
on the same index. Record OS file-cache state. Worker-only or service-cold samples cannot
substitute for this process boundary, and sums of component p95 values are not service p95.

Prepare 100,000- and 300,000-vector replicated generations with corresponding current source
inventories. Prove every stated vector is eligible and scanned; discarded stale entries do not
count. Never weaken freshness checks for preparation. Report preparation and generation-load
memory separately. Measure full embedding with a saved graph: graph scan, key preparation,
inference, publication and total. Separately measure one body edit, affected/reused keys and
incremental embedding. The quality full-embedding limit is under 30 minutes and warm API p95
is under 200 ms. Do not apply a removed static-tier incremental limit to this model.

Sample host and child resident memory concurrently with timed API calls, once per second with
a bounded sampler owned by the probe. Each round uses one batch OS read for all requested PIDs:
Windows `tasklist` working set or Linux `VmRSS`; unavailable reads remain null. Report reaching
the sampling cap and the time it was reached. Close and join sampling on success and failure.
Report maximum worker RSS against 1.5 GB, host/transient memory, and
retained generation bytes under the existing three-generation lease rule. Reservations and
heap limits are not resident-memory measurements. Missing machines or workloads remain NOT RUN.

## Identity checklist and repeats

Run on a documented Windows reference environment and a four-core Linux reference environment.
Record CPU, physical/logical cores, available RAM, OS version, JVM vendor/version and flags,
device, native runtime and concurrent work. Record graph/corpus revisions, ordered task/query
hashes, estimator/compiler policy, code/build hash, provider and complete ModelIdentity,
worker payload file hashes, model/tokenizer/descriptor hashes, chunk and fusion policy,
generation digest and current inventory proof. Omit machine names and local paths from public text.

Freeze identities before each repeat; verify them after preparation and after measurement.
Use separate private output directories for independent repeats. Reuse no mismatched checkpoint
and record warmups, repetitions, timer boundaries, cache state, failures and missing evidence.
Repeat affected DEV measurements after implementation or runtime changes, keeping ranking fixed.
Pin a passing implementation and both environments before the single frozen TEST scoring.

## Amendments

Each amendment changes how a boundary above is read. Both were made after the DEV measurement they
concern and before any TEST scoring. A report under this protocol states the outcome under the
earlier text as well.

### 2026-11-19: the pooled comparison decides the regression verdict

Earlier text: the -0.01 limit had to hold per repository and pooled. Now it has to hold pooled, and
the per-repository cells are reported in full. A repository contributes a few dozen tasks to a cell,
so one task moves its mean by more than the limit; the limit is finer than a single repository can
resolve.

A split may be measured as one run per repository. Pooling then requires every run to be complete,
all runs to share one pipeline identity apart from the generation of each repository, and the means
each run reported for its repository to equal the recomputed ones. Pooled means accumulate task
values by ascending repository identifier, then in the task order of the run.

### 2026-11-20: timing and memory boundaries

- The warm p95 limit of 200 ms applies to each API with ranking ON. Where the paired OFF arm on the
  same environment is itself above 200 ms, the limit cannot attribute the excess to ranking. Report
  both arms and their difference at p50 and p95, mark the limit as not met, and record the decision
  to accept or reject the result with its reason. No replacement limit is defined.
- The 1.5 GB limit covers workers during the timed API calls, as the sampling rule already says.
  Report the peak of the embedding worker during a full embedding separately; this protocol sets no
  limit on it.
- Where no separate four-core Linux machine exists, the Linux reference may be four pinned physical
  cores of the Windows reference machine inside a virtual machine. Report it as such. Measure the
  CPU used by everything outside the virtual machine during each step, state the acceptance rule
  for that load before the run, and discard steps that break it. A separate machine stays NOT RUN.
