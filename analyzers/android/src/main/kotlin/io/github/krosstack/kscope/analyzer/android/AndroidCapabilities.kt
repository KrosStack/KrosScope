package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.analyzer.android.buildconfig.BuildConfigSynthesizer
import io.github.krosstack.kscope.analyzer.android.linking.JvmClassLinker
import io.github.krosstack.kscope.analyzer.android.linking.ResourceLinker
import io.github.krosstack.kscope.analyzer.android.manifest.ManifestProducer
import io.github.krosstack.kscope.analyzer.android.rclass.RClassSynthesizer
import io.github.krosstack.kscope.analyzer.android.viewbinding.ViewBindingSynthesizer
import io.github.krosstack.kscope.spi.ArtifactDetector
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.SurfaceSynthesizer

/**
 * The capabilities for Android: detectors, the producers of resources and the manifest, the `R`, view binding and
 * `BuildConfig` stubs, and the linkers of resources and of class names. AIDL is a group of its own,
 * [io.github.krosstack.kscope.analyzer.android.aidl.AidlCapabilities].
 */
public object AndroidCapabilities {
    public fun detectors(): List<ArtifactDetector> =
        AndroidArtifactTypes.resources.map(::ResourceFolderDetector) + AndroidManifestDetector()

    public fun producers(): List<FactProducer> = listOf(ResourceProducer(), ManifestProducer())

    public fun synthesizers(): List<SurfaceSynthesizer> =
        listOf(RClassSynthesizer(), ViewBindingSynthesizer(), BuildConfigSynthesizer())

    public fun linkers(): List<BoundaryLinker> = listOf(ResourceLinker(), JvmClassLinker())
}
