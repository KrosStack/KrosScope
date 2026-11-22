package io.github.krosstack.kscope.cli.status

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.lookup.inNestedWorkspace
import io.github.krosstack.kscope.cli.lookup.slashed
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.service.KrosScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusCommandTest {
    @Test
    fun `a scanned workspace gives one line with the index, its level, its age and the server`() =
        inScannedWorkspace { root ->
            val result = runStatus(root)

            assertEquals(0, result.statusCode, result.output)
            assertEquals(
                "kscope: index ${slashed(root)}/.kscope; level resolved; scanned <age> ago; " +
                    "up to date (1 repository); server not running\n",
                masked(result.stdout),
            )
            assertEquals("", result.stderr)
        }

    @Test
    fun `an index that is behind still exits with 0 and says how`() = inScannedWorkspace { root ->
        Files.setLastModifiedTime(
            root.resolve("lib/src/main/kotlin/Lib.kt"),
            FileTime.from(Instant.now().plusSeconds(3_600)),
        )

        val result = runStatus(root)

        assertEquals(0, result.statusCode, result.output)
        assertTrue(
            masked(result.stdout).contains(
                "; behind: working tree changed (1 file, estimated from file times); server not running\n",
            ),
            result.stdout,
        )
    }

    @Test
    fun `a workspace without an index says so on standard output and exits with 1`() = inWorkspace { root ->
        val result = runStatus(root)

        assertEquals(1, result.statusCode)
        assertEquals("kscope: no index in ${slashed(root)}; run kscope scan\n", result.stdout)
        assertEquals("", result.stderr)
        assertTrue(Files.notExists(root.resolve(".kscope")))
    }

    @Test
    fun `from a folder below the workspace it reports the index of the workspace, like a stated workspace`() =
        inNestedWorkspace { nested ->
            val found = nested.run(listOf("status"))
            val stated = nested.stated(listOf("status"))

            assertEquals(0, found.statusCode, found.output)
            assertTrue(found.stdout.startsWith("kscope: index ${nested.index}; level syntax; "), found.stdout)
            assertEquals(masked(stated.stdout), masked(found.stdout))
            assertEquals("", found.stderr)
        }

    @Test
    fun `json has the fields of the result, and checkMillis and ageSeconds are the only ones that vary`() =
        inScannedWorkspace { root ->
            val result = runStatus(root, "--format", "json")
            val json = parseJson(result.stdout)

            assertEquals(0, result.statusCode, result.output)
            assertEquals(
                listOf(
                    "schemaVersion", "indexLevel", "workspace", "indexLocation", "indexed", "scannedAt", "ageSeconds",
                    "freshness", "repositories", "server", "checkMillis", "toolVersion",
                ),
                json.keys.toList(),
            )
            assertTrue(json.getValue("ageSeconds").jsonPrimitive.int >= 0)
            assertTrue(json.getValue("checkMillis").jsonPrimitive.int >= 0)
            val steady = JsonObject(json - "ageSeconds" - "checkMillis" - "scannedAt")
            assertEquals(JsonPrimitive(1), steady.getValue("schemaVersion"))
            assertEquals("resolved", levelIn(steady))
            assertEquals(slashed(root), steady.getValue("workspace").jsonPrimitive.content)
            assertEquals("${slashed(root)}/.kscope", steady.getValue("indexLocation").jsonPrimitive.content)
            assertTrue(steady.getValue("indexed").jsonPrimitive.boolean)
            assertEquals(1, steady.getValue("repositories").jsonPrimitive.int)
            assertEquals("not-behind", steady.getValue("freshness").jsonObject.getValue("state").jsonPrimitive.content)
            assertEquals(
                listOf("state", "pid", "version", "startedAt", "stale"),
                steady.getValue("server").jsonObject.keys.toList(),
            )
            assertEquals("notRunning", steady.getValue("server").jsonObject.getValue("state").jsonPrimitive.content)
            assertEquals(KrosScope.toolVersion(), steady.getValue("toolVersion").jsonPrimitive.content)
        }

    @Test
    fun `json without an index says indexed false and exits with 1, with the words on standard error`() =
        inWorkspace { root ->
            val result = runStatus(root, "--format", "json")
            val json = parseJson(result.stdout)

            assertEquals(1, result.statusCode)
            assertFalse(json.getValue("indexed").jsonPrimitive.boolean)
            assertEquals(JsonPrimitive(null as String?), json.getValue("indexLevel"))
            assertEquals(JsonPrimitive(null as String?), json.getValue("freshness"))
            assertEquals("kscope: no index in ${slashed(root)}; run kscope scan\n", result.stderr)
        }
}
