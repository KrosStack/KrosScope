package io.github.krosstack.kscope.analyzer.android.aidl.linking

import io.github.krosstack.kscope.analyzer.android.aidl.STRUCTURED_ATTRIBUTE
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

internal const val PARCELABLE_CLASS_NOT_FOUND = "aidl.parcelable-class-not-found"

private val classKinds = setOf("kotlin.class", "java.class")

/**
 * Joins an unstructured `parcelable Foo;` to the class of the same qualified name, which is where the parcelable is
 * implemented. The name is all the AIDL file says, so the evidence is textual: the match is exact for one class and
 * probable for several, as the variants of a module may each have one. Without a class the relationship is
 * unresolved, because the class may live in a library. A structured parcelable has a generated class and no
 * relationship here.
 */
internal class ParcelableLinks(private val graph: GraphReader, private val contract: Symbol) {
    fun relationships(): List<Relationship> {
        val qualifiedName = contract.qualifiedName
        val isUnstructured = contract.attributes[STRUCTURED_ATTRIBUTE] != "true"
        return if (qualifiedName == null || !isUnstructured) emptyList() else bindings(qualifiedName)
    }

    private fun bindings(qualifiedName: String): List<Relationship> {
        val query = SymbolQuery(qualifiedName = qualifiedName, category = SymbolCategory.TYPE, limit = ALL)
        val classes = graph.symbols(query).filter { it.origin is SymbolOrigin.Source && it.kind.id in classKinds }
        if (classes.isEmpty()) return listOf(unresolved(qualifiedName))
        val certainty = if (classes.size == 1) Certainty.EXACT else Certainty.PROBABLE
        return classes.map { bindingOf(bindingTo(it, certainty), EvidenceClass.TEXTUAL) }
    }

    private fun bindingTo(parcelableClass: Symbol, certainty: Certainty) =
        Binding(contract, NodeRef.OfSymbol(parcelableClass.id), PARCELABLE_CLASS, certainty, contract.scope)

    private fun unresolved(qualifiedName: String) = Relationship(
        from = NodeRef.OfSymbol(contract.id),
        kind = RelationKind.BINDS,
        subKind = PARCELABLE_CLASS,
        resolution = Resolution.Unresolved(qualifiedName, ReasonCode(PARCELABLE_CLASS_NOT_FOUND)),
        evidenceClass = EvidenceClass.TEXTUAL,
        site = contract.nameLocation,
        scope = contract.scope,
    )
}
