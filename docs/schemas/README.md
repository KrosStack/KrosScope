# JSON result schemas

These Draft 2020-12 schemas describe version 1 of the CLI JSON results and benchmark reports.
They are generated from Kotlin serialization descriptors. Inline identifiers remain JSON scalars;
sealed values retain their `type` discriminator. Token counts are nonnegative integers.

Regenerate them after changing a serialized result:

```text
./gradlew :benchmark:run --args="schemas --out docs/schemas"
```

`ApiSchemasTest` rejects drift between the checked-in schemas and the current descriptors.
Descriptor tests cover nullable values, serialized enum names, sealed discriminators, token-count
constraints and conflicting definition names. Integration validation also checks emitted JSON with
an independent Draft 2020-12 validator.

| Schema | Result |
|---|---|
| `analysis.schema.json` | Task analysis, rankings, scope and context-size estimates |
| `context.schema.json` | Budgeted source fragments and rendered Markdown |
| `doctor.schema.json` | Environment availability checks |
| `graph.schema.json` | Graph nodes, relationships and truncation |
| `impact.schema.json` | Impact traversal |
| `info.schema.json` | Index information |
| `node-resolution.schema.json` | Node argument candidates, including ambiguity results |
| `related.schema.json` | Related files, symbols and evidence |
| `scan.schema.json` | Scan and update results |
| `structure.schema.json` | The structure report of `info --structure` |
| `symbol.schema.json` | Symbol lookup results |
| `localization.schema.json` | Localization methods, gates and paired comparisons |
| `context-benchmark.schema.json` | Context coverage, precision, budgets and paired comparisons |

A result that is read from an index (analysis, context, graph, impact, info, node resolution, related, scan and
symbol) has the field `indexLevel` right after `schemaVersion`: the level of that index, `resolved` or `exact`. In `info`
it is `null` while there is no index.

The schemas describe JSON structure. Source-range validity, evidence quality, index freshness and
budget accounting are checked by the implementation and its behavioral tests.
