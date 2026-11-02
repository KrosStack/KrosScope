package io.github.krosstack.kscope.analyzer.compose

import io.github.krosstack.kscope.analyzer.compose.res.ComposeResSynthesizer
import io.github.krosstack.kscope.spi.ArtifactDetector
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.SurfaceSynthesizer

/** The capabilities for Compose Multiplatform resources: the detectors, the producer and the `Res` stub. */
public object ComposeCapabilities {
    public fun detectors(): List<ArtifactDetector> = ComposeArtifactTypes.all.map(::ComposeResourceFolderDetector)

    public fun producers(): List<FactProducer> = listOf(ComposeResourceProducer())

    public fun synthesizers(): List<SurfaceSynthesizer> = listOf(ComposeResSynthesizer())
}
