package io.github.krosstack.kscope.analyzer.jni.loads

import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType

/** The artifact types of the sources that can load a native library. */
internal object JvmSourceTypes {
    val kotlin = ArtifactType("kotlin.source", ArtifactCategory.SOURCE)
    val java = ArtifactType("java.source", ArtifactCategory.SOURCE)

    val all = setOf(kotlin, java)
}
