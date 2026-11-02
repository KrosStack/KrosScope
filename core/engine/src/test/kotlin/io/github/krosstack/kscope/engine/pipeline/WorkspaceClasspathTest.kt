package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.CoreCapabilities
import io.github.krosstack.kscope.engine.FakeTrustedProvider
import io.github.krosstack.kscope.engine.RecordingProducer
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.BuildModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Workspace code is analysed from source, so the build output inside the workspace never reaches a classpath. */
class WorkspaceClasspathTest {
    private val trusted = FakeTrustedProvider()
    private val bySourceSet = RecordingProducer("by-source-set", granularity = OwnershipKind.SOURCE_SET)

    private fun registry() = coreRegistry(bySourceSet, providers = CoreCapabilities.buildModelProviders() + trusted)

    private fun classpathOf(entries: List<String>): (BuildModel) -> Map<SourceSetId, List<String>> = { model ->
        mapOf(model.sourceSets.first { it.id.value.endsWith(":core@commonMain") }.id to entries)
    }

    @Test
    fun `classpath entries inside the workspace are dropped before the producers see them`() {
        TestWorkspace("gradle-multi").use { workspace ->
            val buildOutput = workspace.root.resolve("app/build/intermediates/R.jar").toString()
            val moduleClasses = workspace.root.resolve("core/build/classes/kotlin/jvm/main").toString()
            val sibling = workspace.root.resolveSibling("workspace-libs/b.jar").toString()
            trusted.classpaths = classpathOf(listOf(buildOutput, "/libs/a.jar", moduleClasses, sibling))
            workspace.engine(registry(), trusted = true).use { engine ->
                engine.update()
                assertEquals(listOf("/libs/a.jar", sibling), bySourceSet.classpaths.values.single())
            }
        }
    }

    @Test
    fun `a binary the repository keeps stays on the classpath while the build output beside it is dropped`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.write("libs/vendor.jar", "vendored")
            val vendored = workspace.root.resolve("libs/vendor.jar").toString()
            val buildOutput = workspace.root.resolve("app/build/intermediates/R.jar").toString()
            trusted.classpaths = classpathOf(listOf(buildOutput, vendored, "/libs/a.jar"))
            workspace.engine(registry(), trusted = true).use { engine ->
                engine.update()
                assertEquals(listOf(vendored, "/libs/a.jar"), bySourceSet.classpaths.values.single())
            }
        }
    }

    @Test
    fun `replacing a binary the repository keeps redoes the analysis`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.write("libs/vendor.jar", "one")
            trusted.classpaths = classpathOf(listOf(workspace.root.resolve("libs/vendor.jar").toString()))
            workspace.engine(registry(), trusted = true).use { engine ->
                engine.update()
                bySourceSet.calls.clear()
                workspace.write("libs/vendor.jar", "two")
                engine.update()
                assertTrue(bySourceSet.calls.isNotEmpty())
            }
        }
    }

    @Test
    fun `a source set whose classpath lies entirely inside the workspace has none`() {
        TestWorkspace("gradle-multi").use { workspace ->
            val buildOutput = workspace.root.resolve("app/build/intermediates/R.jar").toString()
            trusted.classpaths = classpathOf(listOf(buildOutput))
            workspace.engine(registry(), trusted = true).use { engine ->
                engine.update()
                assertTrue(bySourceSet.calls.isNotEmpty() && bySourceSet.classpaths.isEmpty())
            }
        }
    }

    @Test
    fun `rebuilding a module inside the workspace does not redo the analysis`() {
        TestWorkspace("gradle-multi").use { workspace ->
            val output = workspace.root.resolve("app/build/intermediates/R.jar").toString()
            trusted.classpaths = classpathOf(listOf("/libs/a.jar", output))
            workspace.engine(registry(), trusted = true).use { engine ->
                engine.update()
                bySourceSet.calls.clear()
                trusted.classpaths = classpathOf(listOf("/libs/a.jar", "$output.rebuilt"))
                engine.update()
                assertTrue(bySourceSet.calls.isEmpty())
            }
        }
    }
}
