package io.github.krosstack.kscope.context.selection.generated

import io.github.krosstack.kscope.context.declaration
import io.github.krosstack.kscope.context.sourceArtifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import kotlin.random.Random

internal class SurfaceGenerator(private val random: Random) {
    fun generate(files: List<GeneratedFile>): List<GeneratedSurface> {
        val sources = files.filter { it.kind == FileKind.SOURCE }.flatMap { file -> file.symbols.map { file to it } }
        if (sources.isEmpty() || !random.chance(SURFACE_PERCENT)) return emptyList()
        return (0 until (1 + random.nextInt(MAX_SURFACES))).map { index ->
            val (file, symbol) = random.pick(sources)
            surface(index, file, symbol)
        }
    }

    private fun surface(index: Int, file: GeneratedFile, source: Symbol): GeneratedSurface {
        val path = "generated/Surface$index"
        val text = "SECRET BODY $index"
        val origin = SymbolOrigin.Generated(GENERATOR, NodeRef.OfSymbol(source.id))
        val artifact = sourceArtifact(path, text).copy(
            ref = ArtifactRef(file.artifact.ref.repository, RepoPath(path)),
            generated = GeneratedInfo(GENERATOR, NodeRef.OfSymbol(source.id)),
        )
        val symbol = declaration("Surface$index", artifact, TextRange(1, 1, 1, text.length + 1), "class Surface$index")
            .copy(origin = origin)
        val generation = Relationship(
            NodeRef.OfSymbol(source.id),
            RelationKind.GENERATES,
            null,
            Resolution.Resolved(NodeRef.OfSymbol(symbol.id), Certainty.EXACT),
            EvidenceClass.CONVENTION,
            null,
            Scope(),
        )
        return GeneratedSurface(artifact, text, symbol, generation)
    }

    private companion object {
        const val SURFACE_PERCENT = 40
        const val MAX_SURFACES = 2
        const val GENERATOR = "test.generator"
    }
}
