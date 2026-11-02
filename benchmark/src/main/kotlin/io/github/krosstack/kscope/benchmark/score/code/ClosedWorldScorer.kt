package io.github.krosstack.kscope.benchmark.score.code

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.gold.GoldClosedWorldFile
import io.github.krosstack.kscope.benchmark.graph.AddressResolution
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.graph.UnmappedReason
import io.github.krosstack.kscope.benchmark.graph.key
import io.github.krosstack.kscope.benchmark.score.Item
import io.github.krosstack.kscope.benchmark.score.Matching
import io.github.krosstack.kscope.benchmark.score.ProducedEdges
import io.github.krosstack.kscope.benchmark.score.Score
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Platform
import kotlinx.serialization.Serializable

/** How many of the gold edges of one kind the graph has. */
@Serializable
public data class KindCount(public val kind: String, public val expected: Int, public val matched: Int)

/**
 * The edges of one closed-world file. [score] covers the edges that can be produced now; [deferred] lists the gold
 * edges to generated surfaces, which no producer makes yet, and [unmapped] the gold edges whose target address could
 * not be mapped for another reason, which count as misses. [extrasByKind] counts the graph edges gold does not list.
 */
@Serializable
public data class FileEdgeScore(
    public val file: String,
    public val module: String,
    public val sourceSet: String,
    public val androidDependent: Boolean,
    public val score: Score,
    public val deferred: List<String>,
    public val unmapped: List<String>,
    public val approximateLines: Int,
    public val byKind: List<KindCount>,
    public val extrasByKind: Map<String, Int>,
)

private class GoldEdgeItem(
    val line: Int,
    val column: Int,
    val kind: String,
    val text: String,
    val target: GoldAddress,
) {
    val label: String get() = "$kind $text -> ${target.describe()} @$line:$column"
}

private const val IMPORT = "import"
private const val CONSTRUCTOR_CALL = "constructor_call"

/**
 * Compares the outgoing edges of a file with its closed-world gold: every reference, call and inheritance edge, and
 * every import, to a workspace symbol. A gold edge matches a graph edge on the source line and the target; targets in
 * libraries are not part of gold and are left out of the graph side too.
 */
internal class ClosedWorldScorer(private val index: GraphIndex, private val resolver: GoldAddressResolver) {
    private val produced = ProducedEdges(index)

    fun score(gold: GoldClosedWorldFile): FileEdgeScore {
        val mapped = goldEdges(gold).map { it to resolver.resolve(it.target) }
        val (deferred, comparable) = mapped.partition { (_, resolution) -> isDeferred(resolution) }
        val expected = comparable.map { (edge, resolution) -> expectedItem(edge, resolution) }
        val matching = Matching.of(expected, produced.of(gold.file))
        return FileEdgeScore(
            file = gold.file,
            module = gold.module,
            sourceSet = gold.sourceSet,
            androidDependent = isAndroidDependent(gold.file),
            score = matching.score(),
            deferred = deferred.map { (edge, _) -> edge.label }.sorted(),
            unmapped = comparable.mapNotNull { (_, resolution) -> unmappedLabel(resolution) }.sorted(),
            approximateLines = comparable.count { (_, resolution) -> isApproximate(resolution) },
            byKind = kindCounts(matching),
            extrasByKind = matching.extra.groupingBy { it.group }.eachCount().toSortedMap(),
        )
    }

    private fun goldEdges(gold: GoldClosedWorldFile): List<GoldEdgeItem> =
        gold.edges.map { GoldEdgeItem(it.line, it.column, it.kind, it.text, it.target) } +
            gold.imports.map { GoldEdgeItem(it.line, 0, IMPORT, it.importedName, it.target) }

    private fun isDeferred(resolution: AddressResolution) =
        resolution is AddressResolution.Unmapped && resolution.reason == UnmappedReason.GENERATED_SURFACE_NOT_PRODUCED

    private fun unmappedLabel(resolution: AddressResolution): String? =
        (resolution as? AddressResolution.Unmapped)?.describe()

    private fun isApproximate(resolution: AddressResolution) =
        resolution is AddressResolution.Found && resolution.approximateLine

    private fun expectedItem(edge: GoldEdgeItem, resolution: AddressResolution): Item = when (resolution) {
        is AddressResolution.Found -> Item("${edge.line}|${targetOf(edge, resolution).key()}", edge.label, edge.kind)

        is AddressResolution.Unmapped ->
            Item("unmapped|${edge.label}", "${edge.label} [${resolution.reason.code}]", edge.kind)
    }

    // Gold addresses the primary constructor of a Kotlin class by the class, whose header declares it.
    private fun targetOf(edge: GoldEdgeItem, found: AddressResolution.Found): NodeRef =
        (if (edge.kind == CONSTRUCTOR_CALL) index.primaryConstructorOf(found.node) else null) ?: found.node

    private fun kindCounts(matching: Matching): List<KindCount> {
        val missed = matching.missed.groupingBy { it.group }.eachCount()
        val matched = matching.matched.groupingBy { it.group }.eachCount()
        return (missed.keys + matched.keys).sorted().map { kind ->
            val hit = matched[kind] ?: 0
            KindCount(kind, hit + (missed[kind] ?: 0), hit)
        }
    }

    private fun isAndroidDependent(file: String): Boolean {
        val sourceSet = index.artifactsByPath[file]?.let(index::sourceSetOf)
        val platforms = sourceSet?.let(index::targetPlatformsOf).orEmpty()
        return platforms.isNotEmpty() && platforms.all { it == Platform.ANDROID }
    }
}
