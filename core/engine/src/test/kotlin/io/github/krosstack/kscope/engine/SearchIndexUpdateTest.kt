package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.engine.progress.ProgressEvent
import io.github.krosstack.kscope.engine.progress.ProgressListener
import io.github.krosstack.kscope.engine.progress.ScanStages
import io.github.krosstack.kscope.spi.search.SearchScope
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SearchIndexUpdateTest {
    private val greeter = "core/src/commonMain/kotlin/com/example/core/Greeter.kt"
    private val registry get() = coreRegistry(FileNamedSymbolProducer("symbols"))

    @Test
    fun `a scan makes the content of sources and the paths of all files searchable`() =
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry).use { engine ->
                engine.update()
                assertContains(engine.hits(SearchScope.ARTIFACTS, "greet").paths(), greeter)
                assertContains(engine.hits(SearchScope.ARTIFACTS, "notes").paths(), "notes.txt")
                assertContains(engine.hits(SearchScope.ARTIFACTS, "nothing").paths(), "docs/guide.md")
                assertContains(engine.hits(SearchScope.SYMBOLS, "greeter").symbolIds(), "test:$greeter")
            }
        }

    @Test
    fun `an edited file is found by its new words and no longer by its old ones`() =
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry).use { engine ->
                engine.update()
                workspace.write(greeter, "class Greeter { fun farewell() = \"Bye\" }\n")
                engine.update()
                assertEquals(listOf(greeter), engine.hits(SearchScope.ARTIFACTS, "farewell").paths())
                assertFalse(greeter in engine.hits(SearchScope.ARTIFACTS, "hello").paths())
                engine.assertMatchesRebuild()
            }
        }

    @Test
    fun `text changed after discovery is retried when it matches the artifact hash again`() =
        TestWorkspace().use { workspace ->
            val path = "src/Notes.kt"
            val original = "val originalword = 1\n"
            workspace.write(path, original)
            val listener = ProgressListener { event ->
                if (event is ProgressEvent.Started && event.stage == ScanStages.SEARCH_INDEX) {
                    workspace.write(path, "val unexpectedword = 2\n")
                }
            }
            val config = WorkspaceConfig(workspace.root, workspace.indexDirectory)
            val unexpectedHits = ScanEngine(coreRegistry(), config, listener).use { engine ->
                engine.update()
                assertEquals(listOf(path), engine.hits(SearchScope.ARTIFACTS, "notes").paths())
                engine.hits(SearchScope.ARTIFACTS, "unexpectedword").paths()
            }
            workspace.write(path, original)
            workspace.engine().use { engine ->
                engine.update()
                assertEquals(listOf(path), engine.hits(SearchScope.ARTIFACTS, "originalword").paths())
                assertEquals(emptyList(), unexpectedHits)
                assertEquals(emptyList(), engine.hits(SearchScope.ARTIFACTS, "unexpectedword").paths())
                engine.assertMatchesRebuild()
            }
        }

    @Test
    fun `a deleted or renamed file leaves the index and its symbol with it`() =
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry).use { engine ->
                engine.update()
                workspace.move(greeter, "core/src/commonMain/kotlin/com/example/core/Welcomer.kt")
                engine.update()
                assertFalse("test:$greeter" in engine.hits(SearchScope.SYMBOLS, "greeter").symbolIds())
                assertEquals(1, engine.hits(SearchScope.SYMBOLS, "welcomer").size)
                assertTrue(greeter !in engine.hits(SearchScope.ARTIFACTS, "greeter").paths())
                engine.assertMatchesRebuild()
            }
        }

    @Test
    fun `a file above the size limit is found by its path only`() = TestWorkspace("gradle-multi").use { workspace ->
        workspace.write("core/Big.kt", "val needle = " + "1 + ".repeat(LARGE_REPEATS) + "1\n")
        workspace.engine(registry, maxArtifactBytes = LIMIT_BYTES).use { engine ->
            engine.update()
            assertContains(engine.hits(SearchScope.ARTIFACTS, "big").paths(), "core/Big.kt")
            assertEquals(emptyList(), engine.hits(SearchScope.ARTIFACTS, "needle").paths())
            assertContains(engine.hits(SearchScope.ARTIFACTS, "greeter").paths(), greeter)
        }
    }

    @Test
    fun `an update that changes nothing writes nothing to the index`() =
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry).use { engine ->
                engine.update()
                workspace.indexConnection().use { watcher ->
                    val before = watcher.dataVersion()
                    engine.update()
                    assertEquals(before, watcher.dataVersion())
                }
            }
        }

    @Test
    fun `verification fails when the search index of the incremental store lost a document`() =
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry).use { engine ->
                engine.update()
                workspace.indexConnection().use { it.execute(DELETE_FIRST_ARTIFACT_DOCUMENT) }
                val verify = assertNotNull(engine.update(verify = true).verify)
                assertFalse(verify.matches)
                assertEquals(0, verify.differingUnits)
                assertEquals(1, verify.differingSearchDocuments)
            }
        }

    @Test
    fun `an index of an older schema is rebuilt together with its search tables`() =
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine(registry).use { it.update() }
            workspace.indexConnection().use { it.execute("PRAGMA user_version = 3") }
            workspace.engine(registry).use { engine ->
                engine.update()
                assertContains(engine.hits(SearchScope.ARTIFACTS, "greet").paths(), greeter)
            }
        }

    private companion object {
        const val LARGE_REPEATS = 4_000
        const val LIMIT_BYTES = 4_096L
        const val DELETE_FIRST_ARTIFACT_DOCUMENT =
            "DELETE FROM search_artifact WHERE rowid = (SELECT MIN(rowid) FROM search_artifact)"
    }
}
