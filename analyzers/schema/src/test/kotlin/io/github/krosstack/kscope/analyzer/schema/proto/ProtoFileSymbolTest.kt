package io.github.krosstack.kscope.analyzer.schema.proto

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.Render
import io.github.krosstack.kscope.analyzer.schema.Resources
import io.github.krosstack.kscope.analyzer.schema.TestWorkspace
import io.github.krosstack.kscope.analyzer.schema.facts
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.model.Symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PATH = "lib/src/main/proto/library.proto"

private fun fileSymbolOf(source: String): Symbol {
    val workspace = TestWorkspace(mapOf(PATH to source), listOf(ModuleSpec(":lib")))
    return workspace.produce(ProtoProducer()).facts().symbols().first { it.kind.id == "proto.file" }
}

private fun Symbol.line() = requireNotNull(nameLocation).range.startLine

class ProtoFileSymbolTest {
    @Test
    fun `the file symbol is named after the file and sits at its package statement`() {
        val file = fileSymbolOf(Resources.text("wire/library.proto"))

        assertEquals("library.proto", file.name)
        assertEquals(PATH, file.qualifiedName)
        assertEquals("package sample.library", file.signature)
        assertEquals(mapOf("proto.package" to "sample.library"), file.attributes)
        assertEquals(3, file.line())
        assertEquals(3 to 3, requireNotNull(file.declaration).range.let { it.startLine to it.endLine })
        assertEquals(null, file.container)
        assertEquals("SCHEMA", file.kind.category.name)
    }

    @Test
    fun `a file without a package statement is at its first line`() {
        val file = fileSymbolOf("syntax = \"proto3\";\nmessage Ping {}\n")

        assertEquals(1, file.line())
        assertEquals("library.proto", file.signature)
        assertEquals(emptyMap(), file.attributes)
    }

    @Test
    fun `a package in a comment or a string is not the package statement`() {
        val source = "// package not.this;\n/* package nor.this; */\nsyntax = \"proto3\";\n" +
            "option java_package = \"package nor.that;\";\npackage real.one;\nmessage Ping {}\n"
        val file = fileSymbolOf(source)

        assertEquals(5, file.line())
        assertEquals("package real.one", file.signature)
    }

    @Test
    fun `the id of a file symbol carries the module, the source set and the path of the file`() {
        val workspace = TestWorkspace(mapOf(PATH to "package p;\n"), listOf(ModuleSpec(":lib")))
        val ids = workspace.produce(ProtoProducer()).facts().symbols().map { Render.id(it.id) }

        assertTrue(":lib@main:proto/`$PATH`:" in ids, "$ids")
    }
}
