package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId

/** Short readable forms of facts, so that tests can state what they expect on one line each. */
internal object Render {
    fun id(id: SymbolId): String {
        val identity = (id.identity() as ParsedSymbolId.Valid).identity
        return "${identity.unit.substringAfter("//")}@${identity.sourceSet}:${identity.descriptor.render()}"
    }

    fun node(node: NodeRef): String = when (node) {
        is NodeRef.OfSymbol -> id(node.id)
        is NodeRef.OfArtifact -> node.ref.path.value.substringAfterLast('/')
        is NodeRef.OfModule -> node.id.value.substringAfter("//")
        is NodeRef.OfSourceSet -> node.id.value.substringAfter("//")
        is NodeRef.OfExternal -> node.coordinate
    }

    fun at(location: SourceLocation?): String =
        location?.range?.let { "${it.startLine}:${it.startColumn}-${it.endLine}:${it.endColumn}" }.orEmpty()

    fun relationship(relationship: Relationship): String = with(relationship) {
        val target = (resolution as? Resolution.Resolved)?.let { node(it.target) } ?: resolution.toString()
        "$kind/${subKind.orEmpty()} ${node(from)} -> $target $evidenceClass"
    }
}

internal fun List<Fact>.symbols(): List<Symbol> = filterIsInstance<Fact.OfSymbol>().map { it.value }

internal fun List<Fact>.relationships(): List<Relationship> = filterIsInstance<Fact.OfRelationship>().map { it.value }
