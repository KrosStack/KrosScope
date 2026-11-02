package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.service.settings.AnalyzerKind
import io.github.krosstack.kscope.service.settings.AnalyzerSettings
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultCapabilitiesTest {
    private val providers = DefaultCapabilities.registry(Path.of("index")).buildModelProviders

    @Test
    fun `the gradle providers are registered with the structural one as the last resort`() {
        assertEquals(
            listOf("kscope.build.gradle.static", "kscope.build.gradle.tooling", "kscope.build.structural"),
            providers.map { it.producer.id },
        )
    }

    @Test
    fun `only the tooling provider executes build logic`() {
        assertEquals(listOf(false, true, false), providers.map { it.requiresTrust })
    }

    @Test
    fun `the android capabilities are registered`() {
        val registry = DefaultCapabilities.registry(Path.of("index"))
        assertTrue("android.resource.values" in registry.detectors.map { it.id })
        assertTrue("android.manifest" in registry.detectors.map { it.id })
        val producers = registry.producers.map { it.descriptor.producer.id }
        val expected = listOf("kscope.analyzer.kotlin", "kscope.android.resources", "kscope.android.manifest")
        assertTrue(producers.containsAll(expected))
        val synthesizers = registry.synthesizers.map { it.descriptor.producer.id }
        assertTrue(
            synthesizers.containsAll(
                listOf("kscope.android.r-class", "kscope.android.view-binding", "kscope.android.build-config"),
            ),
        )
        val linkers = registry.linkers.associate { it.descriptor.producer.id to it.namespaces }
        assertEquals(setOf("android.resource"), linkers["kscope.android.resource-linker"])
        assertEquals(setOf("jvm.class"), linkers["kscope.android.jvm-class-linker"])
    }

    @Test
    fun `the schema capabilities are registered`() {
        val registry = DefaultCapabilities.registry(Path.of("index"))
        val detectors = registry.detectors.map { it.id }
        assertTrue(detectors.containsAll(listOf("proto.source", "sqldelight.source", "room.schema")))
        val producers = registry.producers.map { it.descriptor.producer.id }
        val schemaProducers = listOf("kscope.schema.proto", "kscope.schema.sqldelight", "kscope.schema.room")
        assertTrue(producers.containsAll(schemaProducers))
        val synthesizers = registry.synthesizers.map { it.descriptor.producer.id }
        assertTrue(synthesizers.containsAll(listOf("kscope.schema.wire-kotlin", "kscope.schema.sqldelight-stubs")))
        val linkers = registry.linkers.associate { it.descriptor.producer.id to it.namespaces }
        assertEquals(setOf("sqldelight.table"), linkers["kscope.schema.sqldelight-linker"])
        assertEquals(emptySet(), linkers["kscope.schema.room-linker"])
    }

    private fun idsWithout(vararg kinds: AnalyzerKind): Set<String> {
        val registry = DefaultCapabilities.registry(Path.of("index"), selection = AnalyzerSettings(kinds.toSet()))
        val producerIds = registry.producers.map { it.descriptor.producer.id }
        val synthesizerIds = registry.synthesizers.map { it.descriptor.producer.id }
        val linkerIds = registry.linkers.map { it.descriptor.producer.id }
        return (registry.detectors.map { it.id } + producerIds + synthesizerIds + linkerIds).toSet()
    }

    @Test
    fun `every group is on by default and a group that is off contributes nothing`() {
        val all = idsWithout()
        val groups = AnalyzerGroups.of(AnalyzerSettings.ALL)

        assertEquals(AnalyzerKind.entries, groups.map { it.kind })
        AnalyzerKind.entries.forEach { kind ->
            val group = groups.single { it.kind == kind }
            val own = group.detectors.map { it.id } + group.producers.map { it.descriptor.producer.id } +
                group.synthesizers.map { it.descriptor.producer.id } + group.linkers.map { it.descriptor.producer.id }
            assertTrue(own.isNotEmpty(), kind.key)
            assertEquals(all - own.toSet(), idsWithout(kind), kind.key)
        }
    }

    @Test
    fun `the android group leaves aidl to its own switch`() {
        val withoutAndroid = idsWithout(AnalyzerKind.ANDROID_RESOURCES)
        val withoutAidl = idsWithout(AnalyzerKind.AIDL)

        assertTrue("aidl.source" in withoutAndroid)
        assertTrue("android.manifest" !in withoutAndroid)
        assertTrue("kscope.android.resource-linker" !in withoutAndroid)
        assertTrue("aidl.source" !in withoutAidl)
        assertTrue("android.manifest" in withoutAidl)
    }
}
