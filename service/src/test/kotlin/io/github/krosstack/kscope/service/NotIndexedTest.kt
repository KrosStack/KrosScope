package io.github.krosstack.kscope.service

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotIndexedTest {
    private val queries: Map<String, (KrosScope) -> Any> = mapOf(
        "analyze" to { scope -> scope.analyze("greet a person") },
        "context" to { scope -> scope.context("greet a person") },
        "symbol" to { scope -> scope.symbol("Lib") },
        "impact" to { scope -> scope.impact("Lib") },
        "related" to { scope -> scope.related("Lib") },
        "graph" to { scope -> scope.graphView() },
        "resolve" to { scope -> scope.resolveNode("Lib") },
        "graph reader" to { scope -> scope.graph() },
        "search index" to { scope -> scope.searchIndex() },
    )

    private fun <T> inWorkspace(block: (Path) -> T): T {
        val root = Files.createTempDirectory("kscope-not-indexed")
        try {
            write(root, "settings.gradle.kts", "include(\":lib\")\n")
            write(root, "lib/build.gradle.kts", "")
            write(root, "lib/src/main/kotlin/Lib.kt", "class Lib\n")
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

    @Test
    fun `every query on a workspace that was never scanned says it is not indexed and creates nothing`() =
        inWorkspace { root ->
            KrosScope.open(root).use { scope ->
                queries.forEach { (name, query) ->
                    val failure = assertFailsWith<NotIndexedException>(name) { query(scope) }
                    assertTrue(failure.indexLocation.endsWith(".kscope"), name)
                }
                assertFalse(scope.isIndexed())
            }

            assertTrue(Files.notExists(root.resolve(".kscope")), "a query created the index directory")
        }

    @Test
    fun `an index without any file counts as no index and a query leaves it as it is`() {
        val root = Files.createTempDirectory("kscope-empty-index")
        try {
            KrosScope.open(root).use { it.scan() }
            val index = root.resolve(".kscope/index.db")
            assertTrue(Files.isRegularFile(index))
            val before = Files.readAllBytes(index).contentHashCode()

            KrosScope.open(root).use { scope ->
                assertFalse(scope.isIndexed())
                assertFailsWith<NotIndexedException> { scope.analyze("greet a person") }
            }

            assertEquals(before, Files.readAllBytes(index).contentHashCode())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a scan makes the same workspace queryable and the index is found without loading it`() = inWorkspace { root ->
        KrosScope.open(root).use { scope ->
            assertFalse(scope.isIndexed())
            scope.scan()
            assertTrue(scope.isIndexed())
            assertEquals("Lib", scope.symbol("Lib").symbols.first().symbol.name)
        }
        KrosScope.open(root).use { reopened -> assertTrue(reopened.isIndexed()) }
    }
}
