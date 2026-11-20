package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.service.components.ComponentPlatform
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ComponentLayoutTest {
    @Test
    fun `default cpu command and int8 identity remain compatible with components 2026_11`(@TempDir directory: Path) {
        val runtime = Files.createDirectories(directory.resolve("semantic/2026.11"))
        Files.createDirectory(runtime.resolve("lib"))
        val model = Files.createDirectories(directory.resolve("semantic-model/2026.11/model"))
        Files.writeString(
            model.resolve("model.json"),
            """{"name":"nomic-ai/CodeRankEmbed","revision":"3c4b60807d71f79b43f3c4363786d9493691f8b1",
                "dimensions":768,"quantization":"dynamic-int8-per-channel-reduce-range","chunkBuilderVersion":1}""",
        )
        val previous = System.clearProperty("kscope.semantic.device")
        try {
            val jdk = directory.resolve("jdk")
            val layout = ComponentLayout(runtime, model, jdk)
            val platform = ComponentPlatform.current(System.getProperty("os.name"), System.getProperty("os.arch"))
            assertEquals(
                listOf(
                    jdk.resolve("bin/java").toString(),
                    "-Donnxruntime.native.path=${runtime.resolve("native").resolve(platform)}",
                    "-cp",
                    "${runtime.resolve("lib")}/*",
                    "io.github.krosstack.kscope.semantic.worker.Main",
                    model.toString(),
                ),
                layout.command(),
            )
            assertEquals(
                ModelIdentity(
                    "nomic-ai/CodeRankEmbed",
                    "3c4b60807d71f79b43f3c4363786d9493691f8b1",
                    768,
                    "dynamic-int8-per-channel-reduce-range",
                    1,
                ),
                layout.identity,
            )
        } finally {
            if (previous != null) System.setProperty("kscope.semantic.device", previous)
        }
    }

    @Test
    fun `cuda setting adds the device argument and separates float identity`(@TempDir directory: Path) {
        Files.createDirectory(directory.resolve("lib"))
        Files.writeString(
            directory.resolve("model.json"),
            """{"name":"test","revision":"revision","dimensions":2,"quantization":"fp32"}""",
        )
        val cpu = ComponentLayout(directory, directory, device = "cpu")
        val previous = System.getProperty("kscope.semantic.device")
        try {
            System.setProperty("kscope.semantic.device", "cuda")
            val cuda = ComponentLayout(directory, directory)
            assertEquals(cpu.command() + "cuda", cuda.command())
            assertNotEquals(cpu.identity, cuda.identity)
            assertTrue(cuda.identity.revision.contains("pipeline-v1:cuda:fp32:"))
        } finally {
            if (previous == null) {
                System.clearProperty("kscope.semantic.device")
            } else {
                System.setProperty("kscope.semantic.device", previous)
            }
        }
    }
}
