package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.writeFile
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StructureCommandTest {
    @Test
    fun `info structure describes a workspace that was never scanned and creates nothing`() = inWorkspace { root ->
        val result = runKscope("info", "--structure", "--workspace", root.toString())

        assertEquals(0, result.statusCode, result.stderr)
        assertTrue(result.stdout.startsWith("A single Gradle build."), result.stdout)
        assertTrue(result.stdout.contains("settings.gradle.kts in ."), result.stdout)
        assertFalse(Files.exists(root.resolve(".kscope")))
    }

    @Test
    fun `info structure as json carries the conclusion and the evidence`() = inWorkspace { root ->
        writeFile(root, "web/package.json", "{}")

        val result = runKscope("info", "--structure", "--format", "json", "--workspace", root.toString())
        val json = parseJson(result.stdout)

        assertEquals("1", json.getValue("schemaVersion").jsonPrimitive.content)
        assertEquals("0", json.getValue("ignoredDirectoriesSkipped").jsonPrimitive.content)
        val conclusion = json.getValue("conclusion").jsonObject
        assertEquals("several-builds", conclusion.getValue("kind").jsonPrimitive.content)
        val project = json.getValue("projects").jsonArray.single().jsonObject
        assertEquals("web", project.getValue("directory").jsonPrimitive.content)
    }

    @Test
    fun `the depth option limits how far below the workspace it looks`() = inWorkspace { root ->
        writeFile(root, "a/b/c/package.json", "{}")

        val shallow = runKscope("info", "--structure", "--structure-depth", "1", "--workspace", root.toString())
        val deep = runKscope("info", "--structure", "--structure-depth", "3", "--workspace", root.toString())

        assertFalse(shallow.stdout.contains("package.json"), shallow.stdout)
        assertTrue(deep.stdout.contains("a/b/c"), deep.stdout)
    }

    @Test
    fun `a depth outside the range is a usage error`() = inWorkspace { root ->
        val result = runKscope("info", "--structure", "--structure-depth", "99", "--workspace", root.toString())

        assertEquals(2, result.statusCode)
    }

    @Test
    fun `info without the option still needs an index`() = inWorkspace { root ->
        val result = runKscope("info", "--workspace", root.toString())

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("No index found"), result.stderr)
    }
}
