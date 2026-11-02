package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.analyzer.jni.libraries.LibraryLinker
import io.github.krosstack.kscope.analyzer.jni.libraries.LibraryTargetProducer
import io.github.krosstack.kscope.analyzer.jni.loads.LoadCallProducer
import io.github.krosstack.kscope.spi.ArtifactDetector
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactProducer

/**
 * The capabilities for native code: the detectors of native sources and build files, the producers of native
 * functions, library targets and library loads, and the linkers of `external` methods to functions and of loads to
 * library targets.
 */
public object JniCapabilities {
    public fun detectors(): List<ArtifactDetector> = listOf(
        NativeSourceDetector(),
        RustSourceDetector(),
        NativeBuildFileDetector(NativeArtifactTypes.cmakeLists, "CMakeLists.txt"),
        NativeBuildFileDetector(NativeArtifactTypes.cargoManifest, "Cargo.toml"),
        NativeBuildFileDetector(NativeArtifactTypes.ndkMakefile, "Android.mk"),
    )

    public fun producers(): List<FactProducer> =
        listOf(NativeFunctionProducer(), LibraryTargetProducer(), LoadCallProducer())

    public fun linkers(): List<BoundaryLinker> = listOf(JniLinker(), LibraryLinker())
}
