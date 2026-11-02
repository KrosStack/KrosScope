package io.github.krosstack.kscope.analyzer.compose

import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType

internal object ComposeArtifactTypes {
    val values = ArtifactType("compose.resource.values", ArtifactCategory.RESOURCE)
    val drawable = ArtifactType("compose.resource.drawable", ArtifactCategory.RESOURCE)
    val font = ArtifactType("compose.resource.font", ArtifactCategory.RESOURCE)
    val file = ArtifactType("compose.resource.file", ArtifactCategory.RESOURCE)

    /** The files that define resources with a name. A `files` file is read by its path and has no name. */
    val named: Set<ArtifactType> = setOf(values, drawable, font)

    val all: Set<ArtifactType> = named + file
}
