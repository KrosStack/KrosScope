package io.github.krosstack.kscope.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MethodDisambiguatorTest {
    private val module = ModuleId("repo//:core:model")

    private fun idOf(descriptor: SymbolDescriptor) = SymbolIdentity.workspace(module, "main", descriptor).toSymbolId()

    @Test
    fun `parameters are joined in declaration order`() {
        val parameters = listOf(DeclaredParameterType("String"), DeclaredParameterType("Int"))
        val rendered = MethodDisambiguator.render(null, parameters)
        assertEquals("String,Int", rendered)
    }

    @Test
    fun `an extension receiver comes first`() {
        val rendered = MethodDisambiguator.render("Task", listOf(DeclaredParameterType("Int")))
        assertEquals("this:Task,Int", rendered)
    }

    @Test
    fun `a vararg parameter and a function type are marked`() {
        val parameters = listOf(
            DeclaredParameterType(MethodDisambiguator.FUNCTION_TYPE),
            DeclaredParameterType("Int", isVararg = true),
        )
        val rendered = MethodDisambiguator.render(null, parameters)
        assertEquals("Function,vararg:Int", rendered)
    }

    @Test
    fun `no receiver and no parameters give an empty disambiguator`() {
        assertEquals("", MethodDisambiguator.render(null, emptyList()))
    }

    @Test
    fun `an extension property is a method style segment with its receiver`() {
        val descriptor = SymbolDescriptor().namespace("app").extensionProperty("label", "Task")
        assertEquals("app/label(this:Task).", descriptor.render())
    }

    @Test
    fun `extension properties with the same name on different receivers have different ids`() {
        val onTask = idOf(SymbolDescriptor().namespace("app").extensionProperty("label", "Task"))
        val onProject = idOf(SymbolDescriptor().namespace("app").extensionProperty("label", "Project"))
        assertNotEquals(onTask, onProject)
    }

    @Test
    fun `an extension property differs from a member property of the same name`() {
        val extension = idOf(SymbolDescriptor().namespace("app").type("Task").extensionProperty("label", "Task"))
        val member = idOf(SymbolDescriptor().namespace("app").type("Task").term("label"))
        assertNotEquals(extension, member)
    }

    @Test
    fun `an extension property id parses back to the same identity`() {
        val identity = SymbolIdentity.workspace(
            module,
            "commonMain",
            SymbolDescriptor().namespace("app").extensionProperty("label", "Task"),
        )
        assertEquals(ParsedSymbolId.Valid(identity), SymbolId.parse(identity.toSymbolId().value))
    }
}
