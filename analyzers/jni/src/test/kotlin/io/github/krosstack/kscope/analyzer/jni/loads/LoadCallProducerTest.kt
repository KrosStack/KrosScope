package io.github.krosstack.kscope.analyzer.jni.loads

import io.github.krosstack.kscope.analyzer.jni.NativeModel
import io.github.krosstack.kscope.analyzer.jni.NativeWorkspace
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

private const val CLIENT = "bridge/src/main/kotlin/sample/NativeChecksum.kt"
private const val HELPER = "bridge/src/main/java/sample/Helper.java"
private const val PLAIN = "bridge/src/main/kotlin/sample/Plain.kt"

private val CLIENT_KOTLIN = """
    package sample

    object NativeChecksum {
        init {
            System.loadLibrary("checksum")
        }
    }
""".trimIndent()

private val HELPER_JAVA = """
    class Helper {
        static {
            System.load("/opt/libhelper.so");
            System.loadLibrary("checksum");
            System.loadLibrary("checksum");
        }
    }
""".trimIndent()

private const val PLAIN_KOTLIN = "package sample\n\nclass Plain\n"

class LoadCallProducerTest {
    private val workspace = NativeWorkspace(
        mapOf(CLIENT to CLIENT_KOTLIN, HELPER to HELPER_JAVA, PLAIN to PLAIN_KOTLIN),
    )

    private fun symbols() = workspace.symbols(LoadCallProducer())

    @Test
    fun `a call that loads a library is a textual contract symbol named after the library`() {
        val load = symbols().single { it.declaration?.artifact?.path?.value == CLIENT }
        assertEquals("jvm.load-library", load.kind.id)
        assertEquals(SymbolCategory.CONTRACT, load.kind.category)
        assertEquals("checksum", load.name)
        assertEquals("System.loadLibrary(\"checksum\")", load.signature)
        assertEquals(EvidenceClass.TEXTUAL, load.evidenceClass)
        assertEquals(Certainty.EXACT, load.certainty)
        assertEquals(SymbolOrigin.Source, load.origin)
        assertEquals(mapOf("jvm.load-call" to "System.loadLibrary"), load.attributes)
        assertEquals(5, load.declaration?.range?.startLine)
        assertEquals(5, load.nameLocation?.range?.startLine)
        assertEquals(NativeModel.sourceSet, load.scope.sourceSet)
    }

    @Test
    fun `the identity names the module, the file and the library`() {
        val load = symbols().single { it.declaration?.artifact?.path?.value == CLIENT }
        val identity = (load.id.identity() as ParsedSymbolId.Valid).identity
        assertEquals(":bridge", identity.unit.substringAfter("//"))
        assertEquals("`$CLIENT`:load-library:checksum.", identity.descriptor.render())
    }

    @Test
    fun `java files are read as well, and a library loaded twice in a file is one symbol`() {
        val pairs = symbols().map { it.declaration?.artifact?.path?.value to it.name }
        assertEquals(setOf(CLIENT to "checksum", HELPER to "checksum", HELPER to "helper"), pairs.toSet())
        assertEquals(3, pairs.size)
    }

    @Test
    fun `the files of a source set answer in one batch owned by the source set`() {
        val batch = workspace.produce(LoadCallProducer()).single()
        assertEquals(NativeModel.sourceSetUnit, batch.owner)
        assertEquals(ProducerRef("kscope.jni.load-calls", "0.2.0"), batch.producer)
    }

    @Test
    fun `a source set without a load gives a batch without facts`() {
        val plain = NativeWorkspace(mapOf(PLAIN to PLAIN_KOTLIN))
        assertEquals(emptyList(), plain.produce(LoadCallProducer()).single().facts)
    }

    @Test
    fun `a file that cannot be read gives a note while the other files of the source set still produce`() {
        val readable = mapOf(CLIENT to CLIENT_KOTLIN, PLAIN to PLAIN_KOTLIN)
        val unreadable = object : WorkspaceFiles {
            override fun read(ref: ArtifactRef): ByteArray =
                readable[ref.path.value]?.toByteArray() ?: throw IOException("the disk is gone")

            override fun absolutePath(ref: ArtifactRef): Path = Path.of(ref.path.value)
        }
        val facts = workspace.produce(LoadCallProducer(), readFrom = unreadable).single().facts
        val note = facts.filterIsInstance<Fact.OfDiagnostic>().single().value
        assertEquals("jni.unreadable", note.code.value)
        assertEquals("$HELPER: it could not be read: the disk is gone.", note.message)
        assertEquals(listOf("checksum"), facts.filterIsInstance<Fact.OfSymbol>().map { it.value.name })
    }

    @Test
    fun `producing twice gives the same facts`() {
        assertEquals(workspace.produce(LoadCallProducer()), workspace.produce(LoadCallProducer()))
    }

    @Test
    fun `a file with a load but outside every module only gets a note`() {
        val facts = workspace.produce(LoadCallProducer(), inModule = false).flatMap { it.facts }
        assertEquals(List(2) { "jni.module-unknown" }, facts.map { (it as Fact.OfDiagnostic).value.code.value })
    }
}
