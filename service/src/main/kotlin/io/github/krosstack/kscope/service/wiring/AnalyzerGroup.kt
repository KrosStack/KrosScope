package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.service.settings.AnalyzerKind
import io.github.krosstack.kscope.spi.ArtifactDetector
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.SurfaceSynthesizer

/** The capabilities of one switchable group of analyzers. */
internal class AnalyzerGroup(
    val kind: AnalyzerKind,
    val detectors: List<ArtifactDetector> = emptyList(),
    val synthesizers: List<SurfaceSynthesizer> = emptyList(),
    val producers: List<FactProducer> = emptyList(),
    val linkers: List<BoundaryLinker> = emptyList(),
)
