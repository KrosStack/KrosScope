# ADR 0013: Token estimation

Status: Accepted · Date: 2026-10-07

## In short

- Token counts come from a `TokenEstimator` that is named in every result. The default is JTokkit `o200k_base`.
- `cl100k_base` and the character heuristic are available too, selected by name.
- No calibration factor, safety margin or per-model correction is applied. A caller that wants headroom lowers
  the budget it passes.
- A budget is exact for the named tokenizer and an estimate for any other. The size of that error is not
  measured.

## Problem

The context compiler works to a budget in tokens, but the tokenizer of the model that will read the context is not
known to KrosScope and cannot be queried: that would need a network, an account and the model's vendor.

Counting must still be:

- exact for the text that is produced,
- deterministic,
- quick enough to run many times in one selection, and
- honest about what it is.

A count that silently stands for another tokenizer, or that carries a hidden correction, would make a budget
look more precise than it is.

## Options considered

1. **Count with the API of a model provider.** Exact for that provider, but it needs the network and credentials and
   makes the result depend on a service. Rejected by the principle that everything runs locally.
2. **A character heuristic only.** No dependency and no start-up cost, but its error against any real tokenizer is
   unmeasured and varies with the language and the symbols of the code.
3. **A byte-pair-encoding tokenizer embedded in the process, with named vocabularies, and the heuristic as an
   explicit, named fallback.** Chosen. JTokkit implements the `o200k_base` and `cl100k_base` vocabularies in Java.
4. **A calibration factor per target model**, a multiplier that scales a count towards the tokenizer of a particular
   model. Rejected: nothing in the project measures such a ratio, a factor would hide which tokenizer a number came
   from, and it would turn a budget into a guess about a model that the tool cannot see.

## Evidence

What the code does:

- **Contract.** `TokenEstimator` in `core/spi` has an `id`, `count(text)`, `count(parts)` and `truncate(text,
  maxTokens)`. `count(parts)` counts the exact concatenation: the boundaries between parts do not separate tokens.
  `truncate` returns a prefix that fits and never cuts inside a surrogate pair, found by bisection over code points.
- **Implementations.** `TokenEstimators.create` returns:
  - `o200k_base` (the default) and `cl100k_base`, both through JTokkit 1.1.0 (`countTokensOrdinary`);
  - the heuristic `chars_4`, which is the number of code points divided by four, rounded up.

  The command line accepts `o200k_base`, `cl100k_base` and `chars`; `chars` is reported as `chars_4`. An unknown
  id is an error. Nothing scales a count: each estimator returns the count of its own vocabulary or rule.
- **Identification.** Every context package records the id of the estimator that counted it, and its token counts are
  those of that estimator. The context sizes that `analyze` reports use `o200k_base`.
- **Speed without approximation.** The compiler counts the complete Markdown, and counts blocks separately and adds
  them up only where the blocks cannot merge into one token (see [ADR 0012](0012-context-package-and-budget.md)); the
  costs of source ranges are cached by content hash, source range and tokenizer.

What was measured:

- The benchmark pages that report token counts use JTokkit `o200k_base`, for KrosScope and for the BM25
  baselines alike. Those numbers measure coverage, not coding success.
- No page compares these counts with the counts of any particular model, so the size of the error against a
  model that uses a different tokenizer is not known.

## Decision

- Token counts come from a `TokenEstimator` that is named in every result, and the default is JTokkit `o200k_base`.
- `cl100k_base` is available for budgets meant for models that use it, and the character heuristic is available when
  no vocabulary should be loaded; both are selected by name, never by a hidden switch.
- No calibration factor, safety margin or per-model correction is applied inside KrosScope. A caller that wants
  headroom lowers the budget it passes.
- A new estimator is added by implementing `TokenEstimator` with its own id, not by changing what an existing id
  means.

## Consequences

- A budget is exact for the named tokenizer and an estimate for any other. For a model with another tokenizer the
  caller should leave headroom; the size of that headroom is not measured.
- Results can be compared and reproduced because they name their tokenizer. A change of tokenizer changes the ids, and
  so the numbers cannot be mistaken for the old ones.
- The JTokkit vocabularies are bundled with the distribution, and each is loaded on the first count that asks for
  it.
- Supporting a further vocabulary is a small, local change, but each new one needs its own check that blocks can be
  counted separately.

## Reversal cost

Low. The estimator is a one-method-family interface behind `service`, results carry the estimator id, and the
dependency is declared once in the version catalog. Replacing JTokkit would change counts and therefore the budgets of
saved results, which is visible through the id.
