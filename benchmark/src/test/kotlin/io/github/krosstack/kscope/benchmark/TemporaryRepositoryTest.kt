package io.github.krosstack.kscope.benchmark

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest

open class TemporaryRepositoryTest(prefix: String) {
    protected val base: Path = Files.createTempDirectory(prefix)

    @AfterTest
    fun cleanUp() {
        TemporaryTree.delete(base)
    }
}
