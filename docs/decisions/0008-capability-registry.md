# ADR 0008: Capability registry as the only extension mechanism

Status: Accepted · Date: 2026-10-05

## Problem

Support for new artifacts (resources, manifests, AIDL, schemas, JNI contracts) must not require edits to
unrelated logic. Without a single extension path, file-extension checks and per-language branches spread
through the engine, and "supported" comes to mean "the extension is recognised".

## Options considered

1. **Extension conditionals in core code.**
2. **A general plugin framework with dynamic loading.**
3. **Closed enums for artifact types, symbol kinds and relationship kinds.**
4. **Capability registration into an immutable registry, with open namespaced kinds and a closed
   relationship vocabulary.** Chosen.

## Evidence

- Existing tools layer a generic core with per-language producers. Sourcegraph applies precise navigation
  over syntactic and search-based fallbacks with strict precedence
  ([Sourcegraph docs](https://sourcegraph.com/docs/code-navigation)). Glean keeps language-specific facts
  beneath a language-neutral layer
  ([Meta Engineering](https://engineering.fb.com/2024/12/19/developer-tools/glean-open-source-code-indexing/)).
- A recognised file extension is not complete semantic coverage, a warning stated by
  [roam-code](https://github.com/Cranot/roam-code). This argues for confining file-name checks to detection.
- Research schemas keep the edge vocabulary small: CodexGraph uses five edge types
  ([CodexGraph](https://arxiv.org/html/2408.03910v1)) and the Code Graph Model seven node and five edge types
  ([CGM](https://arxiv.org/pdf/2505.16901)).
- Reasoning: ranking and impact logic assign meaning to relationship kinds, so those must be closed. Artifact
  and symbol kinds grow with each ecosystem, so they must be open. No third-party in-process extension is
  needed yet; out-of-process analyzers already cover external producers through the versioned analyzer
  protocol.

## Decision

- The only extension mechanism is capability registration: `ArtifactDetector`, `BuildModelProvider`,
  `FactProducer`, `SurfaceSynthesizer` and `BoundaryLinker`, with `SignalProvider`, `ContextRenderer` and
  `TokenEstimator` added by the phases that need them.
- Capabilities are collected in an immutable `CapabilityRegistry`, ordered deterministically by id and
  assembled by the `service` module.
- File names and extensions are inspected only inside `ArtifactDetector` implementations.
- `ArtifactType`, `SymbolKind` and `SourceRootKind` are open namespaced values (for example `kotlin.class`)
  grouped into small closed categories that generic logic may depend on.
- `RelationKind` is closed. Producers may add a namespaced sub-kind.

## Consequences

- Adding support for an artifact means a detector, a producer or linker, declared capabilities and corpus
  tests. No unrelated module changes.
- Adding a relationship kind is a deliberate core change.
- Generic code must handle unknown kinds through their category.
- Registry assembly in `service` is the single place where the set of supported artifacts is visible.

## Reversal cost

Low to medium. The contracts are small interfaces, and dynamic loading can later populate the same registry
without changing producers. Moving to closed kinds, or widening `RelationKind` into an open set, would
require revisiting ranking and impact logic.
