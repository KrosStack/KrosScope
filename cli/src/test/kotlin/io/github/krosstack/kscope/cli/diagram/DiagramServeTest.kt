package io.github.krosstack.kscope.cli.diagram

import io.github.krosstack.kscope.cli.runKscopeWith
import io.github.krosstack.kscope.cli.serve.withTestServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagramServeTest {
    @Test
    fun `a served diagram is byte for byte the one-shot diagram in every kind and format`() {
        inDiagramWorkspace { root ->
            val kinds = listOf(
                listOf("modules"),
                listOf("modules", ":lib", "--depth", "0"),
                listOf("components"),
                listOf("symbol", "Greeter"),
                listOf("symbol", "salute", "--depth", "2"),
            )
            withTestServer(root) { server ->
                for (kind in kinds) {
                    for (format in listOf("mermaid", "dot", "json")) {
                        val args = diagramArguments(root, kind + listOf("--format", format))
                        val local = runKscopeWith(listOf("--no-server") + args)
                        val remote = runKscopeWith(args)

                        assertEquals(0, remote.statusCode, remote.output)
                        assertTrue(remote.stdout.isNotEmpty(), args.toString())
                        assertEquals(local.stdout, remote.stdout, args.toString())
                        assertEquals(local.stderr, remote.stderr, args.toString())
                    }
                }
                assertEquals(kinds.size * 3, server.requestCount)
            }
        }
    }

    @Test
    fun `a served diagram that is refused ends like the one-shot one`() {
        inDiagramWorkspace { root ->
            val refusals = listOf(
                listOf("symbol", "Absent"),
                listOf("symbol", "Lib"),
                listOf("symbol", "Lib", "--format", "json"),
                listOf("symbol", "total"),
                listOf("modules", ":absent"),
                listOf("modules", "--characters", "10"),
            )
            val refusedByTheClient = listOf(listOf("symbol"), listOf("modules", "--format", "text"))
            withTestServer(root) { server ->
                refusals.forEach { refusal ->
                    val args = diagramArguments(root, refusal)
                    val local = runKscopeWith(listOf("--no-server") + args)
                    val remote = runKscopeWith(args)

                    assertTrue(local.stdout.isNotEmpty() || local.stderr.isNotEmpty(), args.toString())
                    assertEquals(local.statusCode, remote.statusCode, args.toString())
                    assertEquals(local.stdout, remote.stdout, args.toString())
                    assertEquals(local.stderr, remote.stderr, args.toString())
                }
                assertEquals(refusals.size, server.requestCount)
                refusedByTheClient.forEach { refusal ->
                    val args = diagramArguments(root, refusal)
                    assertEquals(2, runKscopeWith(args).statusCode, args.toString())
                }
                assertEquals(refusals.size, server.requestCount)
            }
        }
    }
}
