package io.github.krosstack.kscope.context.compact

import io.github.krosstack.kscope.context.CharacterEstimator
import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.context.ContextFixture
import io.github.krosstack.kscope.context.sourceArtifact
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount

internal class CompactWorkspace(private vararg val texts: String) {
    private val artifacts: List<Artifact> =
        texts.mapIndexed { index, text -> sourceArtifact("source/File$index", text) }
    private val estimator = CharacterEstimator()

    fun artifact(index: Int): Artifact = artifacts[index]

    fun file(index: Int): ArtifactRef = artifacts[index].ref

    fun text(index: Int): String = texts[index]

    fun input(vararg files: ArtifactRef): ContextInput = ContextInput(
        "t",
        files.mapIndexed { rank, file -> ContextCandidate(NodeRef.OfArtifact(file), 1.0 - rank * RANK_STEP) },
    )

    fun compiler(symbols: List<Symbol> = emptyList()): ContextCompiler {
        val content = artifacts.mapIndexed { index, artifact -> artifact.ref to texts[index] }.toMap()
        val fixture = ContextFixture(artifacts, symbols, content)
        return ContextCompiler(fixture, fixture, estimator)
    }

    fun compile(
        input: ContextInput,
        budget: Int,
        symbols: List<Symbol> = emptyList(),
        level: ContextLevel = ContextLevel.FULL,
    ): ContextPackage = compiler(symbols).compile(input, level, TokenCount(budget))

    companion object {
        const val LINE = 40
        private const val BIG_LINES = 60
        private const val RANK_STEP = 0.1

        fun big(): String = (0 until BIG_LINES).joinToString("") { "a".repeat(LINE) + "\n" }
    }
}
