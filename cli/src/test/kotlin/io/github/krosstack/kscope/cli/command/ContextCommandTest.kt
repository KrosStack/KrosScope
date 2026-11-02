package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.runKscope
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextCommandTest {
    @Test
    fun `all service json includes its schema version`() = inScannedWorkspace { root ->
        val result = runKscope("info", "--workspace", root.toString(), "--format", "json")

        assertEquals(1, parseJson(result.stdout).getValue("schemaVersion").jsonPrimitive.int)
    }

    @Test
    fun `doctor reports environment checks as clean json without exposing paths`() = inWorkspace { root ->
        val result = runKscope("doctor", "--workspace", root.toString(), "--format", "json")

        assertEquals(0, result.statusCode)
        assertEquals(1, parseJson(result.stdout).getValue("schemaVersion").jsonPrimitive.int)
        assertTrue(result.stdout.contains("jdk"))
        assertTrue(result.stdout.contains("worker"))
        assertTrue(!result.stdout.contains(root.toString()))
        assertEquals("", result.stderr)
    }

    @Test
    fun `context accepts a zero budget and markdown with no ml`() = inScannedWorkspace { root ->
        val result = runKscope(
            "--no-ml", "context", "change", "--workspace", root.toString(),
            "--budget", "0", "--level", "minimal", "--estimator", "chars", "--format", "markdown",
        )

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.isBlank())
        assertEquals("", result.stderr)
    }

    @Test
    fun `context rejects negative budgets with a usage exit code`() = inWorkspace { root ->
        val result = runKscope("context", "change", "--workspace", root.toString(), "--budget", "-1")

        assertEquals(2, result.statusCode)
    }

    @Test
    fun `update is exposed as an alias of scan`() {
        val result = runKscope("update", "--help")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("--rebuild"))
        assertTrue(result.stdout.contains("--verify"))
    }
}
