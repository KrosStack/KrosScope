package io.github.krosstack.kscope.benchmark.score

import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.graph.key
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin

private const val KOTLIN_SOURCE = "kotlin.source"
private const val JAVA_SOURCE = "java.source"

/** Uses across the language boundary: a relationship from a file in [fromSource] to a symbol [targetFilter] accepts. */
internal class InteropScorer(
    private val name: String,
    private val fromSource: String,
    private val relationKinds: Set<RelationKind>,
    private val targetFilter: (Symbol) -> Boolean,
) {
    fun score(index: GraphIndex, resolver: GoldAddressResolver, gold: List<GoldRelationship>): ClassScore {
        val mapped = MappedClass.of(gold, resolver)
        val expected = mapped.expectedItems { "${it.gold.from.file}|${it.gold.line}|${it.to.key()}" }
        val matching = Matching.of(expected + mapped.unmappedMisses(), produced(index))
        return scoreOf(name, gold, mapped, matching)
    }

    private fun produced(index: GraphIndex): List<Item> = index.relationships
        .filter { it.kind in relationKinds }
        .mapNotNull { relationship -> itemOf(index, relationship) }

    private fun itemOf(index: GraphIndex, relationship: Relationship): Item? {
        val site = relationship.site
        val target = index.resolvedTarget(relationship) as? NodeRef.OfSymbol
        if (site == null || target == null || !isCandidate(index, site.artifact.path.value, target)) return null
        val line = site.range.startLine
        val path = site.artifact.path.value
        return Item("$path|$line|${target.key()}", "${relationship.kind} $path:$line", evidenceOf(relationship))
    }

    private fun isCandidate(index: GraphIndex, path: String, target: NodeRef.OfSymbol): Boolean {
        val symbol = index.symbolsById[target.id]
        return index.artifactsByPath[path]?.type?.id == fromSource && symbol != null && targetFilter(symbol)
    }
}

internal fun evidenceOf(relationship: Relationship): String =
    relationship.evidenceClass.name.lowercase().replace('_', '-')

private val callAndInheritance = setOf(
    RelationKind.CALLS,
    RelationKind.EXTENDS,
    RelationKind.IMPLEMENTS,
    RelationKind.OVERRIDES,
)

private val kotlinMembers = setOf("kotlin.function", "kotlin.property", "kotlin.enum-entry")

/**
 * Every use in Kotlin of a Java declaration that the workspace writes: supertype, constructor, override, static,
 * instance and SAM use. The Java surfaces that generators make are other classes of gold.
 */
internal val kotlinCallsJava = InteropScorer(
    name = "kotlin_calls_java",
    fromSource = KOTLIN_SOURCE,
    relationKinds = callAndInheritance,
    targetFilter = { it.kind.id.startsWith("java.") && it.origin is SymbolOrigin.Source },
)

/** Every use in Java of a Kotlin member: accessor, constant, object, facade, static and enum constant use. */
internal val javaCallsKotlin = InteropScorer(
    name = "java_calls_kotlin",
    fromSource = JAVA_SOURCE,
    relationKinds = setOf(RelationKind.CALLS, RelationKind.REFERENCES),
    targetFilter = { it.kind.id in kotlinMembers },
)
