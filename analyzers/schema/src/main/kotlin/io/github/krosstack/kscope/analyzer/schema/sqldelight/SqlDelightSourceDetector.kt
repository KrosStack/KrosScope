package io.github.krosstack.kscope.analyzer.schema.sqldelight

import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector

/** Claims the SQLDelight statement files and migrations below a `sqldelight` directory. */
internal class SqlDelightSourceDetector : ArtifactDetector {
    override val id: String = SchemaArtifactTypes.sqldelight.id

    override fun detect(candidate: ArtifactCandidate): ArtifactType? {
        val segments = candidate.path.value.split('/')
        val isStatementFile = segments.last().endsWith(".sq") || segments.last().endsWith(".sqm")
        return SchemaArtifactTypes.sqldelight.takeIf { isStatementFile && SQLDELIGHT_DIRECTORY in segments.dropLast(1) }
    }

    private companion object {
        const val SQLDELIGHT_DIRECTORY = "sqldelight"
    }
}
