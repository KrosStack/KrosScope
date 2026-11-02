package io.github.krosstack.kscope.spi

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.RepoPath
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class OutputRootTest {
    private val detector = object : ArtifactDetector {
        override val id: String = "plain"

        override fun detect(candidate: ArtifactCandidate): ArtifactType? = null
    }

    private val files = object : WorkspaceFiles {
        override fun read(ref: ArtifactRef): ByteArray = ByteArray(0)

        override fun absolutePath(ref: ArtifactRef): Path = Path.of(ref.path.value)
    }

    @Test
    fun `a detector names no output root unless it overrides the method`() {
        assertEquals(emptyList(), detector.outputRoots(OutputRootRequest(emptyList(), files)))
    }

    @Test
    fun `the label of an output root is its generator after the prefix`() {
        val root = OutputRoot(RepoPath("web/dist"), "vite")

        assertEquals("output:vite", root.label)
        assertEquals("output:", OutputRoot.LABEL_PREFIX)
    }
}
