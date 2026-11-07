package io.github.krosstack.kscope.diagram

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

internal class DiagramFacts(private val order: Int) {
    private val repos = listOf("alpha", "beta").map { Repository(RepositoryId(it), it, null, null) }
    private val roots = repos.map {
        BuildRoot(
            BuildRootId("${it.id.value}//"),
            it.id,
            RepoPath(""),
            BuildSystem.GRADLE,
        )
    }
    private val modules = (0 until 10).map { index ->
        val root = roots[index / 5]
        val path = ":module$index"
        Module(
            ModuleId("${root.id.value}$path"), root.id, path, RepoPath("module$index"), emptyList(), emptySet(),
            null, EvidenceClass.BUILD_MODEL, Certainty.EXACT,
        )
    }
    private val links: List<Relationship> = listOf(
        link(0, 1), link(1, 0), link(0, 1, DrawnState.HEURISTIC, evidence = EvidenceClass.CONVENTION),
        link(1, 2, DrawnState.PROBABLE), link(2, 3, DrawnState.HEURISTIC),
        link(3, 4, DrawnState.UNRESOLVED), link(4, 5, DrawnState.NOT_ANALYSED),
        link(0, 5, subKind = "maven-coordinates"),
        link(1, 6, subKind = "jvm-library", evidence = EvidenceClass.CONTENT),
        link(2, 7, DrawnState.PROBABLE, "native-library"),
        link(3, 8, DrawnState.HEURISTIC, "web-bundle"),
        link(4, 9, subKind = "generator-input"),
        link(5, 0, DrawnState.PROBABLE, "openapi-route").copy(
            kind = RelationKind.BINDS,
            from = NodeRef.OfSymbol(SymbolId("kscope gradle ${modules[5].id.value} . operation.")),
        ),
        link(6, 1, DrawnState.HEURISTIC, "native-library").copy(
            kind = RelationKind.BINDS,
            from = NodeRef.OfArtifact(ArtifactRef(repos[1].id, RepoPath("native"))),
        ),
        link(5, 0, DrawnState.UNRESOLVED, "jvm-library"),
        link(6, 1, DrawnState.NOT_ANALYSED, "native-library"),
        link(7, 2, DrawnState.UNRESOLVED, "web-bundle"),
    )

    val facts: List<Relationship> get() = reorder(links)

    fun structure(): WorkspaceStructure = WorkspaceStructure(
        reorder(repos),
        reorder(roots),
        reorder(modules),
        emptyList(),
        emptyList(),
        emptyList(),
    )

    private fun link(
        from: Int,
        to: Int,
        state: DrawnState = DrawnState.EXACT,
        subKind: String? = null,
        evidence: EvidenceClass = EvidenceClass.BUILD_MODEL,
    ): Relationship {
        val resolution = when (state) {
            DrawnState.EXACT, DrawnState.PROBABLE, DrawnState.HEURISTIC ->
                Resolution.Resolved(NodeRef.OfModule(modules[to].id), Certainty.valueOf(state.name))

            DrawnState.UNRESOLVED -> Resolution.Unresolved("missing", ReasonCode("artifact.missing"))

            DrawnState.NOT_ANALYSED -> Resolution.NotAnalysed("unread", ReasonCode("artifact.not-analysed"))
        }
        return Relationship(
            NodeRef.OfModule(modules[from].id),
            RelationKind.DEPENDS_ON,
            subKind,
            resolution,
            evidence,
            null,
            Scope(),
        )
    }

    private fun <T> reorder(items: List<T>): List<T> = when (order) {
        0 -> items
        1 -> items.reversed()
        else -> items.filterIndexed { index, _ -> index % 2 == 1 } + items.filterIndexed { index, _ -> index % 2 == 0 }
    }
}
