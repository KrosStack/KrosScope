package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.AppDependsOnCoreProvider
import io.github.krosstack.kscope.engine.MisbehavingProducer
import io.github.krosstack.kscope.engine.RecordingLinker
import io.github.krosstack.kscope.engine.RecordingProducer
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.assertMatchesRebuild
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.scanningSample
import io.github.krosstack.kscope.engine.store
import io.github.krosstack.kscope.model.OwnershipKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CapabilityDriversTest {
    private val appMain = "app/src/main/kotlin/com/example/app/Main.kt"
    private val greeter = "core/src/commonMain/kotlin/com/example/core/Greeter.kt"

    @Test
    fun `an unchanged tree skips every capability unit`() {
        val byArtifact = RecordingProducer("by-artifact")
        val bySourceSet = RecordingProducer("by-source-set", granularity = OwnershipKind.SOURCE_SET)
        val linker = RecordingLinker("linker")
        scanningSample(coreRegistry(byArtifact, bySourceSet, linker)) { _, engine ->
            val first = engine.update()
            assertEquals(2 + 6 + 6 + 1, first.unitsProduced)
            val second = engine.update()
            assertEquals(0, second.unitsProduced)
            assertEquals(2 + 6 + 6 + 1, second.unitsSkipped)
        }
        assertEquals(1, byArtifact.calls.size)
        assertEquals(1, bySourceSet.calls.size)
        assertEquals(1, linker.linkCalls)
    }

    @Test
    fun `without dependency edges any source change invalidates every source set unit`() {
        val bySourceSet = RecordingProducer("by-source-set", granularity = OwnershipKind.SOURCE_SET)
        scanningSample(coreRegistry(bySourceSet)) { workspace, engine ->
            engine.update()
            bySourceSet.calls.clear()
            workspace.write(appMain, "fun main() = Unit\n")
            engine.update()
        }
        assertEquals(6, bySourceSet.producedKeys.size)
    }

    @Test
    fun `source set units are invalidated by their own module and by the modules they depend on`() {
        val bySourceSet = RecordingProducer("by-source-set", granularity = OwnershipKind.SOURCE_SET)
        val registry = coreRegistry(bySourceSet, providers = listOf(AppDependsOnCoreProvider()))
        scanningSample(registry) { workspace, engine ->
            engine.update()
            bySourceSet.calls.clear()

            workspace.write(appMain, "fun main() = Unit\n")
            engine.update()
            assertEquals(3, bySourceSet.producedKeys.size)
            assertTrue(bySourceSet.producedKeys.all { ":app@" in it })
            bySourceSet.calls.clear()

            workspace.write(greeter, "class Greeter\n")
            engine.update()
            assertEquals(6, bySourceSet.producedKeys.size)
            engine.assertMatchesRebuild()
        }
    }

    @Test
    fun `module build root repository and workspace units are formed per scope`() {
        val expectedUnits = mapOf(
            OwnershipKind.MODULE to 2,
            OwnershipKind.BUILD_ROOT to 1,
            OwnershipKind.REPOSITORY to 1,
            OwnershipKind.WORKSPACE to 1,
        )
        expectedUnits.forEach { (kind, expected) ->
            val producer = RecordingProducer("by-$kind", granularity = kind)
            scanningSample(coreRegistry(producer)) { workspace, engine ->
                engine.update()
                assertEquals(expected, producer.producedKeys.size, "units of $kind")
                producer.calls.clear()
                workspace.write(appMain, "fun main() = Unit\n")
                engine.update()
                assertEquals(1, producer.producedKeys.size, "stale units of $kind")
                engine.assertMatchesRebuild()
            }
        }
    }

    @Test
    fun `a linker runs again only when something else changed`() {
        val linker = RecordingLinker("linker")
        scanningSample(coreRegistry(linker)) { workspace, engine ->
            engine.update()
            engine.update()
            assertEquals(1, linker.linkCalls)
            workspace.write(greeter, "class Greeter\n")
            engine.update()
            assertEquals(2, linker.linkCalls)
            engine.update()
            assertEquals(2, linker.linkCalls)
            engine.assertMatchesRebuild()
        }
    }

    @Test
    fun `a producer is handed every artifact, also those of units that are not produced`() {
        val bySourceSet = RecordingProducer("by-source-set", granularity = OwnershipKind.SOURCE_SET)
        val registry = coreRegistry(bySourceSet, providers = listOf(AppDependsOnCoreProvider()))
        scanningSample(registry) { workspace, engine ->
            engine.update()
            bySourceSet.calls.clear()
            workspace.write(appMain, "fun main() = Unit\n")
            engine.update()
            assertTrue(bySourceSet.producedKeys.all { ":app@" in it }, "Only the app units are produced")
            assertTrue(appMain in bySourceSet.contextPaths)
            assertTrue(greeter in bySourceSet.contextPaths, "The artifacts of units that are not produced are context")
        }
    }

    @Test
    fun `the stored fingerprint of a unit is the one the engine computed, whatever the producer echoes`() {
        val bySourceSet = RecordingProducer("by-source-set", granularity = OwnershipKind.SOURCE_SET)
        scanningSample(coreRegistry(bySourceSet)) { _, engine ->
            engine.update()
            val fingerprints = engine.store.units().filter { it.producer.id == "by-source-set" }.map { it.fingerprint }
            assertEquals(6, fingerprints.size)
            assertTrue(fingerprints.none { it.value == "ignored" })
        }
    }

    @Test
    fun `a linker sees the finished graph and its facts are stored`() {
        val linker = RecordingLinker("linker")
        scanningSample(coreRegistry(linker)) { _, engine ->
            val report = engine.update()
            assertTrue(report.diagnostics.any { it.code.value == "fake.linked" && it.message == "artifacts=19" })
        }
    }

    @Test
    fun `units of capabilities that left the registry are removed`() {
        val producer = RecordingProducer("recorder")
        val linker = RecordingLinker("linker")
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(coreRegistry(producer, linker)).use { it.update() }
            workspace.engine().use { engine ->
                val report = engine.update()
                assertEquals(6 + 1, report.unitsRemoved)
                assertTrue(report.diagnostics.none { it.code.value.startsWith("fake.") })
                val producers = engine.store.units().map { it.producer.id }
                assertEquals(listOf("kscope.build.structural", "kscope.discovery"), producers)
            }
        }
    }

    @Test
    fun `a batch naming another producer or an unrequested owner is dropped and reported`() {
        scanningSample(coreRegistry(MisbehavingProducer("misbehaving"))) { _, engine ->
            val report = engine.update()
            val rejected = report.diagnostics.filter { it.code.value == "engine.batch-rejected" }
            assertEquals(6 + 1, rejected.size)
            assertTrue(report.diagnostics.none { it.code.value == "fake.bad" })
        }
    }
}
