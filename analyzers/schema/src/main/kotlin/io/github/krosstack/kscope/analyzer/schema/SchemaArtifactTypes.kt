package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType

internal object SchemaArtifactTypes {
    val proto = ArtifactType("proto.source", ArtifactCategory.SCHEMA)
    val sqldelight = ArtifactType("sqldelight.source", ArtifactCategory.SCHEMA)
    val roomSchema = ArtifactType("room.schema", ArtifactCategory.SCHEMA)
    val openApi = ArtifactType("openapi.document", ArtifactCategory.SCHEMA)
    val kotlinSource = ArtifactType("kotlin.source", ArtifactCategory.SOURCE)
    val buildScript = ArtifactType("gradle.build-script", ArtifactCategory.BUILD)
}
