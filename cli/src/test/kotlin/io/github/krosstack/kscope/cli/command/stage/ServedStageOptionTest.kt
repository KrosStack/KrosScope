package io.github.krosstack.kscope.cli.command.stage

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.runKscopeWith
import io.github.krosstack.kscope.cli.serve.withTestServer
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ServedStageOptionTest {
    @Test
    fun `served lexical analysis matches the one shot response`() = inScannedWorkspace { root ->
        withTestServer(root) {
            val base = listOf("analyze", "change the library", "--workspace", root.toString(), "--format", "json")
            val local = runKscopeWith(listOf("--no-server") + base + listOf("--stage", "lexical"))
            val served = runKscopeWith(base + listOf("--stage", "lexical"))

            assertEquals(local.stdout, served.stdout)
        }
    }

    @Test
    fun `served stage is selected separately for each request`() = inScannedWorkspace { root ->
        withTestServer(root) { server ->
            val base = listOf("analyze", "change the library", "--workspace", root.toString(), "--format", "json")
            val stages = listOf(listOf("--stage", "lexical"), emptyList()).map { flags ->
                parseJson(runKscopeWith(base + flags).stdout)["stage"]?.jsonPrimitive?.content
            }

            assertEquals(listOf("lexical", null) to 2, stages to server.requestCount)
        }
    }
}
