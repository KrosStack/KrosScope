package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType

/** The artifact types of native code: its sources, and the build files that declare the libraries it becomes. */
internal object NativeArtifactTypes {
    val source = ArtifactType("native.source", ArtifactCategory.NATIVE)
    val rustSource = ArtifactType("native.rust-source", ArtifactCategory.NATIVE)
    val cmakeLists = ArtifactType("native.cmake-lists", ArtifactCategory.CONFIG)
    val cargoManifest = ArtifactType("native.cargo-manifest", ArtifactCategory.CONFIG)
    val ndkMakefile = ArtifactType("native.ndk-makefile", ArtifactCategory.CONFIG)

    val sources = setOf(source, rustSource)
    val buildFiles = setOf(cmakeLists, cargoManifest, ndkMakefile)
}
