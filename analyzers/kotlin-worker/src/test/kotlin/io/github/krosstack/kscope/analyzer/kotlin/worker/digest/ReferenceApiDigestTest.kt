package io.github.krosstack.kscope.analyzer.kotlin.worker.digest

import io.github.krosstack.kscope.analyzer.kotlin.worker.ReferenceWorkspaceLayout
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.worker.WorkerFile
import java.nio.file.Files
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReferenceApiDigestTest {
    @Test
    fun `every reference Kotlin source has a deterministic digest`() {
        val root = ReferenceWorkspaceLayout.workspaceRoot
        val sources = Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }.sorted().toList()
        }.map { path ->
            WorkerFile(
                ArtifactRef(RepositoryId("reference"), RepoPath(root.relativize(path).invariantSeparatorsPathString)),
                path.toString(),
                ContentHash("content"),
            )
        }
        assertTrue(sources.isNotEmpty())
        val fixture = DigestFixture(root)
        val first = fixture.digests(sources)
        assertEquals(sources.map { it.ref }.toSet(), first.keys)
        assertEquals(first, fixture.digests(sources))
    }
}
