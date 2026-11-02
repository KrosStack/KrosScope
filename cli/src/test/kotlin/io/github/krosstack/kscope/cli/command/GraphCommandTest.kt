package io.github.krosstack.kscope.cli.command

import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.cli.KscopeCommand
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.parseWithExitCodes
import io.github.krosstack.kscope.cli.runKscope
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphCommandTest {
    @Test
    fun `related lookup of an unknown name returns empty JSON results`() = inScannedWorkspace { root ->
        val result = runKscope("related", "Absent", "--workspace", root.toString(), "--format", "json")
        val json = parseJson(result.stdout)

        assertEquals(0, result.statusCode, result.output)
        assertEquals("Absent", json.getValue("requested").jsonPrimitive.content)
        assertTrue(json.getValue("roots").jsonArray.isEmpty())
        assertTrue(json.getValue("files").jsonArray.isEmpty())
        assertTrue(json.getValue("symbols").jsonArray.isEmpty())
        assertEquals("", result.stderr)
    }

    @Test
    fun `graph defaults to the modules of the index`() = inScannedWorkspace { root ->
        val result = runKscope("graph", "--workspace", root.toString(), "--format", "json")
        val json = parseJson(result.stdout)

        assertEquals(0, result.statusCode, result.output)
        assertEquals("modules", json.getValue("scope").jsonPrimitive.content)
        assertTrue(json.getValue("relationships").jsonArray.isEmpty())
        assertEquals("", result.stderr)
    }

    @Test
    fun `an unknown neighborhood root is empty and bad options are usage errors`() = inScannedWorkspace { root ->
        val valid = runKscope(
            "graph", "--scope", "neighborhood", "--node", "Absent",
            "--workspace", root.toString(), "--format", "json",
        )
        assertEquals(0, valid.statusCode, valid.output)
        assertTrue(parseJson(valid.stdout).getValue("nodes").jsonArray.isEmpty())

        val invalid = listOf(
            listOf("--scope", "unsupported"),
            listOf("--depth", "4"),
            listOf("--depth", "-1"),
            listOf("--limit", "0"),
            listOf("--limit", "100001"),
            listOf("--scope", "neighborhood"),
            listOf("--scope", "workspace", "--node", "Absent"),
        )
        invalid.forEach { flags ->
            val command = KscopeCommand()
            val arguments = listOf("graph") + flags + listOf("--workspace", root.toString())
            val result = command.test(arguments) { command.parseWithExitCodes(it) }
            assertEquals(2, result.statusCode, result.output)
        }
    }
}
