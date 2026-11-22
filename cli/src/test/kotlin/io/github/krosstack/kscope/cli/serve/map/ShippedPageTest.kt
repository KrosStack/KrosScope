package io.github.krosstack.kscope.cli.serve.map

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShippedPageTest {
    private fun resource(name: String): String? =
        ShippedPageTest::class.java.getResource("$DIRECTORY/$name")?.readText()

    private val files: List<String> = checkNotNull(resource("MANIFEST")).lines().filter(String::isNotBlank)

    @Test
    fun `every file the manifest names ships`() {
        files.forEach { assertNotNull(resource(it), it) }
    }

    @Test
    fun `every file in the directory of the page is listed in the manifest`() {
        val manifest = checkNotNull(ShippedPageTest::class.java.getResource("$DIRECTORY/MANIFEST"))
        val shipped = Files.list(Path.of(manifest.toURI()).parent).use { stream ->
            stream.map { it.fileName.toString() }.toList().toSet()
        }
        assertEquals(emptySet(), shipped - files.toSet() - "MANIFEST")
    }

    @Test
    fun `every script a module imports is listed in the manifest`() {
        val imported = files.filter { it.endsWith(".js") }
            .flatMap { name -> IMPORT.findAll(resource(name).orEmpty()).map { it.groupValues[1] } }
            .toSet()
        assertEquals(emptySet(), imported - files.toSet())
    }

    @Test
    fun `the page asks for nothing from another host`() {
        files.forEach { name ->
            val addresses = ADDRESS.findAll(resource(name).orEmpty()).map { it.value }.toSet() - SVG_NAMESPACES
            assertTrue(addresses.isEmpty(), "$name names $addresses")
        }
    }

    private companion object {
        const val DIRECTORY = "/io/github/krosstack/kscope/cli/serve/map"
        val IMPORT = Regex("""from "\./([^"]+)"""")
        val ADDRESS = Regex("""https?://[^\s"'`)]+""")
        val SVG_NAMESPACES = setOf("http://www.w3.org/2000/svg")
    }
}
