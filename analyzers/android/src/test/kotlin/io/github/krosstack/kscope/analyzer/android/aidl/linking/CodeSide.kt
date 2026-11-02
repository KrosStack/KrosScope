package io.github.krosstack.kscope.analyzer.android.aidl.linking

import io.github.krosstack.kscope.analyzer.android.aidl.AidlModel
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.Visibility

/** Where the code side of a scenario lives: the symbols of Kotlin and the compiler edges between them. */
internal class CodeSide(private val module: String = ":svc", private val sourceSet: String = "main") {
    val facts = mutableListOf<Fact>()
    private val scope = Scope(sourceSet = AidlModel.sourceSetId(module, sourceSet))

    private fun idOf(descriptor: SymbolDescriptor) =
        SymbolIdentity.workspace(AidlModel.moduleId(module), sourceSet, descriptor).toSymbolId()

    private fun descriptorOf(id: SymbolId): SymbolDescriptor =
        checkNotNull((id.identity() as? ParsedSymbolId.Valid)?.identity).descriptor

    private fun descriptorOf(qualifiedName: String): SymbolDescriptor {
        val parts = qualifiedName.split('.')
        val packageDescriptor = parts.dropLast(1).fold(SymbolDescriptor()) { d, part -> d.namespace(part) }
        return packageDescriptor.type(parts.last())
    }

    /** A class or, with [owner], a member of one; members are functions unless [kind] says otherwise. */
    fun symbol(qualifiedName: String, kind: String = "kotlin.class", owner: Symbol? = null): Symbol {
        val name = qualifiedName.substringAfterLast('.')
        val descriptor = owner?.let { descriptorOf(it.id).method(name, "") } ?: descriptorOf(qualifiedName)
        val symbol = Symbol(
            id = idOf(descriptor),
            kind = SymbolKind(kind, if (owner == null) SymbolCategory.TYPE else SymbolCategory.CALLABLE),
            name = name,
            qualifiedName = qualifiedName,
            signature = null,
            container = owner?.id,
            visibility = Visibility.PUBLIC,
            flags = emptySet(),
            origin = SymbolOrigin.Source,
            declaration = null,
            nameLocation = SourceLocation(ArtifactRef(AidlModel.repository, RepoPath("x.kt")), TextRange(1, 1, 1, 2)),
            documentation = null,
            scope = scope,
            evidenceClass = EvidenceClass.COMPILER,
            certainty = Certainty.EXACT,
        )
        facts += Fact.OfSymbol(symbol)
        return symbol
    }

    fun edge(edge: Edge) {
        facts += Fact.OfRelationship(
            Relationship(
                from = NodeRef.OfSymbol(edge.from.id),
                kind = edge.kind,
                subKind = edge.subKind,
                resolution = Resolution.Resolved(NodeRef.OfSymbol(edge.to.id), edge.certainty),
                evidenceClass = EvidenceClass.COMPILER,
                site = SourceLocation(ArtifactRef(AidlModel.repository, RepoPath("x.kt")), edge.at),
                scope = scope,
            ),
        )
    }
}

/** A compiler relationship from [from] to [to] at [at]. */
internal data class Edge(
    val from: Symbol,
    val kind: RelationKind,
    val to: Symbol,
    val at: TextRange,
    val subKind: String? = null,
    val certainty: Certainty = Certainty.EXACT,
)
