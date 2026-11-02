package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.service.analysis.SampleWorkspace
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** An instance that stays open notices that its index was deleted, replaced or written by another process. */
class IndexFreshnessTest {
    private val sample = SampleWorkspace("tests")
    private val index = sample.root.resolve(".kscope")

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    private fun names(service: KrosScope, query: String): List<String> =
        service.symbol(query).symbols.map { it.symbol.name }

    private fun addFarewell() = sample.write(
        "core/src/main/kotlin/sample/tests/Farewell.kt",
        "package sample.tests\n\nclass Farewell {\n    fun wave(): String = \"bye\"\n}\n",
    )

    private fun deleteIndexFiles(): Boolean = try {
        Files.list(index).use { files -> files.filter { it.fileName.toString().startsWith("index.db") }.toList() }
            .forEach(Files::delete)
        true
    } catch (_: IOException) {
        false
    }

    @Test
    fun `a refresh changes nothing until another process commits and then sees the commit`() {
        KrosScope.open(sample.root).use { it.scan() }
        KrosScope.open(sample.root).use { open ->
            val before = names(open, "Greeter")
            open.refreshIndex()
            assertEquals(before, names(open, "Greeter"))
            assertTrue("Greeter" in before)
            assertFalse("Farewell" in names(open, "Farewell"))
            addFarewell()
            KrosScope.open(sample.root).use { it.scan() }

            open.refreshIndex()

            assertTrue("Farewell" in names(open, "Farewell"))
        }
    }

    @Test
    fun `an index that was deleted is forgotten`() {
        KrosScope.open(sample.root).use { it.scan() }
        KrosScope.open(sample.root).use { open ->
            assertTrue("Greeter" in names(open, "Greeter"))
            assumeTrue(deleteIndexFiles(), "this platform does not delete an index file that is open")

            open.refreshIndex()

            assertFalse(open.isIndexed())
            assertFailsWith<NotIndexedException> { open.symbol("Greeter") }
            assertFalse(Files.exists(index.resolve("index.db")), "a refresh must not create an index")
        }
    }

    @Test
    fun `an index that was replaced is read again`() {
        KrosScope.open(sample.root).use { it.scan() }
        KrosScope.open(sample.root).use { open ->
            assertFalse("Farewell" in names(open, "Farewell"))
            assumeTrue(deleteIndexFiles(), "this platform does not delete an index file that is open")
            addFarewell()
            KrosScope.open(sample.root).use { it.scan() }

            open.refreshIndex()

            assertTrue("Farewell" in names(open, "Farewell"))
            assertTrue("Greeter" in names(open, "Greeter"))
        }
    }
}
