package io.github.krosstack.kscope.cli.service.launch

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import io.github.krosstack.kscope.cli.service.entry.inTemporaryDirectory
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemSpawnerDetachTest {
    @Test
    fun `the detached child survives its launcher and console exiting`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        inTemporaryDirectory("detached with space") { directory ->
            val arguments = directory.resolve("launcher.args")
            Files.writeString(arguments, LaunchArguments.render(javaCommand().drop(1) + listOf("launch", "$directory")))
            val shell = ProcessBuilder(
                "cmd.exe", "/d", "/c", "start", "\"\"", "/min", "/wait", javaCommand().first(), "@$arguments",
            ).redirectErrorStream(true).redirectOutput(directory.resolve("launcher.log").toFile()).start()
            try {
                assertTrue(shell.waitFor(20, TimeUnit.SECONDS), "launcher did not exit")
                assertEquals(0, shell.exitValue(), Files.readString(directory.resolve("launcher.log")))
                val launcher = Files.readString(directory.resolve("launcher.pid")).toLong()
                val child = Files.readString(directory.resolve("child.pid")).toLong()
                val console = Files.readAllLines(directory.resolve("console.pids")).map(String::toLong)
                assertTrue(launcher in console, "launcher must belong to its console")
                assertFalse(child in console, "detached child must leave the launcher console")
                assertEquals(
                    listOf(false, true),
                    listOf(alive(launcher), alive(child)),
                    "the launcher must exit while its detached child stays alive",
                )
            } finally {
                shell.destroyForcibly()
                listOf("child.pid", "launcher.pid").forEach { name ->
                    val file = directory.resolve(name)
                    if (Files.exists(file)) terminate(Files.readString(file).toLong())
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(arguments: Array<String>) {
            val directory = Path.of(arguments[1])
            if (arguments[0] == "sleep") {
                CountDownLatch(1).await(1, TimeUnit.MINUTES)
            } else {
                Files.writeString(directory.resolve("launcher.pid"), ProcessHandle.current().pid().toString())
                val command = javaCommand() + listOf("sleep", "$directory")
                SystemSpawner(windowsStart = { words, workingDirectory ->
                    WindowsProcessCreation.start(words, workingDirectory).also { pid ->
                        Files.writeString(directory.resolve("child.pid"), pid.toString())
                    }
                }).spawn(ServiceLaunch(Path.of(command.first()), command.drop(1)), directory)
                Files.write(directory.resolve("console.pids"), consoleProcessIds().map(Long::toString))
            }
        }

        private fun consoleProcessIds(): List<Long> {
            val capacity = 64
            Memory(capacity.toLong() * Int.SIZE_BYTES).use { buffer ->
                val count = NativeLibrary.getInstance("kernel32")
                    .getFunction("GetConsoleProcessList", Function.ALT_CONVENTION)
                    .invokeInt(arrayOf(buffer, capacity))
                check(count in 1..capacity) { "GetConsoleProcessList returned $count process ids" }
                return buffer.getIntArray(0, count).map(Integer::toUnsignedLong).sorted()
            }
        }

        private fun javaCommand(): List<String> {
            val types = listOf(
                SystemSpawnerDetachTest::class.java,
                SystemSpawner::class.java,
                Unit::class.java,
                Native::class.java,
            )
            val classPath = types.map { Path.of(it.protectionDomain.codeSource.location.toURI()) }
                .distinct().joinToString(File.pathSeparator)
            return listOf(
                Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-cp",
                classPath,
                SystemSpawnerDetachTest::class.java.name,
            )
        }

        private fun alive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

        private fun terminate(pid: Long) {
            ProcessHandle.of(pid).ifPresent { process ->
                process.destroyForcibly()
                process.onExit().get(10, TimeUnit.SECONDS)
            }
        }
    }
}
