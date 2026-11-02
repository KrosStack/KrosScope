package io.github.krosstack.kscope.benchmark.localization

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizationCorpusTest {
    @Test
    fun `raw baselines include readable native and unknown text with explicit exclusion counts`() {
        val workspace = Files.createTempDirectory("kscope-localization-corpus")
        try {
            val native = write(workspace, "native", "needle".toByteArray(), ArtifactCategory.NATIVE)
            val unknown = write(workspace, "other", "other text".toByteArray(), ArtifactCategory.OTHER)
            val nul = write(workspace, "nul-text", byteArrayOf(0), ArtifactCategory.RESOURCE)
            val invalidUtf8 = write(workspace, "invalid", byteArrayOf(0xc3.toByte(), 0x28), ArtifactCategory.OTHER)
            val generated = native.copy(
                ref = native.ref.copy(path = RepoPath("generated")),
                generated = GeneratedInfo("generator", null),
            )
            val oversized = unknown.copy(ref = unknown.ref.copy(path = RepoPath("large")), sizeBytes = 2_097_153)
            val missing = unknown.copy(ref = unknown.ref.copy(path = RepoPath("missing")))
            val artifacts = listOf(missing, unknown, invalidUtf8, generated, native, oversized, nul)
            val corpus = LocalizationCorpus.load(workspace, artifacts)

            assertEquals(mapOf("native" to "needle", "other" to "other text"), corpus.documents)
            assertEquals(LocalizationCorpusSummary(2, 1, 1, 2, 1), corpus.summary)
            PlainTextSearch(corpus.documents).use { baseline ->
                assertEquals(listOf("native"), baseline.search("needle"))
            }
        } finally {
            workspace.toFile().deleteRecursively()
        }
    }

    private fun write(workspace: Path, path: String, bytes: ByteArray, category: ArtifactCategory): Artifact {
        Files.write(workspace.resolve(path), bytes)
        return Artifact(
            ArtifactRef(RepositoryId("sample"), RepoPath(path)),
            ArtifactType("sample", category),
            ContentHash("ignored"),
            bytes.size.toLong(),
            1,
            null,
            null,
        )
    }
}
