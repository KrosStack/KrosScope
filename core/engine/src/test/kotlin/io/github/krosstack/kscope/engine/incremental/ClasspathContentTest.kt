package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.CoreCapabilities
import io.github.krosstack.kscope.engine.FakeTrustedProvider
import io.github.krosstack.kscope.engine.RecordingProducer
import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.model.OwnershipKind
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertTrue

/** The state of a classpath entry decides whether a source set is analysed again, not only where the entry lies. */
class ClasspathContentTest {
    private val trusted = FakeTrustedProvider()
    private val bySourceSet = RecordingProducer("by-source-set", granularity = OwnershipKind.SOURCE_SET)

    private fun jar(libs: Path): Path = libs.resolve("vendor.jar").also { Files.writeString(it, "one") }

    private fun classes(libs: Path): Path {
        val directory = Files.createDirectories(libs.resolve("classes"))
        Files.writeString(Files.createDirectories(directory.resolve("vendor")).resolve("Base.class"), "one")
        return directory
    }

    private fun withClasspathEntry(prepare: (Path) -> Path, block: (ScanEngine, Path) -> Unit) {
        TestWorkspace("gradle-multi").use { workspace ->
            val entry = prepare(Files.createDirectories(workspace.root.resolveSibling("libs")))
            trusted.classpaths = { model ->
                val sourceSet = model.sourceSets.first { it.id.value.endsWith(":core@commonMain") }
                mapOf(sourceSet.id to listOf(entry.toString()))
            }
            val registry = coreRegistry(bySourceSet, providers = CoreCapabilities.buildModelProviders() + trusted)
            workspace.engine(registry, trusted = true).use { engine ->
                engine.update()
                bySourceSet.calls.clear()
                block(engine, entry)
            }
        }
    }

    private fun rewrite(file: Path, text: String, laterBy: Duration = Duration.ZERO) {
        val modified = Files.getLastModifiedTime(file)
        Files.writeString(file, text)
        Files.setLastModifiedTime(file, FileTime.from(modified.toInstant().plus(laterBy)))
    }

    @Test
    fun `an unchanged classpath binary does not redo the analysis`() {
        withClasspathEntry(::jar) { engine, _ ->
            engine.update()
            assertTrue(bySourceSet.calls.isEmpty())
        }
    }

    @Test
    fun `a classpath binary replaced at the same path with another size redoes the analysis`() {
        withClasspathEntry(::jar) { engine, jar ->
            rewrite(jar, "longer")
            engine.update()
            assertTrue(bySourceSet.calls.isNotEmpty())
        }
    }

    @Test
    fun `a classpath binary replaced at the same path with another time redoes the analysis`() {
        withClasspathEntry(::jar) { engine, jar ->
            rewrite(jar, "two", Duration.ofMinutes(1))
            engine.update()
            assertTrue(bySourceSet.calls.isNotEmpty())
        }
    }

    @Test
    fun `a classpath binary that disappears redoes the analysis`() {
        withClasspathEntry(::jar) { engine, jar ->
            Files.delete(jar)
            engine.update()
            assertTrue(bySourceSet.calls.isNotEmpty())
        }
    }

    @Test
    fun `a file changed inside a classpath directory redoes the analysis`() {
        withClasspathEntry(::classes) { engine, directory ->
            rewrite(directory.resolve("vendor/Base.class"), "longer")
            engine.update()
            assertTrue(bySourceSet.calls.isNotEmpty())
        }
    }

    @Test
    fun `a file added to a classpath directory redoes the analysis`() {
        withClasspathEntry(::classes) { engine, directory ->
            Files.writeString(directory.resolve("vendor/Other.class"), "one")
            engine.update()
            assertTrue(bySourceSet.calls.isNotEmpty())
        }
    }

    @Test
    fun `an untouched classpath directory does not redo the analysis`() {
        withClasspathEntry(::classes) { engine, _ ->
            engine.update()
            assertTrue(bySourceSet.calls.isEmpty())
        }
    }
}
