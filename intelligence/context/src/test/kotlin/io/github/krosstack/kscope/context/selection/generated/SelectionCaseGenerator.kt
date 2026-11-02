package io.github.krosstack.kscope.context.selection.generated

import io.github.krosstack.kscope.context.ContextFixture
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextInput
import kotlin.random.Random

internal class SelectionCaseGenerator(private val seed: Long, private val profile: CaseProfile = caseProfileOf(seed)) {
    private val random = Random(seed)
    private val shape = fileShapeOf(profile)

    fun generate(): GeneratedSelection {
        val plans = FilePlanner(random, seed, profile).plan()
        val sources = SourceFileGenerator(random, seed % SURROGATE_EVERY == SURROGATE_REMAINDER, shape)
        val twinSeed = if (profile == CaseProfile.TWINS) random.nextLong() else 0L
        val files = plans.mapIndexed { index, plan ->
            if (plan.twin > 0) twin(plan, twinSeed) else sources.generate(plan, "f$index")
        }
        val surfaces = SurfaceGenerator(random).generate(files)
        val relationships = RelationshipGenerator(random)
        val calls = relationships.calls(files)
        val unresolved = relationships.unresolved(files)
        val generations = surfaces.map { it.generation }
        val input = ContextInput(
            task = task(),
            candidates = CandidateGenerator(random, candidateMixOf(profile, random)).generate(files, surfaces),
            boundaries = calls + generations,
            tests = files.filter { it.kind == FileKind.TEST }.map { NodeRef.OfArtifact(it.artifact.ref) },
            unresolved = unresolved,
        )
        val artifacts = files.map { it.artifact } + surfaces.map { it.artifact }
        val symbols = files.flatMap { it.symbols } + surfaces.map { it.symbol }
        val content = files.associate { it.artifact.ref to it.text } +
            surfaces.associate { it.artifact.ref to it.text }
        val base = ContextFixture(artifacts, symbols, content, calls + generations + unresolved)
        return GeneratedSelection(seed, StructuredFixture(base, workspaceStructure(plans)), input, files, profile)
    }

    private fun twin(plan: FilePlan, twinSeed: Long): GeneratedFile =
        SourceFileGenerator(Random(twinSeed), false, shape).generate(plan, "t${plan.twin}")

    private fun task(): String = if (seed % SURROGATE_EVERY == SURROGATE_REMAINDER && random.chance(HALF)) {
        random.pick(SURROGATE_TASKS)
    } else {
        random.pick(TASKS)
    }

    private companion object {
        const val SURROGATE_EVERY = 6L
        const val SURROGATE_REMAINDER = 1L
        const val HALF = 50
        val TASKS = listOf("fix the parser", "change α🙂 handling", "", "  \n", "keep `ticks` and\nnewlines", "漢字 task")
        val SURROGATE_TASKS = listOf("bad \uD83D task", "stray \uDE00 surrogate")
    }
}
