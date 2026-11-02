package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.spi.graph.GraphStore
import io.github.krosstack.kscope.store.Builders
import io.github.krosstack.kscope.store.SqliteGraphStore

internal object EdgeFixtures {
    val resolvedCall = Builders.edge("a", Resolutions.resolved("b"))
    val probableCall = Builders.edge("a", Resolutions.resolved("c", Certainty.PROBABLE)).copy(subKind = "kotlin.invoke")
    val unresolvedCall = Builders.edge("a", Resolutions.unresolved("ghost"))
    val unanalysedImport = Builders.edge(
        "a",
        Resolutions.notAnalysed("jni"),
        kind = RelationKind.IMPORTS,
        evidence = EvidenceClass.TEXTUAL,
    )
    val extendsEdge = Builders.edge("b", Resolutions.resolved("c"), kind = RelationKind.EXTENDS)
    val all = listOf(resolvedCall, probableCall, unresolvedCall, unanalysedImport, extendsEdge)

    fun withEdges(check: (GraphStore) -> Unit) = SqliteGraphStore.inMemory().use { store ->
        store.replace(Builders.edgeBatch("unit", all.reversed()))
        check(store)
    }
}
