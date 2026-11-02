package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.engine.CoreCapabilities
import io.github.krosstack.kscope.engine.FakeTrustedProvider
import io.github.krosstack.kscope.engine.RecordingProducer
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.assertMatchesRebuild
import io.github.krosstack.kscope.engine.canonicalDump
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.store
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.BuildModelResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrustedBuildModelTest {
    private val trusted = FakeTrustedProvider()
    private val bySourceSet = RecordingProducer("by-source-set", granularity = OwnershipKind.SOURCE_SET)
    private val greeter = "core/src/commonMain/kotlin/com/example/core/Greeter.kt"

    private fun registry(vararg extra: Any) =
        coreRegistry(*extra, providers = CoreCapabilities.buildModelProviders() + trusted)

    private fun BuildModel.sourceSetId(suffix: String) = sourceSets.first { it.id.value.endsWith(suffix) }.id

    private fun classpathOf(suffix: String, vararg entries: String): (BuildModel) -> Map<SourceSetId, List<String>> =
        { model -> mapOf(model.sourceSetId(suffix) to entries.asList()) }

    @Test
    fun `a provider that executes build logic is skipped unless the workspace is trusted`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry(), trusted = false).use { it.update() }
        }
        assertEquals(0, trusted.calls)
    }

    @Test
    fun `a trusted provider wins over the structural one even when its id sorts later`() {
        val late = FakeTrustedProvider(id = "zzz.trusted")
        val registry = coreRegistry(providers = CoreCapabilities.buildModelProviders() + late)
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry, trusted = true).use { engine ->
                engine.update()
                assertEquals(1, late.calls)
                val producers = engine.store.units().map { it.producer.id }.toSet()
                assertTrue("zzz.trusted" in producers && "kscope.build.structural" !in producers)
            }
        }
    }

    @Test
    fun `classpaths reach the producers on every update and are not stored`() {
        trusted.classpaths = classpathOf(":core@commonMain", "/libs/a.jar", "/libs/b.jar")
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry(bySourceSet), trusted = true).use { engine ->
                engine.update()
                val core = bySourceSet.classpaths.entries.single()
                assertTrue(core.key.value.endsWith(":core@commonMain"))
                assertEquals(listOf("/libs/a.jar", "/libs/b.jar"), core.value)
                bySourceSet.classpaths = emptyMap()

                workspace.write(greeter, "class Greeter\n")
                engine.update()
                assertEquals(listOf("/libs/a.jar", "/libs/b.jar"), bySourceSet.classpaths.values.single())
                assertTrue("/libs/a.jar" !in engine.canonicalDump())
            }
        }
    }

    @Test
    fun `an unchanged trusted model is asked again but neither stored nor produced again`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry(bySourceSet), trusted = true).use { engine ->
                val first = engine.update()
                val second = engine.update()
                assertEquals(2, trusted.calls)
                assertEquals(0, second.unitsProduced)
                assertEquals(first.unitsProduced, second.unitsSkipped)
                engine.assertMatchesRebuild()
            }
        }
    }

    @Test
    fun `a changed classpath produces the source set units again`() {
        trusted.classpaths = classpathOf(":core@commonMain", "/libs/a.jar")
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry(bySourceSet), trusted = true).use { engine ->
                engine.update()
                bySourceSet.calls.clear()
                engine.update()
                assertTrue(bySourceSet.calls.isEmpty())

                trusted.classpaths = classpathOf(":core@commonMain", "/libs/a.jar", "/libs/b.jar")
                engine.update()
                assertEquals(SOURCE_SET_UNITS, bySourceSet.producedKeys.size)
            }
        }
    }

    @Test
    fun `a failed trusted provider is reported and the next provider is used`() {
        trusted.failure = BuildModelResult.Failed(ReasonCode("gradle.tooling-failed"), "Could not configure :app")
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry(), trusted = true).use { engine ->
                val report = engine.update()
                val warning = report.diagnostics.single { it.code.value == "gradle.tooling-failed" }
                assertEquals(DiagnosticSeverity.WARNING, warning.severity)
                assertEquals("Could not configure :app", warning.message)
                assertEquals(listOf(":", ":app", ":core"), engine.graph().structure().modules.map { it.path })
                val producers = engine.store.units().map { it.producer.id }.toSet()
                assertTrue("kscope.build.structural" in producers && "test.trusted" !in producers)
            }
        }
    }

    private companion object {
        const val SOURCE_SET_UNITS = 6
    }
}
