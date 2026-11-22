package io.github.krosstack.kscope.cli.lookup

import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.service.KrosScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParentIndexTest {
    private val queries = listOf(
        listOf("info"),
        listOf("analyze", "change the library"),
        listOf("context", "change the library", "--estimator", "chars"),
        listOf("symbol", "Lib"),
        listOf("impact", "Lib"),
        listOf("related", "Lib"),
        listOf("graph"),
        listOf("graph", "--scope", "neighborhood", "--node", "Lib"),
    )
    private val formats = listOf("text", "markdown")
    private val leading = listOf("schemaVersion", "indexLevel", "indexLocation")
    private val printed = queries.flatMap { query -> formats.map { query + listOf("--format", it) } } +
        listOf(listOf("graph", "--format", "dot"))

    @Test
    fun `every read command in a subfolder answers from the index of a parent folder`() = inNestedWorkspace { nested ->
        printed.forEach { query ->
            val found = nested.run(query + "--quiet")
            val stated = nested.stated(query + "--quiet")

            assertEquals(0, found.statusCode, "$query: ${found.output}")
            assertEquals(stated.stdout, found.stdout, query.toString())
            assertEquals(nested.line + "\n" + stated.stderr, found.stderr, query.toString())
        }
    }

    @Test
    fun `json names the index of the parent folder in a field and prints no line`() = inNestedWorkspace { nested ->
        queries.forEach { query ->
            val found = nested.run(query + listOf("--format", "json", "--quiet"))
            val stated = nested.stated(query + listOf("--format", "json", "--quiet"))
            val json = parseJson(found.stdout)

            assertEquals(nested.index, json.getValue("indexLocation").jsonPrimitive.content, query.toString())
            assertEquals(stated.stderr, found.stderr, query.toString())
            if (query == listOf("info")) {
                assertEquals(stated.stdout, found.stdout)
            } else {
                assertEquals(leading, json.keys.take(leading.size), query.toString())
                assertEquals(parseJson(stated.stdout), JsonObject(json - "indexLocation"), query.toString())
            }
        }
    }

    @Test
    fun `a folder with its own index answers as before and prints no line`() = inNestedWorkspace { nested ->
        (printed + queries.map { it + listOf("--format", "json") }).forEach { query ->
            val own = runIn(nested.root, nested.base, query + "--quiet")
            val stated = nested.stated(query + "--quiet")

            assertEquals(stated.stdout, own.stdout, query.toString())
            assertEquals(stated.stderr, own.stderr, query.toString())
            assertEquals(0, own.statusCode, query.toString())
        }
    }

    @Test
    fun `the nearest index wins and an index without files is passed over`() = inNestedWorkspace { nested ->
        val empty = Files.createDirectories(nested.root.resolve("docs/empty"))
        KrosScope.open(empty).use { it.scan() }
        val inner = scanned(nested.root.resolve("lib"))
        val information = listOf("info", "--format", "json")

        val fromEmpty = parseJson(runIn(empty, nested.base, information).stdout)
        val fromInner = parseJson(nested.run(information).stdout)

        assertEquals(nested.index, fromEmpty.getValue("indexLocation").jsonPrimitive.content)
        assertEquals(slashed(inner.resolve(".kscope")), fromInner.getValue("indexLocation").jsonPrimitive.content)
    }

    @Test
    fun `a stated workspace is not searched for`() = inNestedWorkspace { nested ->
        val workspace = listOf("--workspace", nested.folder.toString())
        listOf(workspace + listOf("symbol", "Lib"), listOf("symbol", "Lib") + workspace).forEach { arguments ->
            val result = runIn(nested.base, nested.base, arguments + "--quiet")

            assertEquals(ExitCodes.FAILURE, result.statusCode, result.output)
            assertEquals(NO_INDEX_LINE, result.stderr)
            assertEquals("", result.stdout)
        }
    }

    @Test
    fun `the level and the trust of the found workspace apply`() = inNestedWorkspace { nested ->
        Files.writeString(nested.root.resolve("kscope.toml"), "level = \"syntax\"\n")
        assertEquals(0, runKscope("trust", "--workspace", nested.root.toString()).statusCode)
        try {
            val info = parseJson(nested.run(listOf("info", "--format", "json")).stdout)
            val level = info.getValue("settings").jsonObject.getValue("level").jsonObject
            val symbol = nested.run(listOf("symbol", "Lib", "--quiet"))

            assertEquals("syntax", info.getValue("indexLevel").jsonPrimitive.content)
            assertEquals("kscope.toml", level.getValue("origin").jsonPrimitive.content)
            assertTrue(level.getValue("trusted").jsonPrimitive.boolean)
            assertTrue(symbol.stderr.contains("Index level: syntax; references are not analysed."), symbol.stderr)
        } finally {
            runKscope("trust", "--revoke", "--workspace", nested.root.toString())
        }
    }

    @Test
    fun `commands that read no index keep the current folder`() = inNestedWorkspace { nested ->
        val here = listOf("--workspace", nested.folder.toString())
        val doctor = nested.run(listOf("doctor"))
        val structure = nested.run(listOf("info", "--structure"))

        assertTrue(doctor.stdout.contains("No index in this workspace"), doctor.stdout)
        assertEquals(runIn(nested.base, nested.base, listOf("info", "--structure") + here).stdout, structure.stdout)
        assertFalse(structure.stderr.contains("parent folder"), structure.stderr)
    }
}
