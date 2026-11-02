package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.serve.ServeClient
import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.serve.ServeServer
import io.github.krosstack.kscope.cli.serve.ServerAlreadyRunning
import java.io.Writer
import java.nio.file.Path

internal class McpHost private constructor(private val server: ServeServer?, private val thread: Thread?) :
    AutoCloseable {
    private val hook = Thread(::close, "kscope-mcp-shutdown")

    private fun start() {
        if (server == null) return
        Runtime.getRuntime().addShutdownHook(hook)
        thread?.start()
    }

    override fun close() {
        server?.requestStop()
        thread?.join()
        server?.close()
        runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
    }

    companion object {
        fun open(workspace: Path, settings: McpSettings, error: Writer): McpHost {
            val server = if (settings.noServer) null else openServer(workspace, settings, error)
            val thread = server?.let { opened ->
                Thread({
                    runCatching { opened.run() }.onFailure { failure ->
                        error.write("error: ${failure.message.orEmpty()}\n")
                        error.flush()
                    }
                }, "kscope-mcp-serve")
            }
            return McpHost(server, thread).also { it.start() }
        }

        private fun openServer(workspace: Path, settings: McpSettings, error: Writer): ServeServer? {
            val metadata = ServeMetadata.read(workspace)
            if (metadata != null && ServeClient.alive(metadata)) return null
            return runCatching { ServeServer.open(workspace, settings.serve, settings.scanRunner) }
                .onSuccess {
                    val watching = if (settings.serve.watchEnabled) " Watching the workspace." else ""
                    error.write("Serving $workspace until standard input closes.$watching\n")
                    error.flush()
                }.getOrElse { failure ->
                    if (failure !is ServerAlreadyRunning) {
                        error.write("error: ${failure.message.orEmpty()}\n")
                        error.flush()
                    }
                    null
                }
        }
    }
}
