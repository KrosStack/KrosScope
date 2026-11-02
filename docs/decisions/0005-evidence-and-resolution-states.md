# ADR 0005: Evidence and resolution states

Status: Accepted · Date: 2026-10-05

## Problem

The graph combines facts from a compiler, build models, generators, naming conventions, parsers, text matching
and history. If these are stored alike, guesses read as facts and an unexamined area reads as a clean one.
Consumers (ranking, impact analysis, reports) need to know how each fact was obtained and what was never
looked at.

## Options considered

1. **A single confidence float** per fact.
2. **A per-language support badge.**
3. **Binary resolved/unresolved** per relationship.
4. **Evidence class plus a resolution state per relationship.** Chosen.

## Evidence

- Mixed-precision graphs otherwise present guesses as facts, and the layers disagree at the margins. One
  Kotlin code-graph project found 59 of 1,003 definitions had no syntax-layer counterpart, and heuristic
  precision fell from 0.97 to 0.91 after a compiler-exact pass
  ([code-graph #67](https://github.com/cyberchronos00/code-graph/issues/67)).
- Per-language labels are poorly calibrated. codebase-memory-mcp rates Kotlin in its top tier
  ([project](https://github.com/DeusData/codebase-memory-mcp)) while an open report says call resolution
  picks the wrong target in a Kotlin monorepo
  ([#1555](https://github.com/DeusData/codebase-memory-mcp/issues/1555)). CKB's basic tier contains Kotlin
  ([CKB](https://github.com/SimplyLiz/ckb)).
- One language sits at different depths per relationship: compiler-resolved for Kotlin calls,
  convention-derived for JNI, artifact-derived for AIDL. Per-edge labels (Graphify's `EXTRACTED`, `INFERRED`,
  `AMBIGUOUS`; [Graphify](https://github.com/Graphify-Labs/graphify)) and per-request capability flags
  ([LSP](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification)) are the
  nearest precedents.
- Reasoning: a float mixes origin with strength and has no calibration method; a binary state cannot separate
  "looked, not found" from "did not look".

## Decision

- Every fact carries an evidence class: `compiler`, `build-model`, `generated-artifact`, `convention`,
  `structural`, `textual`, `historical` or `ml`.
- Every relationship carries a `Resolution`: `Resolved` with certainty `exact`, `probable` or `heuristic`;
  `Unresolved(reason)`, meaning the producer looked and found nothing; or `NotAnalysed(reason)`, meaning it
  did not look. Reason codes are namespaced.
- Provenance (producer id and version, ownership unit, input fingerprint) is recorded once per fact batch.
- Scope (source set, target, variant) is recorded per fact.
- Coverage counters are derived by counting these states, never stored separately. For example:
  external declarations 42, bound exact 31, probable 4, unresolved 7 (illustrative).

## Consequences

- Every producer must choose an evidence class and report what it skipped. A failed analyzer yields
  `NotAnalysed` facts, not a failed scan.
- Ranking and impact logic can weight or filter by class and certainty.
- A claim of exactness is valid only if corpus tests against gold sets support it.
- Changing the `Resolution` shape touches every producer.

## Reversal cost

Medium to high. All producers, the store schema and reports use the model. Adding classes or reason codes
is cheap; replacing the resolution states changes every module.
