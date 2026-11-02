package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolEcosystem
import io.github.krosstack.kscope.model.SymbolId
import kotlin.test.assertTrue

/**
 * Relationships in a short text form that tests can compare: `<from> <kind>[/<subKind>] -> <target>`. A workspace
 * symbol is written `<module>@<source set> <descriptor>`, a library symbol `<ecosystem> <descriptor>`, a file
 * `file:<path>`, an unresolved target `?<hint> (<reason>)` and a probable one has a trailing ` ~probable`. Symbols of
 * the [home] module and source set are written without that prefix.
 */
class Edges(private val home: String = "") {
    fun of(batches: List<FactBatch>): List<String> = batches.relationships().map(::render)

    fun located(batches: List<FactBatch>): List<String> = batches.relationships().map {
        val start = it.site?.range
        "${render(it)} @${start?.startLine}:${start?.startColumn}"
    }

    fun render(relationship: Relationship): String {
        val subKind = relationship.subKind?.let { "/$it" }.orEmpty()
        val kind = relationship.kind.name.lowercase().replace('_', '-') + subKind
        return "${text(relationship.from)} $kind -> ${target(relationship.resolution)}"
    }

    private fun target(resolution: Resolution): String = when (resolution) {
        is Resolution.Resolved ->
            text(resolution.target) + if (resolution.certainty == Certainty.PROBABLE) " ~probable" else ""

        is Resolution.Unresolved -> "?${resolution.hint} (${resolution.reason.value})"

        is Resolution.NotAnalysed -> "!${resolution.hint} (${resolution.reason.value})"
    }

    private fun text(node: NodeRef): String = when (node) {
        is NodeRef.OfSymbol -> text(node.id)
        is NodeRef.OfArtifact -> "file:${node.ref.path.value}"
        is NodeRef.OfModule -> "module:${node.id.value}"
        is NodeRef.OfSourceSet -> "source-set:${node.id.value}"
        is NodeRef.OfExternal -> "external:${node.coordinate}"
    }

    private fun text(id: SymbolId): String = Companion.text(id).removePrefix(home)

    companion object {
        fun text(id: SymbolId): String {
            val identity = (id.identity() as? ParsedSymbolId.Valid)?.identity ?: return id.value
            val descriptor = identity.descriptor.render()
            return if (identity.ecosystem == SymbolEcosystem.GRADLE) {
                "${identity.unit.substringAfter("//:")}@${identity.sourceSet} $descriptor"
            } else {
                "${identity.ecosystem.token} $descriptor"
            }
        }

        fun assertHas(actual: List<String>, vararg expected: String) {
            val missing = expected.filter { it !in actual }
            assertTrue(
                missing.isEmpty(),
                "Missing:\n${missing.joinToString("\n")}\nActual:\n${actual.joinToString("\n")}",
            )
        }

        fun assertLacks(actual: List<String>, vararg unexpected: String) {
            val present = unexpected.filter { it in actual }
            assertTrue(present.isEmpty(), "Unexpected:\n${present.joinToString("\n")}")
        }
    }
}
