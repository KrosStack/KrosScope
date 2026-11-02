package io.github.krosstack.kscope.engine.scope

import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.artifactPaths
import io.github.krosstack.kscope.engine.store
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScopeRecordingTest {
    private val appOnly = ScanScope(include = listOf("app/**"))

    private fun TestWorkspace.engineWith(scope: () -> ScanScope) = engine(scope = scope).also {
        seedRepositoryId("fixed-repository-id")
    }

    @Test
    fun `generated patterns mark the files that are listed and add nothing from disk`() =
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engineWith { ScanScope(generated = listOf("docs", "**/ghost")) }.use { engine ->
                val report = engine.update()
                val artifacts = engine.graph().artifacts().associateBy { it.ref.path.value }
                assertEquals("settings", artifacts.getValue("docs/guide.md").generated?.generator)
                assertNull(artifacts.getValue("README.md").generated)
                assertTrue(report.diagnostics.any { it.message.contains("**/ghost") })
                assertTrue(report.diagnostics.none { it.message.contains("'docs'") })
            }
        }

    @Test
    fun `a pattern that matches nothing gives one scan note per pattern`() =
        TestWorkspace("gradle-multi").use { workspace ->
            val scope = ScanScope(include = listOf("app/**", "nowhere/**"), exclude = listOf("**/nothing"))
            workspace.engineWith { scope }.use { engine ->
                val notes = engine.update().diagnostics.filter { it.code.value == "discovery.pattern-matches-nothing" }
                assertEquals(2, notes.size, notes.toString())
                assertTrue(notes.any { "scan.include" in it.message && "nowhere/**" in it.message })
                assertTrue(notes.any { "scan.exclude" in it.message && "**/nothing" in it.message })
            }
        }

    @Test
    fun `a link inside the included directory does not lead out of the workspace`() =
        TestWorkspace("gradle-multi").use { workspace ->
            val outside = Files.createDirectories(workspace.root.resolveSibling("outside"))
            Files.writeString(outside.resolve("Secret.kt"), "class Secret\n")
            val linked = runCatching {
                Files.createSymbolicLink(workspace.root.resolve("app/escape"), outside)
                Files.createSymbolicLink(workspace.root.resolve("app/Secret.kt"), outside.resolve("Secret.kt"))
            }.isSuccess
            assumeTrue(linked, "symbolic links cannot be created here")
            workspace.engineWith { appOnly }.use { engine ->
                engine.update()
                assertTrue(engine.artifactPaths().none { "escape" in it || it.endsWith("Secret.kt") })
            }
        }

    @Test
    fun `the settings unit exists only while the scope is not the default`() =
        TestWorkspace("gradle-multi").use { workspace ->
            var scope = ScanScope.ALL
            workspace.engineWith { scope }.use { engine ->
                engine.update()
                assertTrue(engine.store.units().none { it.producer.id == "kscope.settings" })

                scope = appOnly
                engine.update()
                val units = engine.store.units().filter { it.producer.id == "kscope.settings" }
                assertEquals(1, units.size)
                val key = units.single().fingerprint
                assertEquals(0, engine.store.batch(units.single().producer, units.single().owner)?.facts?.size)

                scope = ScanScope(include = listOf("app/**", "core/**"))
                engine.update()
                assertNotEquals(key, engine.store.units().single { it.producer.id == "kscope.settings" }.fingerprint)

                scope = ScanScope.ALL
                engine.update()
                assertTrue(engine.store.units().none { it.producer.id == "kscope.settings" })
            }
        }

    @Test
    fun `the index says whether it was built with a scope`() = TestWorkspace("gradle-multi").use { workspace ->
        var scope = appOnly
        workspace.engineWith { scope }.use { engine ->
            engine.update()
            assertTrue(engine.isIndexedWith(appOnly))
            assertTrue(!engine.isIndexedWith(ScanScope.ALL))
            scope = ScanScope.ALL
            engine.update()
            assertTrue(engine.isIndexedWith(ScanScope.ALL))
        }
    }
}
