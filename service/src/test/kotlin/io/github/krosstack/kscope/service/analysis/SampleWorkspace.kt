package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.wiring.DefaultCapabilities
import java.nio.file.Files
import java.nio.file.Path

/** The linkers run again after any change, so they are part of every count of units. */
internal val linkerCount: Int = DefaultCapabilities.registry(Path.of("index")).linkers.size

/** The history producer has one unit, which is produced by a scan from scratch and by a new commit or file path. */
internal const val HISTORY_UNIT = 1

/** A checked-in sample tree copied to a temporary directory, so that tests can change it. */
internal class SampleWorkspace(sample: String) : AutoCloseable {
    val root: Path = Files.createTempDirectory("kscope-sample")

    init {
        val source = SampleWorkspace::class.java.getResource("/samples/$sample")
        val sourceRoot = Path.of(checkNotNull(source) { "Missing sample $sample" }.toURI())
        Files.walk(sourceRoot).use { paths ->
            paths.forEach { path ->
                val target = root.resolve(sourceRoot.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(target) else Files.copy(path, target)
            }
        }
    }

    fun write(relativePath: String, text: String) {
        val target = root.resolve(relativePath)
        Files.createDirectories(target.parent)
        Files.writeString(target, text)
    }

    override fun close() {
        TemporaryTree.delete(root)
    }
}
