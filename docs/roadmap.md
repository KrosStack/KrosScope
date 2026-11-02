# Roadmap

What is not done and what comes next. Items within a group are in priority order, and nothing here is promised for a
date. Under the [benchmark policy](benchmark.md), a change to the ranking or to context compilation is developed on
development data only, then frozen, and its test data is scored once.

## Next

### 1. Pack raw source at budgets of 8,000 tokens and above

Below 8,000 tokens the context compiler packs raw source in rank order, which raised token precision by a factor of 2.7
to 7.1 ([small-budget packing](benchmarks/real-repositories.md#small-budget-packing-realrepos-v2)). At 8,000 and 16,000
tokens it still loses token precision to both BM25 baselines in every group
([context compilation](benchmarks/real-repositories.md#context-compilation)). The work is to extend that policy to
larger budgets, or find where it stops paying. It is measured by context Tier 1
([context coverage](benchmark.md#context-coverage)).

### 2. Finer incremental updates

An edit inside a function body is cheap ([figures](benchmarks/performance.md#update-after-an-edit-inside-a-function-body)).
An edit to a Java file, or to anything other files can see, still analyses the code that depends on it, and every update
that analyses something pays for starting a worker. Keeping a worker alive between updates is the next step
([ADR 0014](decisions/0014-api-digests.md)). It is measured by update time, with no difference from a clean rebuild
under `kscope scan --verify`.

### 3. Make the graph and history signals earn their keep

On real repositories the full ranking is statistically indistinguishable from KrosScope's own lexical-only ranking
([localization](benchmarks/real-repositories.md#localization)), and gold derived from commits names files only
([limitations](benchmarks/real-repositories.md#limitations)). The work is to build task sets whose gold includes
symbols and tests, test each signal on them, and keep only those that help. It is measured by the Tier 2 comparison
([Tier 2](benchmark.md#analyzer-benchmark)).

## Later

- **Faster queries on large graphs.** Token counting dominates what is left of `analyze` and `context` on a large
  codebase ([performance](benchmarks/performance.md)). Planned: a stored sort key
  ([query cost](architecture.md#query-cost)) and a persistent analyzer process.
- **Agent integrations.** [`kscope mcp`](cli.md#mcp) and [`kscope skill`](cli.md#skill) exist; an IDE plugin and a CI
  check would be further clients. Whether an agent with these tools does better than one without is not measured yet.
- **A coding-agent benchmark at scale.** The [pilot](benchmarks/agent-pilot.md) was at its ceiling. A benchmark that can
  show a difference needs tasks the model cannot solve unaided, real repositories, more repetitions and more than one
  agent ([agent benchmark](benchmark.md#agent-benchmark)). Until a task-success difference stands beside a token figure,
  no token-reduction claim is made.
- **Native launcher.** It would remove the JDK requirement for queries; analysis would still need a JDK.
- **Confirm the licences of code embedded in the Kotlin compiler** with the upstream project
  ([third-party licences](third-party-licenses.md#what-the-kotlin-compiler-artifact-embeds)).
- **Decide the re-exports of the service API before a 1.0**, because a client that depends on one makes it hard to
  change.
- **A native-access flag for newer JDKs** in the launcher scripts.
- **Windows argument encoding on JDK 17.** Characters outside the system code page can be replaced before `kscope` sees
  them; `--task-file` is the supported route today ([text encodings](cli.md#text-encodings)).

## Not planned for now

- **Machine-learning layers** ([ML layering](architecture.md#ml-layering)). The deterministic signals have not yet shown
  a gain over lexical-only retrieval, so a learned layer would sit on a ranking whose parts are not yet understood.
- **Languages other than Kotlin and Java** ([support matrix](support-matrix.md#not-supported-or-not-analysed)).
- **Claims about runtime behaviour.** KrosScope reports what sources and build files state and never runs them, so a
  link it cannot establish statically is reported as `unresolved` or `not-analysed`.
