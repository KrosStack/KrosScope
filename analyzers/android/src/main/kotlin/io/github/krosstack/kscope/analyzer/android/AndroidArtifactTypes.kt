package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType

internal object AndroidArtifactTypes {
    val values = ArtifactType("android.resource.values", ArtifactCategory.RESOURCE)
    val layout = ArtifactType("android.resource.layout", ArtifactCategory.RESOURCE)
    val navigation = ArtifactType("android.resource.navigation", ArtifactCategory.RESOURCE)
    val menu = ArtifactType("android.resource.menu", ArtifactCategory.RESOURCE)
    val drawable = ArtifactType("android.resource.drawable", ArtifactCategory.RESOURCE)
    val other = ArtifactType("android.resource.other", ArtifactCategory.RESOURCE)
    val manifest = ArtifactType("android.manifest", ArtifactCategory.CONFIG)

    val gradleProperties = ArtifactType("gradle.properties", ArtifactCategory.BUILD)
    val buildScript = ArtifactType("gradle.build-script", ArtifactCategory.BUILD)

    val resources: Set<ArtifactType> = setOf(values, layout, navigation, menu, drawable, other)
}
