# Held-out task statements

This set adds an independently paraphrased statement for each original task T01–T14 to test sensitivity
to phrasing. It changes no original edit, read, symbol, relationship, test or change-scope gold.
Task IDs pair each statement with that unchanged gold.

The paraphrase author received only the original statements. Source code, ranking configuration and
benchmark results were unavailable. Requirements and constraints were preserved without adding file names,
symbol names or implementation mechanisms.

Retrieval code and constants are frozen and hashed before scoring this set. Original-task diagnostics may
inform general fixes before that freeze. Held-out scores are reported separately and are not used to tune
the frozen implementation. A failed held-out gate stays a reported failure. The scoring freeze is recorded
in `freeze.json` when the implementation is ready for its first held-out evaluation.
