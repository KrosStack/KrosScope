# Benchmark Methodology

Measurement is a product requirement. KrosScope maintains two benchmark classes and never reports a token
reduction without task-success results beside it.

## In short

- The analyzer benchmark runs with `kscope benchmark` and involves no coding agent. The agent benchmark runs a
  coding agent headlessly on reference tasks and decides success with checks the agent never sees.
- Gold data lives outside the scanned tree, in `benchmark/reference/`. A level of support is claimed only where
  a gold-backed test proves it.
- The conformance run exits with 1 when its gate is missed. The gate covers declarations, closed-world edges,
  the build model and dangling targets.
- Tier 1 requires strict improvement over both raw BM25 and English-stop-word BM25 on recall@5, recall@10 and
  NDCG@10, separately on every task set. Without it, no claim is made that KrosScope beats text search.
- The real-repository benchmark takes its tasks from the pinned histories of Now in Android and the KotlinConf
  app. The older half is development data and the newer half is test data, which is scored once.
- Published results state commit, environment, tool and model versions, tokenizer and the full metric table,
  including where KrosScope loses.

## Reference workspace

`fixtures/reference-workspace` ("TaskTide") is an original, Apache-2.0, buildable Kotlin/Android/KMP project
made to exercise the relationships KrosScope claims:

- Kotlin and Java in mixed modules,
- multi-module Gradle with convention plugins and a version catalog,
- KMP source sets with `expect`/`actual`,
- Android resources, manifest components and build variants,
- AIDL,
- generated schema APIs (SQLDelight, Wire proto, Room),
- a JNI boundary, and
- tests at several levels.

Gold data lives outside the scanned tree, in `benchmark/reference/`:

| File | Content |
|---|---|
| `gold/build-model.json` | Modules, source sets, `dependsOn`, targets, variants, project dependencies. |
| `gold/relationships.json` | Every planted boundary relationship, per class, with expected evidence class. Complete per class, so recall and precision are both defined. |
| `gold/closed-world/*.json` | For selected source files, the exhaustive set of outgoing reference, call and inheritance edges. |
| `tasks/*.json` | Development tasks: statement, category, files to edit, files to read, symbols, required relationships, expected change scope, relevant tests. |

- Gold entries address symbols by module, fully qualified name and signature, independent of KrosScope's
  internal identifiers.
- A level of support is claimed only where a gold-backed test proves it. The [support matrix](support-matrix.md)
  lists the relationships that KrosScope claims, the class that proves each and the measured levels.
- Every closed-world file also lists the `declarations` it makes (kind, qualified name and line of each class,
  interface, object, enum entry, typealias, function, property and constructor, without locals), derived by
  reading the source and never from KrosScope's output.
- A closed-world edge to a generated surface (AIDL, Wire, SQLDelight, view binding, Android resources) is
  reported as deferred until a producer makes that surface and is not scored.

### Conformance run

The `benchmark` module loads the gold data, maps its addresses to graph nodes and scores a scan of the reference
workspace: build model, declarations, closed-world edges, the relationship classes that exist, and the coverage of
the graph by evidence class and resolution state, with the integrity of resolved targets.

```
./gradlew :benchmark:run --args="conformance --level syntax|resolved|exact|all --out <directory>"
```

The command scans into a throwaway index, writes `metrics.json` and `report.md` to the output directory, and exits
with 1 when the gate is missed. `--mode default|trusted` stays as the older name of `--level resolved|exact`. With
`--level all` the command runs the three levels in turn, writes one directory for each below the output directory,
and sets them side by side in `levels.md` and `levels.json`: the scan time, the headline scores and the precision and
recall of every relationship class per level. Only the timings vary between runs; the comparison is measured once
per run and states no median. The gate covers:

- declarations: precision and recall at least 0.99;
- closed-world edges: precision at least 0.98, recall at least 0.95;
- a build model equal to gold;
- no dangling target;
- a scan without errors, gold data without problems, and at least one declaration and one closed-world edge
  measured.

The levels gate different files and, at `syntax`, a different set of checks:

| Level | Files that the gate covers |
|---|---|
| `syntax` | The files that compile without the Android classpath. Edge recall is measured and published per class but is not gated, because nothing is resolved. |
| `resolved` (mode default) | The files that compile without the Android classpath. |
| `exact` (mode trusted) | All files. The tests need `-Pkscope.integration=true`. |

The gate of `syntax` is not a weaker one. Declaration precision and recall stay at 0.99, the build model equals gold,
no resolved target is dangling, and the precision of the edges that exist stays at 0.98. In addition no relationship
has compiler evidence, and the Kotlin and Java source sets say that references were not analysed.

The test suite runs the same checks.

Relationship classes with ends of several kinds are also scored per kind. `resource_to_consumer` is reported for
all consumers and apart for Kotlin and Java code, Kotlin layouts reached through view binding, XML and the
manifest, so that a kind without a producer shows as missing instead of lowering one total.

- A Kotlin or Java consumer is the reference from code to the generated `R` field followed by the `GENERATES`
  relationship from the resource.
- A reference that only qualifies the field with `R` or `R.string` is not an edge to score.
- The same holds for the `BuildConfig` class, and a reference to the view binding class of a layout is a
  consumer of the layout.

Evidence classes are assigned as described under Evidence classes below. The next table shows what each
relationship class is scored on:

| Class | Scored on |
|---|---|
| `resource_to_consumer`, `viewbinding_layout_to_consumer`, `buildconfig_field_to_consumer` | The code end, named by file and lines. |
| `compose_resource_to_consumer` | The consumer, named by file and line, as `resource_to_consumer` does. A line that reads an accessor of `Res` counts, an import of it does not. |
| `manifest_component_to_class` | The two nodes. |
| `layout_class_reference` | The two nodes, with the line that names the class. |
| The four AIDL classes | The code end, named by its file, because an object expression and its members are not symbols: the graph states the nearest symbol around them, which lies in the same file. |
| `jni_external_to_native` | The two hops of the JNI contract, from the declaration to its contract and from the contract to the C function, named by file and function. |
| `proto_message_to_consumer`, `sq_table_to_consumer` | The consumer, named by file, scored in two parts (below). |
| `sq_query_to_consumer` | The query and the calling declaration. |
| `room_entity_dao_database` | Pairs: the graph relates a database or a DAO to its entities, and those are turned around to start at the entity, as gold lists them. |
| `room_component_to_consumer` | The consumer, named by file. It leaves out the files that declare Room entities, DAOs and databases. |
| `test_to_subject` | The test class and its subject. |
| `flavor_specific_implementation` | Pairs (below). |

`proto_message_to_consumer` and `sq_table_to_consumer` are scored in two parts:

- A file that names the class that Wire or SQLDelight made is a named consumer.
- A file that only reads members of values of that class, or members whose declared type is made from the
  message or table (a field typed by a message, a query that returns the rows of a table), uses it by inference,
  and gold marks those with `inferred-type`.
- A file of the second kind is not counted when the file also names the type.

`flavor_specific_implementation` is scored on pairs:

- The compiler reaches the implementation of the default variant, and the implementation of each other flavor is
  reached through the `variant-alternative` relationship between the declarations of the flavors.
- The pairs of the implementation that the compiler reaches have `compiler` evidence and the others
  `build-model`, because the variant model of the build is what makes them alternatives.

An unresolved import, or type written with its package, whose qualified name only one workspace symbol has gets a
`qualified-name-match` relationship (textual, heuristic) next to it. The unresolved relationship stays, so that such
a reference counts once as unresolved and once as heuristic; the sub-kind and the evidence class tell the two
apart. In a workspace of several repositories the name must be that of a symbol of the same repository, or of a module
the reference reaches through a dependency by coordinates, and such an answer is a `coordinate-match`.

### Workspace of repositories

`fixtures/workspace-of-repositories` is a second TaskTide workspace of six repositories that sit side by side: an
app, a kit of libraries, C++ and Rust native libraries, a spec repository and a web repository. Its gold data
lives in `benchmark/workspace/`: `gold/relationships.json` lists the 19 links between the repositories in 11
classes, each with the evidence class it is expected to carry, and `outputs.json` lists the files that a build of
the repositories leaves on disk.

```
./gradlew :benchmark:run --args="workspace-conformance --out <directory>"
```

The command copies the fixture into a throwaway directory, makes each repository a Git repository with one commit
of a fixed author and date, and scans the copy at the default level with a throwaway index. The fixture stays
untouched. It writes `workspace-links.json` and `workspace-links.md` to the output directory, and without `--out` it
only prints the table.

- A gold relationship is found when the scan holds a link between the two ends it names, in the relation kind of its
  class: modules for the dependencies, otherwise a file and a line of a declaration or use. A gold address without a
  line stands for any line of its file.
- The table gives, for each class, the gold count, the links found, the recall, the found links that carry the
  expected evidence class, and the links of the class between repositories that no gold relationship names (inside
  the web repository for `bundle_file_to_source`). The last number is reported and is not gated.
- The classes whose links rest on the sources are scored on the first scan. `bundle_file_to_source` needs the files
  of a built bundle, so the command writes the outputs of `outputs.json` into the copy and scans again before it
  scores that class.

The gate holds for every class: recall 1.00 and the expected evidence class on every link found. The command
exits with 1 when one class misses it, and a test runs the same check. A class that meets it can be given a row in
the [support matrix](support-matrix.md).

### Level 4 tasks on the workspace fixture

Six tasks over the workspace fixture are the level 4 tasks of the benchmark: each needs edits in exactly two
repositories (see [results by task level](benchmarks/task-levels.md)). They live in `benchmark/workspace/tasks/`,
one file each, in the format of the reference tasks plus a `level` and a `named` flag. Paths are workspace paths,
`<repository directory>/<path>`, and `relationships` names ids of `gold/relationships.json`.

- Each task changes both ends of one kind of link between repositories: a dependency by Maven coordinates, a JAR
  file dependency, the C++ JNI library with its Kotlin `external` function and `System.loadLibrary` call, the same
  for the Rust JNI library, an OpenAPI operation with its Ktor route and generated client, and a web bundle with the
  Gradle copy task that packages it.
- Three statements name the code to change and three describe the behaviour only.
- The gold files, symbols with their lines and relationships were read from the fixture, not taken from KrosScope.
  A test checks that every path exists, that every symbol line holds its declaration, that the edited files lie in
  two repositories and that every relationship joins repositories of the task. Another test scans the workspace and
  checks that every symbol resolves to at least one scanned declaration through the matcher that the scoring uses.

```
./gradlew :benchmark:run --args="workspace-tasks --out <directory> [--budgets 2000,4000,8000,16000]"
```

The command prepares the fixture as `workspace-conformance` does, writes the build outputs of `outputs.json`, scans
once, and runs the localization and the context benchmark over the six tasks with the methods, baselines and budgets
of the reference run. It writes `workspace-tasks.json` and `workspace-tasks.md`: recall@5, recall@10 and NDCG@10, and
gold recall and token precision at each budget, as `KrosScope / BM25 English / BM25 raw`, for each task, for all
tasks, and for the named and the not-named tasks. The results describe these six tasks. There is no gate, and the
command exits with 0 whenever the run completes.

### Evidence classes

An edge carries the class of the mechanism that established it, not the kind of thing it connects:

| Class | The edge was established by |
|---|---|
| `compiler` | The compiler front end, which bound the reference. |
| `build-model` | What follows from the module, source-set and variant model of the build. |
| `generated-artifact` | Reading the output of a generator. |
| `convention` | A documented naming rule of a generator or tool, applied without reading its output. |
| `textual` | A match by name or string, such as a fully qualified class name written in a manifest. |
| `structural` | The arrangement of files and declarations. |
| `historical` | Version-control history. |
| `ml` | A learned model. |

Gold states the class of every edge in `expectedEvidence`, and a matched edge is correct when its class is the one
gold states. One pair of classes is treated as equal:

| Gold states | Also accepted | Why |
|---|---|---|
| `generated-artifact`, for a hop from a source of truth to a surface that a generator makes | `convention` | The producers derive the surface from the documented naming rules. |

- The report counts these matches by the class that was produced in its Accepted column.
- No other pair of classes is treated as equal, and a matched edge of any other class that differs from gold is
  an evidence mismatch.

## Analyzer benchmark

Runs with `kscope benchmark`; no coding agent involved.

| Group | Metrics |
|---|---|
| Cost | Full index time, incremental index time, peak RSS, database size, query latency (p50/p95). |
| Graph correctness | Precision and recall for symbols, relationships (per class), build model; unresolved-edge rate; share of the workspace analysed at each evidence class and resolution state. |
| Task localization | Precision/recall@k for affected files and symbols, test-selection precision/recall, NDCG@10 and MAP, false-positive rate. |
| Context | Tokens per level, gold-context recall and token precision at fixed budgets (2k, 4k, 8k, 16k), tokens needed for full and for target (90%) gold coverage. |
| Incremental correctness | Number of differences between incremental update and clean rebuild (must be zero). |

Baselines reported next to every localization and context number: BM25 over file text (top files,
truncated to the same budget) and whole-file packing in path order.

Task localization additionally reports raw BM25 with the fixed standard English stop-word list and the
same code-tokenizer/symbol/path lexical retrieval as KrosScope.

- Raw baselines include every readable, non-generated UTF-8 file within the size limit, including native and
  unclassified text. Their corpus exclusion counts are reported.
- Lexical, graph, source-of-truth projection and category-prior ablations identify each component's
  contribution; full retrieval also includes history.

The held-out statements follow these rules:

- Each T01–T14 original statement has an independent paraphrase under `benchmark/reference/heldout`.
- The paraphrase author saw only the original statement, and each paraphrase retains its original gold.
- Retrieval code and constants were frozen before held-out scoring.
- That held-out set is now spent: it remains an evaluation record and is never used for further tuning.
- New retrieval changes use a chronological development split, followed by a new freeze and one evaluation on
  its test split.
- Original and held-out fixture results stay separate.

The gates are:

- **Tier 1** requires strict improvement over both raw BM25 and English-stop-word BM25 on recall@5, recall@10
  and NDCG@10, separately on every task set.
- **Context Tier 1** requires strict improvement over both BM25 baselines on gold-context recall and token
  precision at every budget.
- Without Tier 1, no claim is made that KrosScope beats text search.
- The earlier strongest-lexical gate and its failure remain in the historical localization report.

Tier 2 and the targets:

- **Tier 2** compares full retrieval with lexical-only retrieval, using a paired bootstrap over tasks with
  10,000 resamples, seed 20261005 and a two-sided percentile 95% confidence interval.
- A difference is significant only when its interval excludes zero.
- Tier 2 is reported and does not decide that claim.
- Test precision/recall targets are 0.5/0.8; symbol recall@10 targets 0.5. Missing these targets is a reported
  limitation.
- Change-scope precision, recall and exact match are measured separately for modules, source sets and variants,
  with an overall exact match.
- Explicitly empty gold dimensions count unwanted predictions as false positives; absent gold stays unmeasured.

### Context coverage

All context methods use the same tokenizer and count the complete Markdown package, including its
task text, file headings, fences and warnings. The default is `o200k_base`; `cl100k_base` and the explicit
`chars_4` heuristic are also available. Reported budgets are 2,000, 4,000, 8,000 and 16,000 tokens.

| Metric | Definition |
|---|---|
| Gold-context recall | Gold files containing a nonempty selected source interval and gold symbols whose complete declaration interval is covered, divided by the number of gold files and symbols. File and symbol coverage are also reported separately. |
| Token precision | Tokens in the union of selected raw source intervals inside gold files, divided by the complete package token count. |

- Gold files include required edits, required reads and files containing gold symbols.
- Files that a task requires creating are excluded because they have no source at the base revision.
- Normalized signatures, outlines and generated summaries do not claim declaration-body coverage.
- Coverage requires the fragment text to match the source and to occur in the rendered package.

How the methods and the costs are packed and measured:

- Raw BM25, English-stop-word BM25 and path-order baselines pack whole files, truncating a source prefix when
  necessary.
- Compiler ablations use the same packing policy with lexical-only or full retrieval.
- Costs for 90% and 100% gold-file coverage require complete gold-file contents and include earlier nongold
  files in the baseline ranking.
- Compiler costs report the smallest tested budget reaching the target and the corresponding package token
  count. This is an upper bound from the tested grid, not a global minimum: greedy selections can change
  nonmonotonically with the budget.
- A target not reached through the largest tested budget is reported as such.

```text
./gradlew :benchmark:run --args="context-benchmark --mode default --out <directory>"
```

The optional `--budgets 2000,4000` limits the run to those standard budgets; the result then
reports only them and is never a Tier 1 evaluation: the report states `Tier 1: NOT EVALUATED`, lists the
budgets it measured, and the JSON `tier1Passed` is false.

Historical localization results can be evaluated under the two-tier policy without scanning or
reranking. This writes new output files and preserves the original result:

```text
./gradlew :benchmark:run --args="reclassify-localization --input <saved-localization.json> --out <new-directory>"
```

## Historical commit evaluation

The real-repository benchmark derives tasks from the pinned histories of Now in Android and the KotlinConf app.
It inspects at most 400 recent non-merge commits per repository and selects up to 60 eligible tasks. A task is
eligible under these rules:

- Commits with non-UTF-8 metadata or paths are excluded as `undecodable-commit`.
- A task must change one to eight source files that exist at its parent revision.
- Bot commits, identified whitespace-only or code-formatting changes and pure renames are excluded.
- New or copied files are omitted from gold; existing changed files in the same commit remain eligible.
- Deleted sources and content-changing renames use their parent paths as gold.
- Whitespace comparison preserves literals, token boundaries and newlines; uncertain syntax is retained rather
  than erased.
- Ancillary changes outside the source-file eligibility set are allowed.
- Source eligibility uses a dedicated artifact detector; it does not narrow the readable-text corpus used by
  BM25 baselines.

Git text is decoded strictly as UTF-8, with log output requested in UTF-8. Source blobs that cannot be decoded
are compared as bytes without whitespace normalization; BM25 continues to count undecodable files as binary.

The task statement and the gold:

- The task statement is the commit message after removing repository paths, changed source basenames and
  pull-request numbers.
- Gold consists of the eligible changed source files at the parent.
- The benchmark does not derive symbol or relevant-test gold from a commit diff, so those targets remain
  unmeasured for this corpus.
- Exclusion counts are retained.

Gold is defined by the parent revision's Git tree, independent of what the index holds:

- A gold file that the index or the readable-text corpus lacks is never dropped: every method counts it as not
  retrieved, so recall, NDCG and context recall keep the full gold size as their denominator.
- The report states how many gold files and tasks were affected.
- Token precision is unaffected because an absent file contributes no tokens.

The history windows:

- The eligibility window is capped at 400 non-merge candidates; it does not cap production history signals.
- At each parent revision, the history producer uses up to 2,000 reachable non-merge ancestors, bounded by the
  available shallow history.
- These two windows are reported separately, and no descendant of the indexed parent contributes a history
  signal.

<details>
<summary>How deepening the shallow clones changed the commit count</summary>

Deepening the two initially shallow clones by 399 Git ancestry levels retained 3,045 reachable commits for Now
in Android and 1,237 for KotlinConf because merge ancestry branches. This exceeds a corpus of approximately 400
total commits.

</details>

How the tasks are split and frozen:

- Selected tasks are ordered by committer timestamp and commit identity within each repository.
- The older half is development data and the newer half is test data.
- Repository revisions, eligibility, task assignments, implementation hashes and five incremental-verification
  tasks are frozen before test evaluation.
- Test outcomes do not guide implementation changes.
- Any later development-only experiment requires its own freeze and separately reported test evaluation.

Each task uses a scratch checkout whose detached HEAD is the parent. The index and history therefore end before
the target change. Then:

1. A full default scan is timed, followed by localization and context measurement for all methods and ablations.
2. Only then is the child revision checked out and its incremental update timed. The five preselected updates
   also compare the result with a clean rebuild.

What is reused and measured:

- One index per repository is reused between tasks; the original checkouts are preserved.
- Localization measures all eight methods and ablations.
- Context measures the five methods of the fixture context benchmark: the full compiler, the lexical-only
  compiler, raw BM25, English-stop-word BM25 and path order.

What the results report:

- Results report each repository and the pooled tasks, with development and test sets kept separate.
- Task-paired bootstrap intervals use the same policy as the fixture.
- Source packages and individual raw records remain private; published reports contain scrubbed aggregates,
  failures, coverage and timing limitations.
- A failed or incomplete task is explicit and cannot count as a successful or complete benchmark set.
- An unexpected non-fatal error in one task is recorded as that task's failure with its phase and does not stop
  the remaining tasks; fatal errors such as running out of memory stop the run.

The repositories file lists the local clones and their pinned revisions:

```json
[{"id": "<name>", "checkout": "<path to a local clone>", "head": "<full commit hash>"}]
```

How a run is prepared:

- Preparation reads the clones without modifying them and writes the manifest of eligible tasks.
- The manifest, its digest and the implementation hashes are frozen before any test-split scoring; the SHA-256
  of the freeze file is the checkpoint key of every run.
- The development split is run and reported first, the test split once.
- The output directory must lie outside the clones.

```text
./gradlew :benchmark:run --args="real-repos-prepare --repositories <repositories.json> --out <manifest.json>"
./gradlew :benchmark:run --args="real-repos-run --manifest <manifest.json> --out <run-directory> --split dev --checkpoint-key <sha256>"
./gradlew :benchmark:run --args="real-repos-report --manifest <manifest.json> --input <run-directory> --out <report-directory>"
./gradlew :benchmark:run --args="real-repos-run --manifest <manifest.json> --out <run-directory> --split test --checkpoint-key <sha256>"
./gradlew :benchmark:run --args="real-repos-report --manifest <manifest.json> --input <run-directory> --out <report-directory>"
```

How runs are stored, resumed and reported:

- Each completed task is stored as one record in the run directory.
- Issuing the same run command again resumes: completed records are kept, and a run directory used with a
  different manifest or key is rejected.
- The optional `--repository <id>` runs only that repository's tasks, so the repositories can be measured by
  separate processes that share one run directory.
- The optional `--budgets 2000,4000` scores only those of the four standard context budgets; the budget set is
  part of the run binding, so a run directory cannot mix budget sets.
- The report lists the budgets it contains, and a report that lacks any standard budget cannot pass Tier 1.
- The context timing of a task whose context was resumed from a checkpoint is not a measurement, so the timing
  statistics leave it out, and their sample count shows how many tasks they hold.
- The report command exits 0 only when every group is complete, valid and passes Tier 1; a report over a partial
  run lists the observed tasks and cannot pass.

### Tasks over several repositories and task levels

A task may change files in several repositories, and a manifest may label its tasks with a level and a naming
flag. All three additions are optional, and a manifest without them is read, run and reported exactly as before.

The fields of a task in the manifest:

- `level` is the level of the task from 1 to 5, as defined in [results by task level](benchmarks/task-levels.md).
- `named` says whether the task statement names the code that has to change.
- `workspace` lists the repositories of the task as `{"repository", "parent", "commit"}` entries. The entry of the
  task's own `repository` must repeat the task's `parent` and `commit`. Without `workspace` the task has the one
  repository that it names.

How the runner measures a task with a `workspace`:

- It clones every listed repository into a scratch workspace root, side by side, in a directory named like the
  repository, and checks each out at its `parent` revision. The index covers the whole root, with one
  repository per directory.
- The gold files are workspace paths, `<directory>/<path>`, and each must lie in a listed repository. A task of
  one repository keeps repository-relative paths.
- After scoring, every listed repository moves to its `commit` revision for the incremental update.
- Tasks of the same own repository that list the same repositories share one scratch root and one index.
- The user's checkouts are never modified, and the output directory must lie outside all of them.

How the report groups the results:

- When any task has a `level` or a `named` flag, the report adds the section "Results by task class". It holds one
  group for each level and one for named and for not-named tasks, split by DEV and TEST, with the same tables as
  every other group.
- A compact table shows recall@5, recall@10, NDCG@10, and gold recall and token precision at each context budget as
  `KrosScope / BM25 English / BM25 raw`. The table in [results by task level](benchmarks/task-levels.md) is
  generated from it.
- The groups describe. They do not enter Tier 1, which is decided by the repository groups as before.

Running a catalog that is kept outside this repository:

- A catalog lists, per task, the prompt, the level, the named flag, and for every repository its base, scenario and
  solution commit and its gold files. The freeze file stores the SHA-256 of the catalog, with LF line endings, and
  the base commit of each repository.
- `real-repos-import` checks the catalog against the freeze file and every repository against its base commit,
  checks that each scenario commit descends from its base commit and each solution commit from its scenario
  commit, and writes a manifest. The checkout directory holds one clone per repository, in a directory named like
  the repository.
- A task with gold in one repository becomes a task of that repository. A task with gold in several repositories
  becomes a workspace task over all its repositories, whose own repository is the one that the catalog names.
- The import reads the clones and changes nothing in them. The manifest and the run output stay outside the
  repository.

```text
./gradlew :benchmark:run --args="real-repos-import --catalog <tasks.json> --freeze <freeze.json> --checkouts <directory> --out <manifest.json> [--split dev|test]"
./gradlew :benchmark:run --args="real-repos-run --manifest <manifest.json> --out <run-directory> --split test --checkpoint-key <sha256 of freeze.json>"
./gradlew :benchmark:run --args="real-repos-report --manifest <manifest.json> --input <run-directory> --out <report-directory>"
```

The import puts every task in the test split unless `--split dev` is given, because nothing was tuned on a catalog
task. The level and the named flag come from the catalog, not from the run.

## Agent benchmark

A harness runs a coding agent headlessly on reference tasks in isolated copies of the workspace and decides
success with checks the agent never sees.

Arms:

- **A** agent alone
- **B** agent + conventional retrieval baseline (BM25 context at equal budget)
- **C** agent + deterministic KrosScope context
- **D** agent + deterministic + local ML context (when ML exists)
- Controls (optional): oracle context; random context at equal budget

Collected per run:

| Group | What is collected |
|---|---|
| Tokens and cost | Uncached input, cached input, cache-write, output and reasoning tokens where exposed; cost as the agent reports it. |
| Time | Wall-clock and API time; KrosScope index and context latency. |
| Agent behaviour | Model turns; tool calls by type; file reads, unique files and bytes read; the event index and elapsed time of the first read and first edit of a gold file. |
| Context | Context size; context precision/recall against gold. |
| Results | Compilation and test results; regressions; files modified and unnecessary files modified. |

Derived: tokens, cost, time and tool calls per successful task; exploration reduction; context reduction
ratio; cache hit ratio; success-normalized cost and tokens.

Method:

- Fully crossed and paired: every task under every arm, same model, agent version, prompt template,
  permissions and environment; arm order randomized within each task and repeat; each run in a fresh session
  with isolated configuration so no arm reuses another's cache.
- At least three repetitions per task and arm; success reported as pass@1 averaged over repetitions.
- The index is built at the task's base state; workspace copies carry no post-fix history.
- Success is decided by executable checks (compilation, fail-to-pass tests, pass-to-pass tests).
- Analysis is per-task paired: log-ratios against arm A for continuous metrics, paired bootstrap over
  tasks for success. Cost is reported per attempt, per solved task and on the subset solved by all arms.
- KrosScope is charged its own indexing time, analysis time and injected tokens.

### Task packages

A task package lives in `benchmark/reference/tasks/<id>/` next to the task gold of the same id:

| File | Content |
|---|---|
| `task.json` | Statement, gold files, optional files, the Gradle tasks of the verification, and the fail-to-pass and pass-to-pass test ids. |
| `hidden/` | Test sources, copied into the workspace only after the agent finished. They replace files of the same path. |
| `reference.patch` | A correct change, made with `git diff`. It is never copied into a run. |

The statement of a package starts with the statement of the task gold. Where a hidden test has to name something
the statement leaves open, the package adds one sentence that names it. Six packages exist:

| Id | Kind of change | Verification |
|---|---|---|
| T01 | New column with a migration (SQLDelight), model and mapper | `:data:db:desktopTest`, `:core:model:desktopTest` |
| T02 | New protocol field wired through the sync engine and both apps (Wire) | `:sync:engine:test`, compile of the desktop and Android apps |
| T04 | Android string resource rename across values, translation, navigation and code | `:feature:tasks:testDebugUnitTest` |
| T05 | New `expect`/`actual` function used by shared code | `:core:model:desktopTest`, Android compile of the library |
| T06 | Flavor-specific behavior in a product flavor source set | `:app:testDemoDebugUnitTest`, `:app:testFullDebugUnitTest` |
| T07 | Bug fix in Kotlin code used from Java | `:core:legacy:test` |

```text
./gradlew :benchmark:run --args="agent-tasks-validate --out <directory>"
```

`agent-tasks-validate` checks every package three times in fresh workspace copies:

- the base state with the hidden tests must fail verification;
- the base state with the reference patch and the hidden tests must pass it.

It also rejects a patch that touches a file outside the gold and optional files.

- It writes `validation.json` and `validation.md`, and exits with 1 when a package is not valid.
- Verification runs the project's own Gradle wrapper with `--no-build-cache`, so a repeat is a real run.
- The workspace copy needs the Android SDK, which the runner takes from `ANDROID_HOME` or from `--android-sdk`,
  without writing it into any file.

### Isolation

Each run gets its own copy of the workspace below the system temporary directory, never inside the repository.

- The copy is made from the Git index of the workspace, so it has canonical line endings and none of the ignored
  or untracked files, and it receives a fresh history of one commit with a neutral identity.
- Gold, the reference patch and the hidden tests live outside the workspace and reach the copy only when
  verification starts, and the harness refuses a copy that holds an instruction or planning file or a patch.
- Prompts, event streams and logs are kept apart from the copy.
- The index for arms B and C is built outside the copy, at the base state.

### Running

```text
./gradlew :benchmark:run --args="agent-run --out <directory> --agent claude --model sonnet --arms A,B,C --repeats 3 --budget 4000"
./gradlew :benchmark:run --args="agent-report --input <directory> --out <report-directory>"
```

| Option | Meaning |
|---|---|
| `--agent claude`, `--agent codex`, `--model` | Adapter and model. `claude` defaults to `sonnet`; the model is recorded. |
| `--arms`, `--repeats`, `--only` | Arms (default A,B,C), repetitions per task and arm (default 1), a list of task ids. |
| `--budget` | Token budget of the context of arms B and C (default 4000), counted with the `o200k_base` estimator for both. |
| `--timeout-minutes`, `--global-minutes` | Limit of one agent run (default 15) and of the whole command, which also bounds the verification of a run. Runs that the global limit leaves out are reported as not started, and a verification that it cuts short is a failed run. |
| `--prompt-transport` | `stdin` (default) or `argument`: how the prompt reaches the agent. |
| `--agent-executable`, `--android-sdk` | A program to run instead of the one found on the path; the Android SDK for verification. |
| `--keep-failed`, `--keep-workspaces`, `--seed` | Keep failed records when the command is issued again; keep the workspace copies; seed of the arm order. |
| `--dry-run --stand-in noop`, `--dry-run --stand-in reference` | Everything except the agent. `noop` changes nothing and must fail verification; `reference` applies the reference patch and must pass it. The command exits with 1 on a contradiction. |

- The output directory holds one JSON record per run and is bound to the agent, model, budget and prompt of its
  first run.
- Issuing the same command again keeps completed runs and runs the rest; failed runs are run again unless
  `--keep-failed` is given. A kept run must have been made from the same Git index of the workspace and the same
  task package, with every file of it; a run whose inputs changed is run again.
- A task directory without a task package is refused.
- A run that stops because of the agent (authentication, timeout, non-zero exit, an error result, a stream
  without result) is a failed run with a reason and never a task failure.
- A timeout stops only the process tree the harness started.

`agent-report` writes `aggregate.json` and `report.md`: success beside tokens, cost and turns per arm and per
task, ratios to arm A, tokens and cost per solved task, and the failed, missing and warned runs. The report holds
no paths, prompts or source.

### Adapters

- `claude` runs `claude -p` with `--output-format stream-json --verbose --setting-sources "" --strict-mcp-config
  --disable-slash-commands --tools "Read,Edit,Write,Glob,Grep" --permission-mode acceptEdits
  --no-session-persistence --no-chrome` in the workspace copy.
  - The agent can read, search and edit files; it has no shell and cannot build or test.
  - The child environment holds no variable whose name starts with `CLAUDE` or `ANTHROPIC`, so that a nested
    session does not inherit the settings of the one that started it.
  - The prompt is written to a file that becomes standard input, which closes after it: a long prompt with
    newlines and quotes is not an argument, and the length limit of a Windows command line does not apply.
  - The option `--prompt-transport argument` passes it as the argument right after `-p`, which is limited to
    about 32,000 characters on Windows.
- `codex` runs `codex exec --json --ephemeral --skip-git-repo-check -s workspace-write -C <workspace>` with the
  prompt on standard input. Unlike `claude`, it has a shell by design, so its runs can build and test and are not
  comparable with `claude` runs.

Telemetry is read from the event stream and tolerates unknown event types and missing fields.

- For `claude`, totals come from the result event, or from the messages when the run ended without one.
- For `codex`, input tokens are the uncached part of the reported input, cache-creation tokens are not exposed,
  files read are the workspace files that its commands mention, and a turn is a completed Codex turn, not a
  model call.
- The `init` event of `claude` lists tools, servers and skills; anything beyond the five allowed tools is
  reported as a warning of the run.

### Limits

- Cost is the cost the agent reports; no price table is applied yet.
- Verification covers what its tests check. Where a task asks for wiring in applications (T02), a text check
  stands in for running them, and the applications are compiled.
- Verification of an Android task compiles against the host's Android SDK and runs unit tests on the host; no
  device is involved.
- One repetition cannot separate an arm from noise; the method asks for three.
- Arm D, the oracle and the random control are not part of the harness.

## Reporting

Published results (`docs/benchmarks/`) state commit, environment, tool and model versions, tokenizer,
and the full metric table, including where KrosScope loses.
