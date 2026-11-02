package io.github.krosstack.kscope.analyzer.schema.web.copy

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.generator.RepositorySpec
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.analyzer.schema.web.BundleWorld
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CopySourceNotesTest {
    @Test
    fun `a script that cannot be read is noted and the other scripts are still read`() = tasktideWorld(
        appFiles = mapOf("build.gradle.kts" to "processResources { from(\"root\") }\n"),
    ).use { world ->
        val files = world.workspace.files
        val failing = object : WorkspaceFiles {
            override fun read(ref: ArtifactRef): ByteArray =
                if (ref.path.value == SERVER) throw IOException("gone") else files.read(ref)

            override fun absolutePath(ref: ArtifactRef): Path = files.absolutePath(ref)
        }
        val facts = world.produceCopies(failing).single().facts
        val note = facts.filterIsInstance<Fact.OfDiagnostic>().single().value

        assertEquals("web.unreadable", note.code.value)
        assertEquals(DiagnosticSeverity.WARNING, note.severity)
        assertEquals(SERVER, note.location?.artifact?.path?.value)
        assertEquals(listOf("root"), facts.symbols().map { it.name })
    }

    @Test
    fun `a script that no module contains is noted and leaves no symbol`() = BundleWorld(
        listOf(
            RepositorySpec("lone", mapOf("other/build.gradle.kts" to copying("x")), listOf(ModuleSpec(":server"))),
        ),
    ).use { world ->
        val note = world.copyFacts.filterIsInstance<Fact.OfDiagnostic>().single().value

        assertEquals("web.module-unknown", note.code.value)
        assertEquals(DiagnosticSeverity.INFO, note.severity)
        assertEquals(emptyList(), world.sources())
    }

    @Test
    fun `a script that copies nothing is not noted`() = tasktideWorld(server = "plugins { }\n").use { world ->
        assertEquals(emptyList(), world.copyFacts)
    }

    @Test
    fun `no fact holds a location on disk and producing twice gives the same facts`() = tasktideWorld().use { world ->
        val text = world.copyFacts.joinToString("\n") { it.toString() }.replace('\\', '/')

        assertTrue("file://" !in text, text)
        assertTrue(world.workspace.roots.values.none { it.toString().replace('\\', '/') in text }, text)
        assertEquals(world.copyBatches, world.produceCopies(world.workspace.files))
    }
}
