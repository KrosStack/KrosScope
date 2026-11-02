package io.github.krosstack.kscope.analyzer.schema.room

import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector

/** Claims the schema files that Room exports: JSON with a format version and the entities of a database. */
internal class RoomSchemaDetector : ArtifactDetector {
    override val id: String = SchemaArtifactTypes.roomSchema.id

    override fun detect(candidate: ArtifactCandidate): ArtifactType? {
        if (!candidate.path.value.endsWith(".json")) return null
        val head = candidate.head().toString(Charsets.UTF_8)
        val isExport = REQUIRED_KEYS.all { "\"$it\"" in head }
        return SchemaArtifactTypes.roomSchema.takeIf { isExport }
    }

    private companion object {
        val REQUIRED_KEYS = listOf("formatVersion", "database", "entities")
    }
}
