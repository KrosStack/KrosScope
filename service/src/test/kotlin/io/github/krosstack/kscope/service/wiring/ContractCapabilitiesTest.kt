package io.github.krosstack.kscope.service.wiring

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ContractCapabilitiesTest {
    private val registry = DefaultCapabilities.registry(Path.of("index"))
    private val detectors = registry.detectors.map { it.id }
    private val producers = registry.producers.map { it.descriptor.producer.id }
    private val linkers = registry.linkers.map { it.descriptor.producer.id }

    @Test
    fun `the aidl capabilities are registered`() {
        assertTrue("aidl.source" in detectors)
        assertTrue("kscope.android.aidl" in producers)
        assertTrue("kscope.android.aidl-surface" in registry.synthesizers.map { it.descriptor.producer.id })
        assertTrue("kscope.android.aidl-linker" in linkers)
    }

    @Test
    fun `the jni capabilities are registered`() {
        assertTrue("native.source" in detectors)
        assertTrue("kscope.jni.native-functions" in producers)
        assertTrue("kscope.jni.linker" in linkers)
    }
}
