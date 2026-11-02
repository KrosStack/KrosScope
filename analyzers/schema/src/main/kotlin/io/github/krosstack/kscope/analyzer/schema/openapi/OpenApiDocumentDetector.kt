package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector

private val extensions = listOf(".yaml", ".yml", ".json")

/**
 * Claims OpenAPI and Swagger documents: YAML and JSON files whose first bytes declare the version in a top-level
 * `openapi` or `swagger` key. A file that another detector claims by its name or its content, such as an exported Room
 * schema, has no such key, so it keeps its type whichever detector is asked first.
 */
internal class OpenApiDocumentDetector : ArtifactDetector {
    override val id: String = SchemaArtifactTypes.openApi.id

    override fun detect(candidate: ArtifactCandidate): ArtifactType? {
        val name = candidate.path.value.lowercase()
        val isCandidate = extensions.any { name.endsWith(it) }
        return SchemaArtifactTypes.openApi.takeIf {
            isCandidate && OpenApiHead.declaresVersion(candidate.head().toString(Charsets.UTF_8))
        }
    }
}
