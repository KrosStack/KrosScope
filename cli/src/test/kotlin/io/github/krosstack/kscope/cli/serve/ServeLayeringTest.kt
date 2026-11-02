package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.resolve
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse

class ServeLayeringTest {
    @Test
    fun `service does not depend directly on the store`() {
        val distribution = Path.of(checkNotNull(System.getProperty("kscope.distribution")))
        val root = distribution.parent.parent.parent.parent
        val build = Files.readString(root.resolve("service/build.gradle.kts"))
        val memory = Files.readString(
            root.resolve("service/src/main/kotlin/io/github/krosstack/kscope/service/IndexMemory.kt"),
        )
        assertFalse(build.contains("project(\":core:store\")"))
        assertFalse(memory.contains("io.github.krosstack.kscope.store"))
    }
}
