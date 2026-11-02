package io.github.krosstack.kscope.analyzer.android.aidl

import io.github.krosstack.kscope.analyzer.android.aidl.linking.AidlLinker
import io.github.krosstack.kscope.analyzer.android.aidl.stub.AidlSurfaceSynthesizer
import io.github.krosstack.kscope.spi.ArtifactDetector
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.SurfaceSynthesizer

/** The capabilities for AIDL: the detector, the symbols of the contracts, the Java surface and the linker. */
public object AidlCapabilities {
    public fun detectors(): List<ArtifactDetector> = listOf(AidlSourceDetector())

    public fun producers(): List<FactProducer> = listOf(AidlProducer())

    public fun synthesizers(): List<SurfaceSynthesizer> = listOf(AidlSurfaceSynthesizer())

    public fun linkers(): List<BoundaryLinker> = listOf(AidlLinker())
}
