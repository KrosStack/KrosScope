package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.analyzer.kotlin.worker.ResponseWriter
import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.WorkerLoop
import io.github.krosstack.kscope.analyzer.kotlin.worker.WorkerService
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerProtocol
import io.github.krosstack.kscope.spi.worker.WorkerRequest
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import java.io.BufferedReader
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceOutlineRequestTest {
    private fun file(path: String, sample: String) = WorkerFile(
        ArtifactRef(RepositoryId("r"), RepoPath(path)),
        SampleWorkspace.samplesRoot.resolve(sample).toString(),
        ContentHash("0".repeat(64)),
    )

    private fun convention(path: String): WorkerFile {
        val inSources = "src/main/kotlin/$path"
        return file("convention/$inSources", "plugins/convention/$inSources")
    }

    private fun outlines(vararg files: WorkerFile): Map<String, WorkerResponse.Outline> {
        val output = StringWriter()
        val writer = ResponseWriter(output)
        val request = WorkerProtocol.encode(WorkerRequest.OutlineScripts("o-1", files.toList()))
        WorkerLoop(BufferedReader(StringReader(request)), writer, WorkerService(writer)).run()
        val responses = output.toString().lines().filter { it.isNotBlank() }.map(WorkerProtocol::decodeResponse)
        assertTrue(responses.last() is WorkerResponse.Completed)
        return responses.filterIsInstance<WorkerResponse.Outline>().associateBy { it.ref.path.value }
    }

    @Test
    fun `sources and scripts of one request are outlined side by side`() {
        val outlines = outlines(
            file("lib/build.gradle.kts", "scripts/lib/build.gradle.kts"),
            convention("acme/LibraryConventionPlugin.kt"),
        )
        assertEquals(emptyList(), outlines.getValue("lib/build.gradle.kts").outline.pluginClasses)
        assertTrue(outlines.getValue("lib/build.gradle.kts").outline.calls.isNotEmpty())
        val library = outlines.getValue("convention/src/main/kotlin/acme/LibraryConventionPlugin.kt").outline
        assertEquals(listOf("acme.LibraryConventionPlugin"), library.pluginClasses.map { it.name })
        assertEquals(emptyList(), library.calls)
    }

    @Test
    fun `a plugin class inlines the functions of its own project and no other`() {
        val outlines = outlines(
            convention("acme/Helpers.kt"),
            file("other/src/main/kotlin/OtherPlugin.kt", "plugins/other/src/main/kotlin/OtherPlugin.kt"),
        )
        val other = outlines.getValue("other/src/main/kotlin/OtherPlugin.kt").outline.pluginClasses.single()
        assertEquals(listOf("val", "apply", "configureAndroidBase"), other.apply.map { it.name })
        assertEquals(false, other.apply.last().inlined)
        assertEquals(emptyList(), other.notInlined)
    }
}
