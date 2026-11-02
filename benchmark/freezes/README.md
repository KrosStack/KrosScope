# Evaluation freezes

`context-v1.json` records the implementation, input hashes, tokenizer, budgets and comparison policy
for an incomplete initial context run. That run was stopped during the second original task because
repeated full-package tokenization was too expensive. It had not reached the paraphrases, and no
partial scores were inspected. The subsequent performance corrections preserve exact selection,
rendering and token counts on synthetic equivalence cases.

Completed context measurements use a later freeze. Original statements and spent paraphrases remain
separate; the paraphrases are not used for tuning. Localization policy reclassification uses the earlier
saved predictions without reranking.

Hashes describe the exact Windows working-tree bytes used for the measurement. A checkout with
different line endings can have different byte hashes. Later implementation changes require a new
freeze and a development/test protocol; existing freeze files remain historical records.

`selfbench-v1.json` freezes the [self benchmark](../../docs/benchmarks/self-benchmark.md), the historical-commit
evaluation applied to the history of this repository. Its `baseCommit` names a commit of the current history.

## Digest of a freeze file

A result page quotes the SHA-256 of its freeze file, and that digest is the checkpoint key of the run. The digest
is that of the file as it was written on the measuring machine, with CRLF line endings and one final LF. The
files are stored byte for byte, so the SHA-256 of a checked-in freeze file is the quoted digest.

The [benchmark methodology](../../docs/benchmark.md) defines both gate tiers and source-coverage metrics.

## Commit identifiers

The `baseCommit` and `baseRevision` fields of the freeze files name commits of an earlier history of
this repository. The history was rewritten once to correct commit metadata, one commit subject and
one comment line in `.gitignore`; no other file content changed, so every file hash in the freeze
files still describes the same bytes. The freeze files themselves were left untouched, because
their own SHA-256 values are quoted in the result pages. The recorded identifiers correspond to
these commits of the current history:

| Freeze file | Recorded identifier | Current commit |
|---|---|---|
| `benchmark/reference/heldout/freeze.json` | `0cf25a15f17e3291702a1bb68ab56fa2591cd20a` | `513be60107d60d583f32fd3c006e70b46f997030` |
| `context-v1.json`, `context-v2.json` | `f7707943b933aec019ae9fc64c9c4a26fbafa2c4` | `66b4f58e5586444c291b7ff30555d5f9b26c2bcb` |
| `realrepos-v1.json` | `2f509c23e91195471bca0d7cdbf9e22a7418e7e6` | `cd50e244ef2065f8040730d6a4ccff9f42a5e777` |
| `realrepos-v2.json` | `988769ff230bd9568179365ee31cd2bf69707b04` | `37212a5df014e48ba9683049d0a260428d4c6bcc` |

The result pages under `docs/benchmarks/` cite the current commits.
