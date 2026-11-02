package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.process.FakeWorkerHarness
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerCommand
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerLauncher
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerTimeouts
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class KotlinScriptOutlinerTest {
    private val worker = FakeWorkerHarness()
    private val files = object : WorkspaceFiles {
        override fun read(ref: ArtifactRef): ByteArray = ByteArray(0)

        override fun absolutePath(ref: ArtifactRef): Path = Path.of("/workspace").resolve(ref.path.value)
    }

    @AfterTest
    fun cleanUp() {
        worker.close()
    }

    private fun script(path: String) = Artifact(
        ref = ArtifactRef(RepositoryId("repo"), RepoPath(path)),
        type = ArtifactType("gradle.build-script", ArtifactCategory.BUILD),
        contentHash = ContentHash("hash-$path"),
        sizeBytes = 1,
        lineCount = 1,
        sourceSet = null,
        generated = null,
    )

    private fun outlinerWith(mode: String) =
        KotlinScriptOutliner(worker.launcher(mode, WorkerTimeouts(1.minutes, 5.seconds, 5.seconds))) {}

    @Test
    fun `every script is outlined by the worker`() {
        val scripts = listOf(script("build.gradle.kts"), script("lib/build.gradle.kts"))
        val outlines = outlinerWith("ok").outline(scripts, files)
        assertEquals(scripts.map { it.ref }, outlines.keys.toList())
        assertEquals(listOf("plugins"), outlines.getValue(scripts.first().ref).calls.map { it.name })
    }

    @Test
    fun `an unavailable worker outlines nothing`() {
        val outliner = KotlinScriptOutliner(WorkerLauncher({ WorkerCommand.Missing("it is not installed") })) {}
        assertEquals(emptyMap(), outliner.outline(listOf(script("build.gradle.kts")), files))
    }

    @Test
    fun `a failing worker outlines nothing for the scripts it did not answer`() {
        assertEquals(emptyMap(), outlinerWith("exit").outline(listOf(script("build.gradle.kts")), files))
    }

    @Test
    fun `no worker is started when there are no scripts`() {
        var started = 0
        val launcher = WorkerLauncher({
            started++
            WorkerCommand.Missing("none")
        })
        assertEquals(emptyMap(), KotlinScriptOutliner(launcher) {}.outline(emptyList(), files))
        assertEquals(0, started)
    }
}
