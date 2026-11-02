package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.analyzer.jni.libraries.LibraryTargetProducer
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class SingleArtifactProducerTest {
    private val unreadable = object : WorkspaceFiles {
        override fun read(ref: ArtifactRef): ByteArray = throw IOException("the disk is gone")

        override fun absolutePath(ref: ArtifactRef): Path = Path.of(ref.path.value)
    }

    private fun notesOf(producer: FactProducer, path: String): List<Fact> {
        val workspace = NativeWorkspace(mapOf(path to "content\n"))
        val artifact = workspace.artifacts.single()
        val owner = OwnershipUnit(OwnershipKind.ARTIFACT, path)
        val sourceSet = NativeModel.model.sourceSets.single()
        val unit = AnalysisUnit(owner, listOf(artifact), sourceSet, NativeModel.model.modules.single())
        val batches = mutableListOf<FactBatch>()
        producer.produce(ProduceRequest(listOf(unit), NativeModel.model, unreadable, emptyList())) { batches += it }
        return batches.single().facts
    }

    @Test
    fun `a file that cannot be read gives one note and no symbol, whatever it is read for`() {
        listOf(
            NativeFunctionProducer() to "bridge/src/main/cpp/checksum.c",
            LibraryTargetProducer() to "bridge/CMakeLists.txt",
        ).forEach { (producer, path) ->
            val note = (notesOf(producer, path).single() as Fact.OfDiagnostic).value
            assertEquals("jni.unreadable", note.code.value, path)
            assertEquals(DiagnosticSeverity.INFO, note.severity, path)
            assertEquals("$path: it could not be read: the disk is gone.", note.message)
        }
    }
}
