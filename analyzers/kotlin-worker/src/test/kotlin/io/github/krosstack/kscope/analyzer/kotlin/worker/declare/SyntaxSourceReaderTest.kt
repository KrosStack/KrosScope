package io.github.krosstack.kscope.analyzer.kotlin.worker.declare

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.ScriptParser
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerModule
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class SyntaxSourceReaderTest {
    private fun fileAt(path: Path): WorkerFile {
        val ref = ArtifactRef(RepositoryId("r"), RepoPath(path.fileName.toString()))
        return WorkerFile(ref, path.toString(), ContentHash("h"))
    }

    private fun moduleOf(vararg sets: List<WorkerFile>): WorkerModule {
        val template = SampleWorkspace.sourceSet("m", "main", "shapes/main")
        val sourceSets = sets.mapIndexed { index, files ->
            template.copy(sourceSet = SourceSetId("r//:m@set$index"), files = files)
        }
        return SampleWorkspace.module("m", Platform.JVM).copy(sourceSets = sourceSets)
    }

    @Test
    fun `file extensions are matched without regard to case`(@TempDir root: Path) {
        val kotlin = Files.writeString(root.resolve("Main.KT"), "class Main\n")
        val java = Files.writeString(root.resolve("Legacy.JAVA"), "class Legacy {}\n")
        val module = moduleOf(listOf(fileAt(kotlin), fileAt(java)))
        val read = ScriptParser().use { SyntaxSourceReader(it).sourceSetsOf(listOf(module)).toList() }
        assertEquals(2, read.single().files.size)
        assertEquals(emptyList(), read.single().missing)
    }

    @Test
    fun `source sets are parsed only when their batch is asked for`(@TempDir root: Path) {
        val first = Files.writeString(root.resolve("A.kt"), "class A\n")
        val second = Files.writeString(root.resolve("B.kt"), "class B\n")
        val module = moduleOf(listOf(fileAt(first)), listOf(fileAt(second)))
        ScriptParser().use { parser ->
            val iterator = SyntaxSourceReader(parser).sourceSetsOf(listOf(module)).iterator()
            iterator.next()
            Files.delete(second)
            assertEquals(1, iterator.next().missing.size)
        }
    }
}
