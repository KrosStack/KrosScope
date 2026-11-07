package io.github.krosstack.kscope.cli.service.launch

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemSpawnerCommandTest {
    private val command = listOf("java", "-Dpath=with space", "-cp", "lib/*", "Main", "", "a\"b", "tail\\")

    @Test
    fun `Unix uses setsid when it is available`() {
        val setsid = Path.of("tools", "setsid")
        listOf("Linux", "Mac OS X").forEach { osName ->
            val starter = SystemSpawner(
                osName,
                setsid = { setsid },
                nohup = { error("setsid must take precedence over nohup") },
            )
            assertEquals(listOf(setsid.toString()) + command, starter.detachedCommand(command))
        }
    }

    @Test
    fun `Unix uses nohup through a shell when setsid is absent`() {
        val nohup = Path.of("tools", "nohup")
        listOf("Linux", "Mac OS X").forEach { osName ->
            val starter = SystemSpawner(osName, setsid = { null }, nohup = { nohup })
            assertEquals(
                listOf("/bin/sh", "-c", "exec \"\$@\" </dev/null >/dev/null 2>&1", "sh", nohup.toString()) + command,
                starter.detachedCommand(command),
            )
        }
    }

    @Test
    fun `Unix uses a shell with positional arguments when setsid and nohup are absent`() {
        listOf("Linux", "Mac OS X").forEach { osName ->
            val starter = SystemSpawner(osName, setsid = { null }, nohup = { null })
            assertEquals(
                listOf("/bin/sh", "-c", "exec \"\$@\" </dev/null >/dev/null 2>&1", "sh") + command,
                starter.detachedCommand(command),
            )
        }
    }

    @Test
    fun `Windows sends the original command and directory to native creation`() {
        val directory = Path.of("service")
        var received: Pair<List<String>, Path>? = null
        val starter = SystemSpawner(
            osName = "Windows 11",
            setsid = { error("Windows must not look for setsid") },
            nohup = { error("Windows must not look for nohup") },
            windowsStart = { words, workingDirectory ->
                received = words to workingDirectory
                4711L
            },
        )

        starter.spawn(ServiceLaunch(Path.of(command.first()), command.drop(1)), directory)
        assertEquals(command to directory, received)
    }
}
