package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptOutline
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsOutlineTest {
    private val fixture = OutlineFixture()
    private val settings = fixture.ofSample("scripts/settings.gradle.kts").calls

    @AfterTest
    fun closeFixture() = fixture.close()

    @Test
    fun `includes keep their literal arguments in every call form`() {
        val includes = settings.named("include")
        assertEquals(listOf(":app"), includes[0].literals())
        assertEquals(listOf(":core:model", ":core:legacy"), includes[1].literals())
        assertEquals(listOf(":feature:tasks", ":feature:settings"), includes[2].literals())
    }

    @Test
    fun `the root project name is an assignment call with a literal value`() {
        val rootName = settings.single { it.name == "=" && it.receiver == "rootProject.name" }
        assertEquals(listOf(ScriptArgument(null, "TaskTide", "\"TaskTide\"")), rootName.arguments)
    }

    @Test
    fun `a project directory assignment keeps the target call and the raw value`() {
        val projectDir = settings.single { it.name == "=" && it.receiver == "project(\":sync:engine\").projectDir" }
        assertEquals(listOf(null), projectDir.literals())
        assertEquals("file(\"sync/engine-impl\")", projectDir.arguments.single().expression)
    }

    @Test
    fun `calls in a conditional are listed`() {
        assertEquals(1, settings.named("include").count { it.literals() == listOf(":native:bridge") })
    }

    @Test
    fun `dynamic includes keep their raw expression`() {
        val dynamic = settings.named("forEach").single()
        assertEquals("extras", dynamic.receiver)
        assertEquals("include", dynamic.body.single().name)
        assertEquals(null, dynamic.body.single().arguments.single().literal)
    }

    @Test
    fun `ranges are one based and cover the whole statement`() {
        val plugins = fixture.ofText("x.gradle.kts", "plugins {\n    id(\"java\")\n}\n").calls.single()
        assertEquals(TextRange(1, 1, 3, 2), plugins.range)
        assertEquals(TextRange(2, 5, 2, 15), plugins.body.single().range)
    }

    @Test
    fun `named arguments and trailing lambda bodies are kept`() {
        val call = fixture.ofText("x.gradle.kts", "register(name = \"copy\", type = Copy::class) { from(\"docs\") }")
            .calls.single()
        val expected = listOf(ScriptArgument("name", "copy", "\"copy\""), ScriptArgument("type", null, "Copy::class"))
        assertEquals(expected, call.arguments)
        assertEquals("from", call.body.single().name)
    }

    @Test
    fun `strings with interpolation are expressions and not literals`() {
        val script = "include(\":a\", \":\$name\", \"\${prefix}b\", 3, true, -1)"
        val call = fixture.ofText("x.gradle.kts", script).calls.single()
        assertEquals(listOf(":a", null, null, "3", "true", "-1"), call.literals())
    }

    @Test
    fun `a script with syntax errors still yields the calls that were parsed`() {
        val outline = fixture.ofText("x.gradle.kts", "first()\nsecond(\nthird()\n")
        assertTrue(outline.calls.any { it.name == "first" })
    }

    @Test
    fun `an empty script has no calls`() {
        assertEquals(ScriptOutline(emptyList()), fixture.ofText("x.gradle.kts", ""))
    }
}
