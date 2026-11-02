package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector

private val declarationSuffixes = listOf(".d.ts", ".d.mts", ".d.cts")

/** Claims the TypeScript or the JavaScript sources of [type], by extension; declaration files are no sources. */
internal class ScriptSourceDetector private constructor(
    private val type: ArtifactType,
    private val extensions: Set<String>,
) : ArtifactDetector {
    override val id: String = type.id

    override fun detect(candidate: ArtifactCandidate): ArtifactType? {
        val name = candidate.path.value.substringAfterLast('/').lowercase()
        val isDeclaration = declarationSuffixes.any { name.endsWith(it) }
        return type.takeIf { name.substringAfterLast('.', "") in extensions && !isDeclaration }
    }

    companion object {
        fun typescript() = ScriptSourceDetector(WebArtifactTypes.typescript, setOf("ts", "tsx", "mts", "cts"))

        fun javascript() = ScriptSourceDetector(WebArtifactTypes.javascript, setOf("js", "jsx", "mjs", "cjs"))
    }
}
