package io.github.krosstack.kscope.cli.serve.semantic

import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.serve.ServeOutput
import io.github.krosstack.kscope.cli.serve.ServeRequest
import io.github.krosstack.kscope.cli.serve.ServeRequests
import io.github.krosstack.kscope.cli.serve.ServeResponse
import io.github.krosstack.kscope.cli.serve.ServeServer
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.serve.ServeWire
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.serve.withTestServer
import io.github.krosstack.kscope.cli.service.host.ServiceSettings
import io.github.krosstack.kscope.cli.service.host.SingleScanRunner
import io.github.krosstack.kscope.cli.service.host.awaitServed
import io.github.krosstack.kscope.cli.service.host.quietWatch
import io.github.krosstack.kscope.cli.service.host.withTestService
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class ServedSemanticQueryTest {
    private val json = listOf("--format", "json", "--quiet")

    @TempDir
    lateinit var directory: Path

    @Test
    fun `served and one shot ON agree on the same admitted snapshot`() {
        val fixture = SemanticQueryFixture(directory)
        val analysis = fixture.local(fixture.analyze + json)
        val semantic = parseJson(analysis.stdout).getValue("semantic").jsonObject
        assertEquals("used", semantic["state"]?.jsonPrimitive?.content)
        val context = fixture.local(fixture.context + json)
        assertNotEquals(fixture.structural("json"), context.stdout)
        for (hosted in listOf(false, true)) {
            withServer(fixture, hosted) { server ->
                for ((command, expected) in listOf(fixture.analyze to analysis, fixture.context to context)) {
                    val actual = server.query(command + json)
                    assertEquals(0, actual.exitCode, actual.stderr)
                    assertEquals(expected.stdout, actual.stdout)
                }
            }
        }
    }

    @Test
    fun `alternating ON OFF ON requests never share a mode`() {
        val fixture = SemanticQueryFixture(directory)
        for (hosted in listOf(false, true)) {
            withServer(fixture, hosted) { server ->
                assertAlternating(fixture, server, fixture.context)
                assertAlternating(fixture, server, fixture.analyze)
            }
        }
    }

    @Test
    fun `release closes query resources and next ON query can reopen`() {
        val fixture = SemanticQueryFixture(directory)
        for (hosted in listOf(false, true)) {
            val runner = if (hosted) SingleScanRunner(fixture) else fixture
            ServeRequests(fixture.root.toRealPath(), {}, runner, {}, ServeSettings(hosted = hosted)).use { requests ->
                val before = execute(requests, fixture)
                assertNotEquals(fixture.structural("json"), before)
                assertEquals(1, fixture.fake.workersStarted - fixture.fake.workersClosed)
                val leases = fixture.root.resolve(".kscope/embeddings-leases")
                assertEquals(1L, Files.list(leases).use { it.count() })
                requests.release()
                assertFalse(requests.loaded)
                assertEquals(fixture.fake.workersStarted, fixture.fake.workersClosed)
                assertEquals(0L, Files.list(leases).use { it.count() })
                val starts = fixture.fake.workersStarted
                assertEquals(fixture.structural("json"), execute(requests, fixture, listOf("--ml", "off")))
                assertEquals(starts, fixture.fake.workersStarted)
                assertEquals(before, execute(requests, fixture))
                assertEquals(starts + 1, fixture.fake.workersStarted)
            }
            assertEquals(fixture.fake.workersStarted, fixture.fake.workersClosed)
        }
    }

    @Test
    fun `ML OFF does not start a query worker`() {
        val fixture = SemanticQueryFixture(directory)
        val starts = fixture.fake.workersStarted
        val queries = fixture.fake.queriesEmbedded
        val local = fixture.local(listOf("--no-ml") + fixture.context + json)
        assertEquals(0, local.statusCode, local.output)
        assertEquals(fixture.structural("json"), local.stdout)
        for (hosted in listOf(false, true)) {
            withServer(fixture, hosted) { server ->
                assertEquals(fixture.structural("json"), server.query(fixture.context + json + "--no-ml").stdout)
            }
        }
        assertEquals(starts, fixture.fake.workersStarted)
        assertEquals(queries, fixture.fake.queriesEmbedded)
    }

    private fun assertAlternating(fixture: SemanticQueryFixture, server: ServeServer, command: List<String>) {
        val on = server.query(command + json)
        val queries = fixture.fake.queriesEmbedded
        val off = server.query(listOf("--no-ml") + command + json + listOf("--ml", "on"))
        val localOff = fixture.local(listOf("--no-ml") + command + json)
        assertEquals(0, off.exitCode, off.stderr)
        assertNotEquals(on.stdout, off.stdout)
        assertEquals(localOff.stdout, off.stdout)
        if (command == fixture.context) assertEquals(fixture.structural("json"), off.stdout)
        assertEquals(queries, fixture.fake.queriesEmbedded)
        assertEquals(on.stdout, server.query(command + json).stdout)
    }

    private fun execute(
        requests: ServeRequests,
        fixture: SemanticQueryFixture,
        flags: List<String> = emptyList(),
    ): String {
        val output = StringWriter()
        val request = ServeRequest(
            arguments = fixture.context + json + flags,
            directory = fixture.root.toString(),
        )
        assertEquals(0, requests.execute(request, ServeOutput(output.buffered())), output.toString())
        return output.toString().lineSequence()
            .filter { it.isNotBlank() }
            .map { ServeWire.json.decodeFromString<ServeResponse>(it) }
            .filter { it.stream == "stdout" }.joinToString("") { it.text }
    }

    private fun withServer(fixture: SemanticQueryFixture, hosted: Boolean, block: (ServeServer) -> Unit) {
        if (hosted) {
            val settings = ServiceSettings(
                serve = ServeSettings(watch = quietWatch()),
                scanRunner = fixture,
                lookMillis = Long.MAX_VALUE,
            )
            withTestService(directory.resolve("service"), listOf(fixture.root), settings) {
                block(it.awaitServed(fixture.root))
            }
        } else {
            withTestServer(fixture.root, scanRunner = fixture, block = block)
        }
    }
}
