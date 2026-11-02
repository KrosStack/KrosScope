package io.github.krosstack.kscope.benchmark.score.code

import io.github.krosstack.kscope.benchmark.gold.GoldClosedWorldFile
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.Item
import io.github.krosstack.kscope.benchmark.score.Matching
import io.github.krosstack.kscope.benchmark.score.Score
import io.github.krosstack.kscope.model.Symbol
import kotlinx.serialization.Serializable

/** The declarations of one closed-world file: those gold lists against the symbols the graph has for the file. */
@Serializable
public data class FileDeclarationScore(public val file: String, public val score: Score)

private val declarationKinds = mapOf(
    "kotlin.class" to "class",
    "kotlin.enum" to "class",
    "kotlin.annotation" to "class",
    "java.class" to "class",
    "java.enum" to "class",
    "java.record" to "class",
    "java.annotation" to "class",
    "kotlin.interface" to "interface",
    "java.interface" to "interface",
    "kotlin.object" to "object",
    "kotlin.enum-entry" to "enum-entry",
    "java.enum-constant" to "enum-entry",
    "kotlin.typealias" to "typealias",
    "kotlin.function" to "function",
    "java.method" to "function",
    "kotlin.property" to "property",
    "java.field" to "property",
    "kotlin.constructor" to "constructor",
    "java.constructor" to "constructor",
)

/**
 * Compares the symbols of a file with the declarations that gold derived from reading its source. A declaration is the
 * same when its kind, qualified name and line agree; the kinds are the language independent ones of gold.
 */
internal class DeclarationScorer(private val index: GraphIndex) {
    fun score(gold: GoldClosedWorldFile): FileDeclarationScore {
        val expected = gold.declarations.map { item(it.kind, it.fqName, it.line) }
        val produced = index.symbolsByPath[gold.file].orEmpty().mapNotNull(::produced)
        return FileDeclarationScore(gold.file, Matching.of(expected, produced).score())
    }

    private fun produced(symbol: Symbol): Item? {
        val kind = declarationKinds[symbol.kind.id] ?: return null
        val line = (symbol.nameLocation ?: symbol.declaration)?.range?.startLine ?: 0
        return item(kind, symbol.qualifiedName ?: symbol.name, line)
    }

    private fun item(kind: String, fqName: String, line: Int) = Item("$kind|$fqName|$line", "$kind $fqName @$line")
}
