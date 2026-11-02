package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceLocation
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

    fun fact(fact: Fact): String = when (fact) {
        is Fact.OfSymbol -> "symbol ${fact.value.kind.id} ${id(fact.value.id)} ${at(fact.value.nameLocation)}"

        is Fact.OfSymbolicReference -> with(fact.value) {
            "ref ${node(from)} ${subKind.orEmpty()} -> $key ${at(site)}".replace("  ", " ")
        }

        is Fact.OfRelationship -> with(fact.value) {
            val target = (resolution as? Resolution.Resolved)?.let { node(it.target) } ?: resolution.toString()
            "rel $kind/${subKind.orEmpty()} ${node(from)} -> $target ${at(site)}"
        }

        is Fact.OfDiagnostic -> "diagnostic ${fact.value.severity} ${fact.value.code.value}"

        else -> fact.toString()
    }

    fun lines(facts: List<Fact>): List<String> = facts.map(::fact)
}
