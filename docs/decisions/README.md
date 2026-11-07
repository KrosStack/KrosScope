# Architecture Decision Records

An Architecture Decision Record (ADR) captures one significant technical decision: the problem, the options
considered, the evidence behind the choice, the decision itself and what it costs to reverse. The
architecture itself is described in [`../architecture.md`](../architecture.md); ADRs record why it is shaped
that way. Numbers are permanent identifiers. An accepted ADR is not edited to change its decision; a later
ADR supersedes it and both stay in the history. Every ADR uses this template:

```
# ADR NNNN: Title
Status: Accepted · Date: YYYY-MM-DD
## Problem
## Options considered
## Evidence
## Decision
## Consequences
## Reversal cost
```

| Number | Title | Status |
|---|---|---|
| [0001](0001-platform-and-licensing.md) | Platform and licensing | Accepted |
| [0002](0002-semantic-analysis.md) | Semantic analysis of Kotlin and Java | Accepted |
| [0003](0003-build-model-acquisition.md) | Build model acquisition | Accepted |
| [0004](0004-symbol-identity.md) | Symbol identity | Accepted |
| [0005](0005-evidence-and-resolution-states.md) | Evidence and resolution states | Accepted |
| [0006](0006-persistence-and-incrementality.md) | Persistence and incrementality | Accepted |
| [0007](0007-generated-surfaces.md) | Generated API surfaces | Accepted |
| [0008](0008-capability-registry.md) | Capability registry as the only extension mechanism | Accepted |
| [0009](0009-aidl-declaration-parser.md) | Owned AIDL declaration parser | Accepted |
| [0010](0010-sqldelight-table-definition-reader.md) | Owned SQLDelight table definition reader | Accepted |
| [0011](0011-deterministic-retrieval.md) | Deterministic retrieval | Accepted |
| [0012](0012-context-package-and-budget.md) | Context package format and budget algorithm | Accepted |
| [0013](0013-token-estimation.md) | Token estimation | Accepted |
| [0014](0014-api-digests.md) | API digests for incremental analysis | Accepted |
| [0015](0015-resident-service-and-settings.md) | A resident service, scan levels and a settings file per workspace | Accepted, implemented in 0.1.0 except the MCP arguments that narrow one query (decision 5); decision 2 amended by 0017; one item of decision 3 superseded by 0018 |
| [0016](0016-multi-repository-workspace.md) | A workspace of several repositories, linked by Maven coordinates | Accepted, implemented in 0.1.0; its coordinate links have gold classes and rows in the support matrix since [ADR 0020](0020-links-through-built-artifacts.md) |
| [0017](0017-row-order-dropped.md) | Ordering on the query path without stored row order | Accepted |
| [0018](0018-stored-trust-for-exact.md) | Stored trust per workspace for the exact level | Accepted |
| [0019](0019-installers-and-agent-skill.md) | Installers in shell and PowerShell, and a skill for coding agents | Accepted |
| [0020](0020-links-through-built-artifacts.md) | Repositories registered anywhere, linked through what they build for each other | Accepted; registration, JAR files, native library loads, OpenAPI and Wire specs, and web bundles implemented |
| [0021](0021-browser-map.md) | A read-only map in the browser, served by `serve --map` | Accepted |
| [0022](0022-optional-components.md) | Optional components from a pinned manifest | Proposed |
