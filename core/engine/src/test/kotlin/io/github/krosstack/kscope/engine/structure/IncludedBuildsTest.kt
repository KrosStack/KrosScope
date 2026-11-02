package io.github.krosstack.kscope.engine.structure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IncludedBuildsTest {
    private fun included(script: String, name: String = "settings.gradle.kts"): List<String> =
        StructureWorkspace().use { workspace ->
            workspace.file(name, script)
            IncludedBuilds.declaredIn(workspace.root.resolve(name), workspace.root)
        }

    @Test
    fun `an includeBuild behind a line comment names nothing`() {
        assertTrue(included("// includeBuild(\"tools\")\ninclude(\":app\")\n").isEmpty())
        assertTrue(included("include(\":app\") // includeBuild(\"tools\")\n").isEmpty())
    }

    @Test
    fun `an includeBuild inside a block comment names nothing`() {
        assertTrue(included("/*\n includeBuild(\"tools\")\n*/\ninclude(\":app\")\n").isEmpty())
        assertTrue(included("/** includeBuild 'tools' */\n", name = "settings.gradle").isEmpty())
    }

    @Test
    fun `a Kotlin block comment that holds another one ends where the outer one ends`() {
        val script = "/* outer /* inner */ includeBuild(\"hidden\") */\nincludeBuild(\"shown\")\n"

        assertEquals(listOf("shown"), included(script))
    }

    @Test
    fun `a Groovy block comment ends at the first closing mark`() {
        val script = "/* outer /* inner */ includeBuild('shown')\n"

        assertEquals(listOf("shown"), included(script, name = "settings.gradle"))
    }

    @Test
    fun `an includeBuild written inside a string literal names nothing`() {
        assertTrue(included("println(\"call includeBuild('tools') first\")\n").isEmpty())
        assertTrue(included("val hint = \"\"\"\n  includeBuild(\"tools\")\n\"\"\"\n").isEmpty())
    }

    @Test
    fun `comment marks inside a string literal do not hide the code after it`() {
        val script = "val home = \"https://example.invalid/a\"\nincludeBuild(\"tools\") /* kept */\n"

        assertEquals(listOf("tools"), included(script))
    }

    @Test
    fun `a comment between the name and the argument does not hide the call`() {
        assertEquals(listOf("tools"), included("includeBuild /* local */ (\"tools\")\n"))
        assertEquals(listOf("tools"), included("includeBuild(\n    // the tools\n    \"tools\",\n)\n"))
    }

    @Test
    fun `a longer name that ends in includeBuild is another function`() {
        assertTrue(included("maybeIncludeBuild(\"tools\")\nnotincludeBuild(\"other\")\n").isEmpty())
        assertEquals(listOf("tools"), included("settings.includeBuild(\"tools\")\n"))
    }

    @Test
    fun `a commented-out includeBuild leaves the build it names an independent build`() =
        StructureWorkspace().use { workspace ->
            workspace.file("settings.gradle.kts", "// includeBuild(\"tools\")\n")
            workspace.file("tools/settings.gradle.kts")

            val builds = StructureScanner(StructureScanner.DEFAULT_DEPTH).scan(workspace.root).builds

            assertEquals(listOf(".", "tools"), builds.map { it.directory })
            assertTrue(builds.all { it.includedBuilds.isEmpty() }, builds.toString())
        }
}
