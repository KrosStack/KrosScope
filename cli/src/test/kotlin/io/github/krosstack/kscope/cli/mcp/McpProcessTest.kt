package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.serve.ServeLock
import io.github.krosstack.kscope.cli.serve.ServeMetadata
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpProcessTest {
    @Test
    fun `the distribution writes only rpc through scans and failed tools in both eras`() {
        val installed = Path.of(checkNotNull(System.getProperty("kscope.distribution")))
        val windows = System.getProperty("os.name").lowercase().contains("windows")
        val launcher = installed.resolve("bin").resolve(if (windows) "kscope.bat" else "kscope")
        for (modern in listOf(false, true)) {
            for (noServer in listOf(false, true)) verifyProcess(launcher, modern, noServer)
        }
    }

    private fun verifyProcess(launcher: Path, modern: Boolean, noServer: Boolean) = inWorkspace { root ->
        val args = listOf("--workspace", root.toString()) + if (noServer) listOf("--no-server") else emptyList()
        val builder = ProcessBuilder(listOf(launcher.toString()) + args + "mcp")
        builder.environment()["JAVA_HOME"] = System.getProperty("java.home")
        builder.environment().remove("KSCOPE_NO_SERVER")
        val process = builder.start()
        val (stdout, stderr) = session(process, modern)
        val replies = stdout.lines().dropLast(1).map { line ->
            parseJson(line).also { assertEquals(line, it.toString()) }
        }
        assertEquals(5, replies.size)
        replies.forEach { assertEquals(setOf("jsonrpc", "id", "result"), it.keys) }
        assertEquals("true", replies[2].getValue("result").jsonObject.getValue("isError").toString())
        assertEquals("false", replies[3].getValue("result").jsonObject.getValue("isError").toString())
        assertEquals("false", replies[4].getValue("result").jsonObject.getValue("isError").toString())
        assertEquals(0, process.exitValue())
        assertEquals(!noServer, stderr.contains("Serving"))
        assertTrue(stderr.contains("error:"))
        assertFalse(Files.exists(ServeMetadata.file(root)))
        ServeLock.acquire(root).close()
    }

    private fun session(process: Process, modern: Boolean): Pair<String, String> {
        val executor = Executors.newFixedThreadPool(2)
        try {
            val output = executor.submit<String> { process.inputStream.readAllBytes().decodeToString() }
            val error = executor.submit<String> { process.errorStream.readAllBytes().decodeToString() }
            val request = if (modern) ::modernRpc else { method, params -> rpc(method, params) }
            val lines = listOf(
                if (modern) rpc("server/discover") else initialize(),
                request("tools/list", buildJsonObject {}),
                request("tools/call", callParams("info")),
                request("tools/call", callParams("scan")),
                request("tools/call", callParams("info")),
            )
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(lines.joinToString("\n") + "\n") }
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "MCP did not finish after EOF")
            return output.get(5, TimeUnit.SECONDS) to error.get(5, TimeUnit.SECONDS)
        } finally {
            process.descendants().use { children -> children.forEach { it.destroyForcibly() } }
            if (process.isAlive) process.destroyForcibly()
            executor.shutdownNow()
        }
    }
}
