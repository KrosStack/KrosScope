package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.runKscopeWith
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ServedMlOptionTest {
    @Test
    fun `served analysis forwards ml off per request`() = inScannedWorkspace { root ->
        withTestServer(root) { server ->
            val base = listOf("analyze", "change the library", "--workspace", root.toString(), "--format", "json")
            val local = runKscopeWith(listOf("--no-server") + base + "--no-ml")
            val off = runKscopeWith(base + "--no-ml")
            val on = runKscopeWith(base)

            assertEquals(0, off.statusCode, off.output)
            assertEquals("off", parseJson(off.stdout).getValue("semantic").jsonObject["state"]?.jsonPrimitive?.content)
            assertEquals(local.stdout, off.stdout)
            assertEquals(
                "not-installed",
                parseJson(on.stdout).getValue("semantic").jsonObject["state"]?.jsonPrimitive?.content,
            )
            assertEquals(2, server.requestCount)
        }
    }
}
