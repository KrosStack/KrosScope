package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.stub.StubClass
import io.github.krosstack.kscope.analyzer.schema.stub.StubFile
import io.github.krosstack.kscope.analyzer.schema.stub.StubForm
import io.github.krosstack.kscope.analyzer.schema.stub.StubFunction
import io.github.krosstack.kscope.analyzer.schema.stub.StubParameter
import io.github.krosstack.kscope.analyzer.schema.stub.StubProperty
import io.github.krosstack.kscope.analyzer.schema.stub.StubSymbols
import io.github.krosstack.kscope.analyzer.schema.stub.StubText
import io.github.krosstack.kscope.analyzer.schema.stub.StubTypes
import io.github.krosstack.kscope.model.Scope
import kotlin.test.Test
import kotlin.test.assertEquals

class StubTextTest {
    @Test
    fun `the simple name of a type is what the analyzer puts in the identity of a function`() {
        assertEquals("ByteString", StubTypes.simpleName("okio.ByteString"))
        assertEquals("List", StubTypes.simpleName("List<String>"))
        assertEquals("Map", StubTypes.simpleName("Map<String, List<Int>?>?"))
        assertEquals("Function", StubTypes.simpleName("(id: String, title: String) -> T"))
        assertEquals("Function", StubTypes.simpleName("((Int) -> Unit)?"))
        assertEquals("String", StubTypes.simpleName("String?"))
    }

    @Test
    fun `names that are keywords or not identifiers are written in backticks`() {
        assertEquals("due_at", StubTypes.written("due_at"))
        assertEquals("`in`", StubTypes.written("in"))
        assertEquals("`first-name`", StubTypes.written("first-name"))
    }

    @Test
    fun `a class is written with its constructor, supertypes, members and companion`() {
        val companion = StubClass("Companion", StubForm.COMPANION, members = listOf(StubProperty("ADAPTER", "A<Box>")))
        val box = StubClass(
            name = "Box",
            form = StubForm.DATA_CLASS,
            constructor = listOf(StubParameter("in", "Int", "0", isProperty = true), StubParameter("note", "String")),
            superTypes = "Base",
            members = listOf(StubFunction("copy", listOf(StubParameter("in", "Int", "this.`in`")), "Box")),
            nested = listOf(companion),
        )
        val expected = """
            package a.b

            public data class Box(
                public val `in`: Int = 0,
                note: String,
            ) : Base {
                public fun copy(`in`: Int = this.`in`): Box = TODO()

                public companion object {
                    public val ADAPTER: A<Box> = TODO()
                }
            }

        """.trimIndent()
        assertEquals(expected, StubText.render(StubFile("a.b", listOf(box))))
    }

    @Test
    fun `symbols of a stub have the ids of its package, classes, properties and methods`() {
        val box = StubClass(
            name = "Box",
            form = StubForm.CLASS,
            constructor = listOf(StubParameter("size", "Int", isProperty = true)),
            members = listOf(
                StubFunction("put", listOf(StubParameter("item", "kotlin.collections.List<Int>")), "Unit"),
            ),
        )
        val identity = IdentityFactory(TestRepository.moduleId(":lib"), "main")
        val facts = StubSymbols(identity, "test", Scope()).of(StubFile("a.b", listOf(box)))
        val ids = facts.symbols.map { Render.id(it.id).substringAfter("@main:") }
        assertEquals(listOf("a/b/Box#", "a/b/Box#`<init>`(Int).", "a/b/Box#size.", "a/b/Box#put(List)."), ids)
        assertEquals(
            listOf("a.b.Box", "a.b.Box.<init>", "a.b.Box.size", "a.b.Box.put"),
            facts.symbols.map { it.qualifiedName },
        )
    }
}
