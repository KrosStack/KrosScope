package io.github.krosstack.kscope.context.selection.generated

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextRole
import kotlin.random.Random

internal data class CandidateMix(
    val filePercent: Int,
    val symbolPercent: Int,
    val anyValuePercent: Int,
    val fixedRelevance: Double? = null,
)

internal fun candidateMixOf(profile: CaseProfile, random: Random): CandidateMix = when (profile) {
    CaseProfile.ORDINARY, CaseProfile.TIES -> CandidateMix(FILE_PERCENT, SYMBOL_PERCENT, ANY_VALUE_PERCENT)
    CaseProfile.DENSE -> CandidateMix(ALL_PERCENT, MANY_PERCENT, ALL_PERCENT)
    CaseProfile.TWINS -> CandidateMix(ALL_PERCENT, ALL_PERCENT, NO_PERCENT, random.pick(TWIN_RELEVANCES))
}

internal class CandidateGenerator(private val random: Random, private val mix: CandidateMix) {
    fun generate(files: List<GeneratedFile>, surfaces: List<GeneratedSurface>): List<ContextCandidate> {
        val artifacts = files.filter { random.chance(mix.filePercent) }
            .map { candidate(NodeRef.OfArtifact(it.artifact.ref), ordinaryRole()) }
        val symbols = files.flatMap { it.symbols }.filter { random.chance(mix.symbolPercent) }
            .map { candidate(NodeRef.OfSymbol(it.id), ordinaryRole()) }
        val generated = surfaces.filter { random.chance(SURFACE_PERCENT) }
            .map { candidate(NodeRef.OfSymbol(it.symbol.id), surfaceRole()) }
        val all = (artifacts + symbols + generated).ifEmpty {
            listOf(candidate(NodeRef.OfArtifact(files.first().artifact.ref), ContextRole.READ))
        }
        return all + all.filter { random.chance(DUPLICATE_PERCENT) }
    }

    private fun candidate(node: NodeRef, role: ContextRole) = ContextCandidate(node, relevance(), role)

    private fun ordinaryRole(): ContextRole = if (random.chance(EDIT_PERCENT)) ContextRole.EDIT else ContextRole.READ

    private fun surfaceRole(): ContextRole =
        if (random.chance(BOUNDARY_PERCENT)) ContextRole.BOUNDARY else ContextRole.READ

    private fun relevance(): Double = mix.fixedRelevance
        ?: if (random.chance(mix.anyValuePercent)) random.nextDouble(MIN_RELEVANCE, 1.0) else random.pick(RELEVANCES)

    private companion object {
        const val SURFACE_PERCENT = 70
        const val DUPLICATE_PERCENT = 20
        const val EDIT_PERCENT = 15
        const val BOUNDARY_PERCENT = 50
        const val MIN_RELEVANCE = 0.01
        val RELEVANCES = listOf(0.1, 1.0 / 3, 1.0 / 7, 0.1, 0.5, 1.0, 1.0 / 3, 0.25)
    }
}

private const val FILE_PERCENT = 80
private const val SYMBOL_PERCENT = 45
private const val ANY_VALUE_PERCENT = 10
private const val ALL_PERCENT = 100
private const val MANY_PERCENT = 90
private const val NO_PERCENT = 0
private val TWIN_RELEVANCES = listOf(1.0, 0.5, 1.0 / 3, 1.0 / 7, 0.1)
