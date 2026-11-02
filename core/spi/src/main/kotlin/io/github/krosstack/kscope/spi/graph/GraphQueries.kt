package io.github.krosstack.kscope.spi.graph

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.Variant
import kotlinx.serialization.Serializable

public data class WorkspaceStructure(
    public val repositories: List<Repository>,
    public val buildRoots: List<BuildRoot>,
    public val modules: List<Module>,
    public val targets: List<Target>,
    public val variants: List<Variant>,
    public val sourceSets: List<SourceSet>,
)

/** Every non-null criterion must match. [artifact] selects the symbols declared in that artifact. */
public data class SymbolQuery(
    public val name: String? = null,
    public val qualifiedName: String? = null,
    public val kindId: String? = null,
    public val category: SymbolCategory? = null,
    public val artifact: ArtifactRef? = null,
    public val container: SymbolId? = null,
    public val flag: SymbolFlag? = null,
    public val limit: Int = 1000,
)

/** An empty [kinds] set matches every kind. [resolved] distinguishes resolved edges from the rest. */
public data class RelationshipQuery(
    public val kinds: Set<RelationKind> = emptySet(),
    public val subKind: String? = null,
    public val evidenceClass: EvidenceClass? = null,
    public val resolved: Boolean? = null,
    public val limit: Int = 100_000,
)

/** [state] is one of `exact`, `probable`, `heuristic`, `unresolved` and `not-analysed`. */
@Serializable
public data class CoverageCell(
    public val kind: RelationKind,
    public val evidenceClass: EvidenceClass,
    public val state: String,
    public val count: Long,
)

/** [metricsByName] counts the metrics per name, [searchDocuments] the documents of the search index per scope. */
@Serializable
public data class CoverageReport(
    public val cells: List<CoverageCell>,
    public val artifactsByType: Map<String, Long>,
    public val symbolsByKind: Map<String, Long>,
    public val metricsByName: Map<String, Long> = emptyMap(),
    public val searchDocuments: Map<String, Long> = emptyMap(),
)
