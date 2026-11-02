package io.github.krosstack.kscope.service

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueryProgressTest {
    private val events = CopyOnWriteArrayList<ProgressEvent>()
    private val listener = ProgressListener { events += it }

    private fun <T> inWorkspace(block: (Path) -> T): T {
        val root = Files.createTempDirectory("kscope-progress")
        try {
            write(root, "settings.gradle.kts", "include(\":lib\")\n")
            write(root, "lib/build.gradle.kts", "")
            write(root, "lib/src/main/kotlin/Lib.kt", "class Lib\n")
            write(root, "README.md", "# Sample\n")
            return block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun write(root: Path, relative: String, text: String) {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    private fun started(): List<String> = events.filterIsInstance<ProgressEvent.Started>().map { it.stage.id }

    private fun <T> KrosScope.scanned(block: KrosScope.() -> T): T {
        scan()
        events.clear()
        return block()
    }

    @Test
    fun `a scan reports the stages of the engine through the options of the service`() = inWorkspace { root ->
        KrosScope.open(root, OpenOptions(progress = listener)).use { it.scan() }

        assertEquals(ScanStages.DISCOVERY.id, started().first())
        val expected = listOf("build-model", "production", "linking", "search-index")
        assertTrue(started().containsAll(expected), started().toString())
        val production = events.filterIsInstance<ProgressEvent.Advanced>().filter { it.stage == ScanStages.PRODUCTION }
        assertEquals(production.first().total, production.last().done)
    }

    @Test
    fun `analyze loads the index once and then ranks and estimates`() = inWorkspace { root ->
        KrosScope.open(root, OpenOptions(progress = listener)).use { scope ->
            scope.scanned {
                analyze("Lib")
                assertEquals(listOf("load-index", "rank", "estimate-context"), started())
                events.clear()
                analyze("Lib")
                assertEquals(listOf("rank", "estimate-context"), started(), "the index is not loaded twice")
            }
        }
    }

    @Test
    fun `context reports the compilation after the ranking`() = inWorkspace { root ->
        KrosScope.open(root, OpenOptions(progress = listener)).use { scope ->
            scope.scanned {
                context("Lib")
                assertEquals(listOf("load-index", "rank", "compile-context"), started())
            }
        }
    }

    @Test
    fun `symbol impact related and graph each report one step after loading the index`() = inWorkspace { root ->
        KrosScope.open(root, OpenOptions(progress = listener)).use { scope ->
            scope.scanned {
                symbol("Lib")
                impact("Lib")
                related("Lib")
                graphView()
                assertEquals(
                    listOf("load-index", "search-symbols", "trace-impact", "follow-relationships", "build-graph"),
                    started(),
                )
            }
        }
    }

    @Test
    fun `a scan makes the next query load the index again`() = inWorkspace { root ->
        KrosScope.open(root, OpenOptions(progress = listener)).use { scope ->
            scope.scanned {
                symbol("Lib")
                scan()
                events.clear()
                symbol("Lib")
                assertEquals(listOf("load-index", "search-symbols"), started())
            }
        }
    }

    @Test
    fun `a workspace is indexed once a scan has put files in it, not when it was only opened`() = inWorkspace { root ->
        KrosScope.open(root).use { scope ->
            assertFalse(scope.isIndexed())
            assertFailsWith<NotIndexedException> { scope.symbol("Lib") }
            assertFalse(scope.isIndexed(), "a query on a workspace that was never scanned does not make an index")
            scope.scan()
            assertTrue(scope.isIndexed())
        }
    }

    @Test
    fun `a listener that is not given leaves the queries as they were`() = inWorkspace { root ->
        val results = listOf(OpenOptions(), OpenOptions(progress = listener)).map { options ->
            KrosScope.open(root, options).use { scope ->
                scope.scan()
                ServiceJson.encode(scope.analyze("Lib")) + ServiceJson.encode(scope.symbol("Lib"))
            }
        }

        assertEquals(results.first(), results.last())
    }
}
