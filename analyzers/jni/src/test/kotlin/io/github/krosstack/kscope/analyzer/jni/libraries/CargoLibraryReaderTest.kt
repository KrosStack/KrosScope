package io.github.krosstack.kscope.analyzer.jni.libraries

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class CargoLibraryReaderTest {
    private fun read(text: String) = CargoLibraryReader().read(text.trimIndent())

    @Test
    fun `the lib table of the reference manifest names a cdylib at the line of its name`() {
        val library = read(
            """
            [package]
            name = "tasktide-hash"
            version = "0.3.0"
            edition = "2021"

            [lib]
            name = "tasktide_hash"
            crate-type = ["cdylib"]

            [dependencies]
            jni = "0.21"
            """,
        ).single()
        assertEquals("tasktide_hash", library.name)
        assertEquals("cargo.library", library.kind)
        assertEquals("cdylib", library.linkage)
        assertEquals(Certainty.EXACT, library.certainty)
        assertEquals(TextRange(7, 9, 7, 22), library.nameRange)
        assertEquals(library.nameRange, library.declaration)
    }

    @Test
    fun `without a name in the lib table the package name is used with underscores`() {
        val library = read(
            """
            [package]
            name = "tasktide-hash"

            [lib]
            crate-type = ["staticlib", "cdylib"]
            """,
        ).single()
        assertEquals("tasktide_hash", library.name)
        assertEquals(TextRange(2, 9, 2, 22), library.nameRange)
    }

    @Test
    fun `a dylib is heuristic`() {
        val library = read("[package]\nname = \"p\"\n[lib]\ncrate-type = [\"dylib\"]\n").single()
        assertEquals("dylib", library.linkage)
        assertEquals(Certainty.HEURISTIC, library.certainty)
    }

    @Test
    fun `dotted keys, inline tables and the underscore spelling of crate-type are read`() {
        assertEquals("a", read("lib.name = \"a\"\nlib.crate-type = [\"cdylib\"]\n").single().name)
        assertEquals("b", read("lib = { name = \"b\", crate-type = [\"cdylib\"] }\n").single().name)
        assertEquals("c", read("[lib]\nname = \"c\"\ncrate_type = [\"cdylib\"]\n").single().name)
    }

    @Test
    fun `a library that is not dynamic is not a native library`() {
        val staticOnly = "[package]\nname = \"p\"\n[lib]\ncrate-type = [\"staticlib\", \"rlib\"]\n"
        val defaultType = "[package]\nname = \"p\"\n[lib]\nname = \"q\"\n"
        val noLib = "[package]\nname = \"p\"\n[dependencies]\nserde = \"1\"\n"
        listOf(staticOnly, defaultType, noLib).forEach { assertEquals(emptyList(), read(it), it) }
    }

    @Test
    fun `a manifest of a workspace, with a wrong type or that does not parse has no library`() {
        val workspace = "[workspace]\nmembers = [\"a\"]\n"
        val wrongType = "[lib]\nname = 3\ncrate-type = \"cdylib\"\n"
        val broken = "[lib\nname = \"a\"\ncrate-type = [\"cdylib\"]\n"
        listOf(workspace, wrongType, broken).forEach { assertEquals(emptyList(), read(it), it) }
    }
}
