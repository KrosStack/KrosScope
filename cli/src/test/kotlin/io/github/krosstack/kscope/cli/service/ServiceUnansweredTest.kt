package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.home.ServiceFile
import io.github.krosstack.kscope.cli.service.home.ServiceFileReading
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceUnansweredTest {
    private val unanswered = "The service did not answer; see `kscope service status`."

    private fun <T> withSilentService(home: Path, block: () -> T): T = SilentService().use { silent ->
        silent.announceIn(ServiceDirectory(home))
        block()
    }

    private fun kept(home: Path): List<String> =
        (ServiceFile(home.resolve("service.toml")).read() as ServiceFileReading.Read).workspaces

    @Test
    fun `add writes the file and then says that a running service did not answer`() = inWorkspace { root ->
        inServiceHome { home ->
            withSilentService(home) {
                val result = runService(home, "add", "--workspace", root.toString())

                assertEquals(1, result.statusCode, result.output)
                assertEquals(listOf("Added ${WorkspacePaths.real(root)}."), result.stdoutLines())
                assertEquals(unanswered, result.stderr.trimEnd())
                assertEquals(listOf(WorkspacePaths.key(WorkspacePaths.real(root))), kept(home))
            }
        }
    }

    @Test
    fun `remove writes the file and then says that a running service did not answer`() = inWorkspace { root ->
        inServiceHome { home ->
            ServiceFile(home.resolve("service.toml")).write(listOf(WorkspacePaths.key(WorkspacePaths.real(root))))
            withSilentService(home) {
                val result = runService(home, "remove", "--workspace", root.toString())

                assertEquals(1, result.statusCode, result.output)
                assertEquals(listOf("Removed ${WorkspacePaths.real(root)}."), result.stdoutLines())
                assertEquals(unanswered, result.stderr.trimEnd())
                assertEquals(emptyList(), kept(home))
            }
        }
    }

    @Test
    fun `stop does not say that a service which is alive does not run`() = inServiceHome { home ->
        withSilentService(home) {
            val result = runService(home, "stop")

            assertEquals(1, result.statusCode, result.output)
            assertEquals("", result.stdout)
            assertEquals(unanswered, result.stderr.trimEnd())
        }
    }
}
