package io.github.krosstack.kscope.context.selection.generated

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import kotlin.random.Random

internal class RelationshipGenerator(private val random: Random) {
    fun calls(files: List<GeneratedFile>): List<Relationship> {
        val nodes = files.flatMap { it.symbols }.map { NodeRef.OfSymbol(it.id) }
        if (nodes.size < 2) return emptyList()
        return (0 until random.nextInt(MAX_CALLS + 1)).map {
            val target = random.pick(nodes)
            relationship(random.pick(nodes), Resolution.Resolved(target, Certainty.EXACT))
        }
    }

    fun unresolved(files: List<GeneratedFile>): List<Relationship> {
        val counts = when (random.nextInt(PROFILES)) {
            0 -> files.map { 0 }
            1 -> files.map { random.nextInt(SMALL_COUNT) }
            else -> straddlingCounts(files.size)
        }
        return files.zip(counts).flatMap { (file, count) ->
            List(count) { relationship(anchor(file), Resolution.Unresolved("missing", ReasonCode("test.missing"))) }
        }
    }

    private fun anchor(file: GeneratedFile): NodeRef =
        file.symbols.firstOrNull()?.let { NodeRef.OfSymbol(it.id) } ?: NodeRef.OfArtifact(file.artifact.ref)

    private fun straddlingCounts(files: Int): List<Int> {
        val order = STRADDLING.shuffled(random)
        return List(files) { order[it % order.size] }
    }

    private fun relationship(from: NodeRef, resolution: Resolution) = Relationship(
        from = from,
        kind = RelationKind.CALLS,
        subKind = null,
        resolution = resolution,
        evidenceClass = EvidenceClass.COMPILER,
        site = null,
        scope = Scope(),
    )

    private companion object {
        const val MAX_CALLS = 3
        const val PROFILES = 3
        const val SMALL_COUNT = 4
        val STRADDLING = listOf(9, 1, 80, 9, 1, 3, 90, 9, 1, 0)
    }
}
