# JSON result schemas

These Draft 2020-12 schemas describe version 3 of analysis JSON, version 2 of context-benchmark JSON
and version 1 of the other CLI JSON results and benchmark reports.
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
| `diagram.schema.json` | Module, component and symbol diagrams |
| `doctor.schema.json` | Environment availability checks |
| `graph.schema.json` | Graph nodes, relationships and truncation |
| `impact.schema.json` | Impact traversal |
| `info.schema.json` | Index information |
| `node-resolution.schema.json` | Node argument candidates, including ambiguity results |
| `related.schema.json` | Related files, symbols and evidence |
| `scan.schema.json` | Scan and update results |
| `status.schema.json` | The result of `kscope status`: the index, its freshness and the server |
| `structure.schema.json` | The structure report of `info --structure` |
| `symbol.schema.json` | Symbol lookup results |
| `localization.schema.json` | Localization methods, gates and paired comparisons |
| `context-benchmark.schema.json` | Context coverage, precision, budgets, actual method status and optional pipeline identity |

A result that is read from an index (analysis, context, graph, impact, info, node resolution, related, scan, status and
symbol) has the field `indexLevel` right after `schemaVersion`: the level of that index, `resolved` or `exact`. In `info`
and `status` it is `null` while there is no index.

A query result (analysis, context, graph, impact, node resolution, related and symbol) that was read from the index
of a parent folder also has the optional field `indexLocation` right after `indexLevel`: the `.kscope` directory that
answered. It is absent when the workspace was given or has its own index. `info`, `scan` and `status` always have
`indexLocation`.

The analysis, context and symbol results also have optional fields after `indexLocation`, each present only when it
applies:

- `narrowedTo`: the name of the repository that the search was limited to, as `info` lists it: its directory below
  the workspace root, or, for a repository at the root, its `name`.
- `outsideNarrowing` (symbol): the name of the repository in which the declaration lies, when an id, qualified name
  or `path:line` names a declaration outside `narrowedTo`.
- `widenedFrom` (symbol): the name of the repository that had no match, when the search was widened to the whole
  workspace. `narrowedTo` is then absent.
- `incomplete`: `true` when the search stopped at its cap of 50,000 candidates before the limits were filled.

Analysis, context and impact results also have the optional object `indexFreshness` after these fields when the
quick check finds something behind, or when the repository of the current folder could not be checked. `info`
always has `indexFreshness`. Its schema allows `null`, which the command does not print.

A `diagram` result has `indexLevel` in the same place.

The schemas describe JSON structure. Source-range validity, evidence quality, index freshness and
budget accounting are checked by the implementation and its behavioral tests.

Context-benchmark v2 adds nullable method `semantic` status and result `pipeline` identity.
Version 1 records still decode with these fields absent; they provide no evidence of semantic use.
Product context JSON remains version 1 and contains the counted context package without semantic status.
Service callers can obtain that status through `contextResult`.
