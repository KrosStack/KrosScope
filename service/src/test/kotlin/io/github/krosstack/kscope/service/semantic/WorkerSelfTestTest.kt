package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.InstalledComponent
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkerSelfTestTest {
    @Test
    fun `self-test completes only after the component lock is released`(@TempDir root: Path) {
        install(root, "semantic", mapOf("lib/placeholder" to ""))
        install(root, "semantic-model", mapOf("model/model.json" to "{}"))
        val directory = ComponentDirectory(root)
        val ordering = Collections.synchronizedList(mutableListOf<String>())
        val pending = FutureTask {
            WorkerSelfTest(directory).run()
            ordering.add("completed")
        }
        val testing = Thread(pending)
        try {
            directory.exclusively("semantic") {
                testing.start()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (!pending.isDone && testing.stackTrace.none { it.className.contains("ReentrantLock") }) {
                    check(System.nanoTime() < deadline) { "Self-test did not reach the component lock" }
                    Thread.onSpinWait()
                }
                ordering.add("released")
            }
            pending.get(10, TimeUnit.SECONDS)
            assertEquals(listOf("released", "completed"), ordering)
        } finally {
            testing.join(TimeUnit.SECONDS.toMillis(10))
        }
    }

    @Test
    fun `oversized startup and cause lines persist the cause class within the diagnostic bound`(@TempDir root: Path) {
        val source = root.resolve("Main.java")
        Files.writeString(
            source,
            """
            package io.github.krosstack.kscope.semantic.worker;
            public class Main {
                public static void main(String[] args) {
                    System.err.println("Startup failure: " + "x".repeat(4096));
                    System.err.println("Caused by: IllegalStateException: " + "y".repeat(4096));
                }
            }
            """.trimIndent(),
        )
        check(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", root.toString(), source.toString()) == 0)
        install(root, "semantic", mapOf("lib/placeholder" to ""))
        install(
            root,
            "semantic-model",
            mapOf(
                "model/model.json" to
                    """{"name":"fake","revision":"test","dimensions":2,"quantization":"int8-dynamic"}""",
            ),
        )
        val library = Files.createDirectories(root.resolve("semantic/1/lib"))
        JarOutputStream(Files.newOutputStream(library.resolve("failure.jar"))).use { jar ->
            val resource = "io/github/krosstack/kscope/semantic/worker/Main.class"
            jar.putNextEntry(JarEntry(resource).apply { time = 0 })
            Files.copy(root.resolve(resource), jar)
            jar.closeEntry()
        }
        val directory = ComponentDirectory(root)
        WorkerSelfTest(directory).run()
        val saved = requireNotNull(directory.installed("semantic")).problem.orEmpty()
        val diagnostic = saved.substringAfter("Semantic process exited\n")
            .substringBefore(". ${WorkerSelfTest.JDK_REQUIREMENT}")
        assertTrue(diagnostic.length <= 2048 && diagnostic.contains("Caused by: IllegalStateException"))
    }

    @Test
    fun `no installed worker leaves the self-test absent`(@TempDir root: Path) {
        assertNull(WorkerSelfTest(ComponentDirectory(root)).run())
    }

    @Test
    fun `an installed worker without its model does not start`(@TempDir root: Path) {
        val worker = Files.createDirectories(root.resolve("semantic/1"))
        Files.createDirectory(worker.resolve("lib"))
        InstalledComponent.write(InstalledComponent("semantic", "1", worker, listOf("bundle.zip")))
        assertNull(WorkerSelfTest(ComponentDirectory(root)).run())
    }

    @Test
    fun `an unrelated component with a library directory is not the worker`(@TempDir root: Path) {
        install(root, "aaa-library", mapOf("lib/placeholder" to ""))
        install(root, "semantic-model", mapOf("model/model.json" to "{}"))
        assertNull(WorkerSelfTest(ComponentDirectory(root)).run())
    }

    @Test
    fun `an unrelated component with a descriptor is not the model`(@TempDir root: Path) {
        install(root, "semantic", mapOf("lib/placeholder" to ""))
        install(root, "aaa-model", mapOf("model/model.json" to "{}"))
        assertNull(WorkerSelfTest(ComponentDirectory(root)).run())
    }

    @Test
    fun `a descriptor missing a required key saves a failure naming the field`(@TempDir root: Path) {
        install(root, "semantic", mapOf("lib/placeholder" to ""))
        install(
            root,
            "semantic-model",
            mapOf("model/model.json" to """{"revision":"test","dimensions":2,"quantization":"int8"}"""),
        )
        val directory = ComponentDirectory(root)
        WorkerSelfTest(directory).run()
        assertTrue(requireNotNull(directory.installed("semantic")).problem.orEmpty().contains("model field: name"))
    }

    @Test
    fun `a malformed descriptor string field returns a failure naming the field`(@TempDir root: Path) {
        install(root, "semantic", mapOf("lib/placeholder" to ""))
        install(root, "semantic-model", mapOf("model/model.json" to """{"name":42}"""))
        assertTrue(WorkerSelfTest(ComponentDirectory(root)).run()?.detail.orEmpty().contains("model field: name"))
    }

    @Test
    fun `a malformed descriptor dimension returns a failure naming the field`(@TempDir root: Path) {
        install(root, "semantic", mapOf("lib/placeholder" to ""))
        install(
            root,
            "semantic-model",
            mapOf("model/model.json" to """{"name":"fake","revision":"test","dimensions":{}}"""),
        )
        assertTrue(WorkerSelfTest(ComponentDirectory(root)).run()?.detail.orEmpty().contains("model field: dimensions"))
    }

    @Test
    fun `an installed malformed model takes precedence over a bundled model`(@TempDir root: Path) {
        install(
            root,
            "semantic",
            mapOf(
                "lib/placeholder" to "",
                "model/model.json" to
                    """{"name":"fake","revision":"test","dimensions":2,"quantization":"int8-dynamic"}""",
            ),
        )
        install(root, "semantic-model", mapOf("model/model.json" to "not json"))
        val directory = ComponentDirectory(root)
        val result = WorkerSelfTest(directory).run(directory.installed("semantic-model"))
        assertTrue(result?.available == false && result.detail.contains("model.json"))
    }

    private fun install(root: Path, name: String, files: Map<String, String>) {
        val target = Files.createDirectories(root.resolve("$name/1"))
        files.forEach { (path, content) ->
            val file = target.resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, content)
        }
        InstalledComponent.write(InstalledComponent(name, "1", target, files.keys.toList()))
    }
}
