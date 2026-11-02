# ADR 0012: Context package format and budget algorithm

Status: Accepted · Date: 2026-10-07

## In short

- The compiler turns the ranked files into one Markdown package that fits an explicit token budget and lists
  what it left out. The same index, task, level, budget and estimator give the same bytes.
- The budget counts the complete rendered Markdown with the estimator named in the package.
- From 8,000 tokens the compiler chooses renderings by gain per token. Below 8,000 it packs raw source only.
  Without a budget it renders every offer at its richest.
- The compact policy raised token precision by a factor of 2.7 to 7.1 in every group at 2,000 and 4,000 tokens.
  Token precision at 8,000 and 16,000 tokens still fails, and the overall context verdict is Tier 1 FAIL on both
  benchmarks.

## Problem

A coding agent has a limited prompt. The context compiler must turn the files that retrieval ranks into one block of
text that:

- fits an explicit token budget,
- says what it left out, and
- can be checked: the same index, task, level, budget and tokenizer must give the same bytes.

Two things go wrong easily:

- A budget that counts only the source text overruns once headings, fences and notes are added.
- A selection built to be clever at large budgets wastes small ones on signatures and summaries of files whose
  code the agent then never sees.

## Options considered

1. **Whole files in rank order until the budget is spent.** Simple and cheap, and it is what the BM25 baselines in the
   benchmarks do.
2. **One selection algorithm at every budget, choosing among several renderings of each node** (signature,
   declaration, outline, whole file, generated summary).
3. **Renderings chosen by relevance per token at large budgets, and raw source only at small budgets.** Chosen. It
   replaces option 2 below 8,000 tokens, after measurement showed option 2 losing there.
4. **A budget on the fragments alone, with the wrapper added afterwards.** Rejected: the package would exceed the
   budget the caller set.

## Evidence

What the code does:

- **Package.** A `ContextPackage` carries `schemaVersion: 1`. It holds:
  - the task, the level, the budget (absent when none was given) and the id of the estimator;
  - the token count of the complete Markdown;
  - the fragments, and the boundaries, tests and unresolved relationships that touch them;
  - the omissions with their reasons and the Markdown itself.

  A fragment names its artifact, an optional range, its node, its rendering (`SIGNATURE`, `DECLARATION`,
  `OUTLINE`, `FILE`, `ENTRY` or `GENERATED`), its role (`EDIT`, `READ`, `TEST` or `BOUNDARY`), its relevance and
  token count, its text, whether it is backed by source, and an evidence note.

  Source ranges are exclusive UTF-16 ranges taken against the indexed content hash, so a file that changed since
  the scan is omitted as stale and not quoted wrongly.
- **Markdown.** The Markdown starts with `Task: <task>`, then one fenced block per fragment. The fence is at least
  three backticks and longer than any run of backticks in the text; its first line is the path, the line range and
  the rendering in brackets. A final line reports how many unresolved relationships there are, when there are any.
- **Budget.** The budget limits the token count of that whole text, headings, fences, notes and the task line
  included.
  - Counting is exact for the Markdown as one string.
  - The counts of single blocks are added up only where the blocks end in a blank line, start with a letter or a
    backtick and are valid Unicode, so that the byte-pair tokenizers cannot merge across them. Otherwise the
    whole text is counted again.
  - A package whose text cannot fit at all is empty and carries one omission with reason `BUDGET`.
  - Without a budget every offer is rendered at its richest.
- **Offer and upgrade, at 8,000 tokens and above.** Each candidate node yields an offer: its renderings ordered from
  cheapest to richest. The selector repeats one step.
  - For every offer it prices the next rendering as a proposal: the exact token count of the whole package with
    it, and the gain in value.
  - The gain is the relevance of the fragment times a utility of 0.35 for a signature, 0.5 for an outline, 0.2
    for a generated summary and 1.0 for a declaration, entry or whole file.
  - It accepts the proposal with the highest gain per added token among those that fit and do not lose value,
    and stops when none is left.
  - Fragments of one file are merged and a whole-file fragment absorbs the others.
- **Compact policy, below 8,000 tokens.** The compiler packs raw source only, in rank order: whole files and
  declarations, with no signature, outline, generated summary, test signature or map.
  - A fragment that does not fit is skipped and later ones are still tried.
  - The remainder is filled with the longest line prefix of the first skipped file that is not yet represented,
    found by bisection.
  - A request without a budget never takes this path.
- **Determinism and order.**
  - Candidates and omissions are sorted by node key, ties never depend on hash order, and no clock or random
    value is read.
  - Fragments appear ordered by module, path, start line, start column and node key.
  - Omissions are sorted by node and reason, and each says why: `BUDGET`, `LEVEL_LIMIT`, `UNREADABLE`, `BINARY`,
    `NO_SOURCE`, `INVALID_RANGE`, `GENERATED_BODY`, `STALE_INDEX` or `OVERSIZED` (text over 2 MiB).

What was measured. Numbers are gold recall / token precision, where precision counts the source tokens of gold files
in the complete rendered Markdown ([Reading the numbers](../benchmarks/README.md#reading-the-numbers)).

- **Why small budgets changed.** In the first real-repository measurement
  ([Real-repository benchmark](../benchmarks/real-repositories.md)) the compiler's token precision was below both BM25
  baselines at every budget.
  - On the pooled test split at 2,000 tokens it was 0.0278, against 0.0849 for raw BM25 and 0.0945 with stop
    words removed.
  - At 4,000 tokens it was 0.0364 against 0.0724 and 0.0834.
  - The page's reading is that small budgets were spent mostly on signatures, outlines and maps.
- **What the compact policy bought.** The change was kept under a rule written before measuring, then frozen, then
  scored once on the test split, as the section
  [Small-budget packing](../benchmarks/real-repositories.md#small-budget-packing-realrepos-v2) records.
  - Pooled test, 2,000 tokens: 0.3893 / 0.1642, against 0.1377 / 0.0849 and 0.1474 / 0.0945 for the baselines.
  - At 4,000 tokens: 0.4002 / 0.1017, against 0.2432 / 0.0724 and 0.2432 / 0.0834.
  - Token precision rose by a factor of 2.7 to 7.1 in every group at both budgets, and the compiler beats both
    baselines on both metrics in five of six test cells.
  - On the fixture all four
    cells, two statement sets at 2,000 and 4,000 tokens, pass.
- **What it did not fix.** On the development split the compiler still trails stop-word BM25 on precision in five of
  six cells, and one test cell (kotlinconf-app at 4,000 tokens) fails on precision. Recall is not monotonic in the
  budget, because greedy whole-file packing can spend a larger budget on one large file.
- **Why large budgets keep the offer and upgrade selection.** At 16,000 tokens the pooled test recall is 0.6758
  against 0.3752 and 0.4043 for the baselines, though precision is 0.0362 against 0.0431 and 0.0452. Packages at 8,000
  tokens and above are byte-identical to the ones before the compact policy, which a test pins over 48 packages.
- **What still fails.** Token precision at 8,000 and 16,000 tokens in every real-repository group, and, on the fixture,
  8,000 tokens on both statement sets (original 0.6723 / 0.1121 against 0.5561 / 0.1346 for stop-word BM25). The
  overall context verdict is Tier 1 FAIL on both benchmarks.
- **Cost.** Scoring five methods at 2,000 and 4,000 tokens took 2.5 seconds per task, against 361 seconds for four
  budgets before the change; the slow part is the selection used at 8,000 and above.

## Decision

- The package is the structure above, with Markdown as its readable form and JSON (`--format json`) as its
  machine-readable form. The budget always counts the complete rendered Markdown with the estimator named in the
  package ([ADR 0013](0013-token-estimation.md)).
- From 8,000 tokens the compiler chooses renderings by gain per token. Below 8,000 it packs raw source only.
  Without a budget it renders every offer at its richest.
- The package is deterministic for a given index, task, level, budget and estimator.
- Anything left out is listed with its reason, and a package never claims more than it contains.

## Consequences

- An agent gets code at small budgets, and the budget it set holds exactly. The cost is that below 8,000 tokens the
  package has no signatures of files it could not fit, and no map.
- The threshold of 8,000 is the smallest of the four budgets measured at which the old selection was kept. Behaviour
  between measured budgets, for example at 6,000 tokens, has not been measured.
- The two regimes are two code paths with different quality profiles. Extending the compact policy upward, or giving
  the large-budget selection a precision-aware rule, is the next experiment, and needs a new freeze and a held-out set
  that has not been scored.
- Quality claims for context say what the benchmark pages say: coverage of the files of real commits, not coding
  success, which was not measured there.

## Reversal cost

Low for the compact policy: it is one branch in `ContextCompiler` and one packer, pinned by tests, and removing it
restores the earlier behaviour at every budget. Medium for the package format, which is part of the JSON contract and
has a schema version.
