package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.runKscopeWith
import io.github.krosstack.kscope.service.KrosScope
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissingIndexTest {
    private val queries = mapOf(
        "analyze" to listOf("analyze", "greet a person"),
        "context" to listOf("context", "greet a person"),
        "info" to listOf("info"),
        "symbol" to listOf("symbol", "Greeter"),
        "impact" to listOf("impact", "Greeter"),
        "related" to listOf("related", "Greeter"),
        "graph" to listOf("graph"),
        "graph neighborhood" to listOf("graph", "--scope", "neighborhood", "--node", "lib/src/main/kotlin/Lib.kt"),
    )

    @Test
    fun `a query on a workspace that was never scanned fails and says what to run`() = inWorkspace { root ->
        queries.forEach { (name, arguments) ->
            val result = runKscopeWith(arguments + listOf("--workspace", root.toString()))
            assertEquals(ExitCodes.FAILURE, result.statusCode, "$name: ${result.output}")
            assertTrue(result.stderr.contains("No index found. Run `kscope scan` first."), "$name: ${result.stderr}")
            assertEquals("", result.stdout, name)
        }
    }

    @Test
    fun `a failed query leaves nothing on disk`() = inWorkspace { root ->
        queries.values.forEach { arguments -> runKscopeWith(arguments + listOf("--workspace", root.toString())) }
        assertTrue(Files.notExists(root.resolve(".kscope")), "the query created the index directory")
    }

    @Test
    fun `the message is the same for json output and prints no result`() = inWorkspace { root ->
        queries.forEach { (name, arguments) ->
            val result = runKscopeWith(arguments + listOf("--workspace", root.toString(), "--format", "json"))
            assertEquals(ExitCodes.FAILURE, result.statusCode, name)
            assertEquals("", result.stdout, name)
        }
    }

    @Test
    fun `an index without any file counts as no index`() {
        val root = Files.createTempDirectory("kscope-cli-empty")
        try {
            KrosScope.open(root).use { it.scan() }
            assertTrue(Files.isRegularFile(root.resolve(".kscope/index.db")))

            val result = runKscope("analyze", "greet a person", "--workspace", root.toString())

            assertEquals(ExitCodes.FAILURE, result.statusCode, result.output)
            assertTrue(result.stderr.contains("No index found"), result.stderr)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `doctor works without an index and creates nothing`() = inWorkspace { root ->
        val result = runKscope("doctor", "--workspace", root.toString())

        assertEquals(0, result.statusCode, result.output)
        assertTrue(result.stdout.contains("No index in this workspace"), result.stdout)
        assertTrue(Files.notExists(root.resolve(".kscope")), "doctor created the index directory")
    }

    @Test
    fun `scan still creates the index and queries then succeed`() = inWorkspace { root ->
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)
        queries.forEach { (name, arguments) ->
            val result = runKscopeWith(arguments + listOf("--workspace", root.toString()))
            assertEquals(0, result.statusCode, "$name: ${result.output}")
        }
    }
}
