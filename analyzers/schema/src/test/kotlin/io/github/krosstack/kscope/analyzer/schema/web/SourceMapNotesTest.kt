package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.analyzer.schema.facts
import io.github.krosstack.kscope.analyzer.schema.relationships
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

private fun List<FactBatch>.notes(): List<Diagnostic> = facts().filterIsInstance<Fact.OfDiagnostic>().map { it.value }

private fun producedWithFailures(workspace: WebWorkspace, fails: (String) -> Boolean): List<FactBatch> {
    val failing = object : WorkspaceFiles {
        override fun read(ref: ArtifactRef): ByteArray =
            if (fails(ref.path.value)) throw IOException("gone") else workspace.files.read(ref)

        override fun absolutePath(ref: ArtifactRef): Path = Path.of(ref.path.value)
    }
    val batches = mutableListOf<FactBatch>()
    WebBundleProducer().produce(workspace.request(workspace.units()).copy(files = failing)) { batches += it }
    return batches
}

class SourceMapNotesTest {
    @Test
    fun `an inline source map is noted and gives no fact`() {
        val inline = "a();\n//# sourceMappingURL=data:application/json;base64,e30=\n"
        val files = tasktideWeb.filterKeys { !it.endsWith(".map") } + ("dist/assets/index.js" to inline)
        val batches = WebWorkspace(files, listOf("dist")).produce()
        val note = batches.notes().single()

        assertEquals("web.inline-source-map", note.code.value)
        assertEquals(DiagnosticSeverity.INFO, note.severity)
        assertEquals("dist/assets/index.js", note.location?.artifact?.path?.value)
        assertEquals(emptyList(), batches.facts().relationships())
        assertEquals(emptyList(), batches.facts().symbols().filter { it.kind.id == "bundle.file" })
    }

    @Test
    fun `an invalid map is noted and gives no fact`() {
        val files = tasktideWeb + ("dist/assets/index.js.map" to "{\"version\":3,\"names\":[]}")
        val batches = WebWorkspace(files, listOf("dist")).produce()
        val note = batches.notes().single()

        assertEquals("web.invalid-source-map", note.code.value)
        assertEquals(DiagnosticSeverity.INFO, note.severity)
        assertEquals("dist/assets/index.js.map", note.location?.artifact?.path?.value)
        assertEquals(emptyList(), batches.facts().relationships())
    }

    @Test
    fun `an unreadable map is noted and the other facts of the source set remain`() {
        val workspace = WebWorkspace(tasktideWeb, listOf("dist"))
        val batches = producedWithFailures(workspace) { it.endsWith(".map") }

        assertEquals(listOf("web.unreadable-source-map"), batches.notes().map { it.code.value })
        assertEquals(DiagnosticSeverity.INFO, batches.notes().single().severity)
        assertEquals(listOf("@tasktide/web"), batches.symbolsOfKind("npm.package").map { it.name })
        assertEquals(emptyList(), batches.facts().relationships())
    }

    @Test
    fun `an unreadable script is noted with a warning and the other scripts remain`() {
        val batches = producedWithFailures(WebWorkspace(tasktideWeb)) { it == "src/main.ts" }
        val note = batches.notes().single()

        assertEquals("web.unreadable", note.code.value)
        assertEquals(DiagnosticSeverity.WARNING, note.severity)
        assertEquals("src/main.ts", note.location?.artifact?.path?.value)
        assertEquals(listOf("src/tasks.ts"), batches.symbolsOfKind("typescript.file").map { it.qualifiedName })
    }

    @Test
    fun `an unreadable bundle file is noted and leaves no link`() {
        val workspace = WebWorkspace(tasktideWeb, listOf("dist"))
        val batches = producedWithFailures(workspace) { it == "dist/assets/index.js" }

        assertEquals(listOf("web.unreadable"), batches.notes().map { it.code.value })
        assertEquals(emptyList(), batches.facts().relationships())
    }
}
