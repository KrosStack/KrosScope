package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.cli.text.format.NodeLabels
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolEcosystem
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.retrieval.ImpactResult
import io.github.krosstack.kscope.service.UnitNeighbourhood

/** One link of a unit to another node: how they are related and what the other node is. */
internal data class UnitLink(val relation: String, val node: String)

/** Another declaration that the same name could mean, with what to pass to pick it. */
internal data class UnitAlternative(val symbol: String, val kind: String, val where: String)

/** What `analyze` prints for a name or a file: the declaration, its place, its links, its tests and its impact. */
internal data class UnitCard(
    val subject: String,
    val kind: String,
    val location: String,
    val signature: String?,
    val module: String?,
    val sourceSet: String?,
    val declares: String?,
    val dependsOn: List<UnitLink>,
    val referredBy: List<UnitLink>,
    val tests: List<String>,
    val impact: String,
    val alternatives: List<UnitAlternative>,
    val pick: String,
)

/** The answers of the service calls that a unit card is made from. */
internal class UnitFacts(
    val node: String,
    val neighbourhood: UnitNeighbourhood,
    val impact: ImpactResult,
    val alternatives: List<Symbol>,
    val locations: WorkspaceLocations,
)

/** Builds a [UnitCard] from the neighbourhood, the impact and the alternatives of one node. */
internal object UnitCards {
    const val FILE_KIND = "file"
    private const val SIGNATURE_WIDTH = 100
    private const val DECLARED_SHOWN = 5
    private val ORDER = listOf(
        RelationKind.EXTENDS, RelationKind.IMPLEMENTS, RelationKind.OVERRIDES, RelationKind.ACTUALIZES,
        RelationKind.TYPE_USES, RelationKind.CALLS, RelationKind.REFERENCES, RelationKind.IMPORTS,
        RelationKind.BINDS, RelationKind.GENERATES, RelationKind.DEPENDS_ON, RelationKind.TESTS,
    )

    fun of(facts: UnitFacts): UnitCard {
        val neighbourhood = facts.neighbourhood
        val symbol = neighbourhood.symbol
        val placed = symbol ?: neighbourhood.declared.firstOrNull()
        val (module, sourceSet) = placement(placed)
        return UnitCard(
            subject = symbol?.let(::name) ?: facts.node,
            kind = symbol?.kind?.id ?: FILE_KIND,
            location = symbol?.let { location(it, facts.locations) } ?: facts.node,
            signature = symbol?.signature?.let { Format.clip(it, SIGNATURE_WIDTH) },
            module = module,
            sourceSet = sourceSet,
            declares = if (symbol == null) declares(neighbourhood.declared) else null,
            dependsOn = links(neighbourhood.dependsOn) { NodeLabels.target(it, facts.locations) },
            referredBy = links(neighbourhood.referredBy) { NodeLabels.of(it.from, facts.locations) },
            tests = facts.impact.tests.map(facts.locations::pathOf),
            impact = ImpactText.summary(facts.impact),
            alternatives = facts.alternatives.map { alternative(it, facts.locations) },
            pick = pick(facts, symbol),
        )
    }

    private fun alternative(symbol: Symbol, locations: WorkspaceLocations): UnitAlternative =
        UnitAlternative(NodeLabels.symbol(symbol.id), symbol.kind.id, location(symbol, locations))

    fun name(symbol: Symbol): String = symbol.qualifiedName ?: symbol.name

    private fun location(symbol: Symbol, locations: WorkspaceLocations): String =
        symbol.declaration?.let { "${locations.pathOf(it.artifact)}:${it.range.startLine}" } ?: "no source"

    private fun pick(facts: UnitFacts, symbol: Symbol?): String = when {
        symbol == null -> facts.node
        facts.alternatives.isEmpty() -> name(symbol)
        else -> location(symbol, facts.locations)
    }

    private fun declares(declared: List<Symbol>): String? =
        declared.sortedBy { it.declaration?.range?.startLine ?: Int.MAX_VALUE }.map { "${it.kind.id} ${name(it)}" }
            .takeIf { it.isNotEmpty() }?.let { Format.listing(it, DECLARED_SHOWN) }

    private fun placement(symbol: Symbol?): Pair<String?, String?> {
        val identity = (symbol?.id?.identity() as? ParsedSymbolId.Valid)?.identity
        if (identity == null || identity.ecosystem != SymbolEcosystem.GRADLE) return null to null
        val module = identity.unit.substringAfter("//", identity.unit)
        val sourceSet = identity.sourceSet.takeIf { it != NOT_APPLICABLE }?.let { "$module@$it" }
        return module to sourceSet
    }

    private fun links(relationships: List<Relationship>, label: (Relationship) -> String): List<UnitLink> =
        relationships.groupBy({ label(it) }, { it.kind }).map { (node, kinds) ->
            val ordered = kinds.distinct().sortedBy(::rank)
            rank(ordered.first()) to UnitLink(relations(ordered), node)
        }.sortedWith(compareBy({ it.first }, { it.second.node })).map { it.second }

    private fun relations(kinds: List<RelationKind>): String = kinds.joinToString(", ") { kind ->
        kind.name.lowercase().replace('_', '-')
    }

    private fun rank(kind: RelationKind): Int = ORDER.indexOf(kind).let { if (it < 0) ORDER.size else it }

    private const val NOT_APPLICABLE = "."
}
