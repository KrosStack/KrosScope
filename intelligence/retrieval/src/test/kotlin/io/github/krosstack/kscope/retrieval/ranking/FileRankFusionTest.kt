package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileRankFusionTest {
    @Test
    fun `duplicates are removed before ranking`() {
        val matches = FileRankFusion.fuse(listOf(ref("a"), ref("a"), ref("c")), listOf(ref("b"), ref("c")))
        assertEquals(listOf("c", "a", "b"), matches.map { it.artifact.path.value })
        assertEquals(1.0 / 62, matches.first().structuralContribution)
        assertEquals(1.0 / 62, matches.first().semanticContribution)
        assertEquals(2.0 / 62, matches.first().score)
        assertEquals(0.0, matches[1].semanticContribution)
        assertEquals(0.0, matches[2].structuralContribution)
    }

    @Test
    fun `rank eleven overlap can enter the final top ten`() {
        val first = (1..10).map { ref("first$it") } + ref("overlap")
        val second = (1..10).map { ref("second$it") } + ref("overlap")
        val matches = FileRankFusion.fuse(first, second)
        assertEquals(21, matches.size)
        assertEquals(ref("overlap"), matches.first().artifact)
        assertTrue(matches.take(10).any { it.artifact == ref("overlap") })
        assertEquals(2.0 / 71, matches.first().score)
    }

    @Test
    fun `equal totals sort by path and equal paths retain repository identity`() {
        val other = ref("a", "other")
        val matches = FileRankFusion.fuse(listOf(ref("b"), ref("a")), listOf(other, ref("a")))
        assertEquals(listOf(ref("a"), other, ref("b")), matches.map { it.artifact })
        assertEquals(listOf(other, ref("a")), FileRankFusion.fuse(listOf(ref("a")), listOf(other)).map { it.artifact })
        assertEquals(emptyList(), FileRankFusion.fuse(emptyList(), emptyList()))
        assertEquals(listOf(ref("a")), FileRankFusion.fuse(emptyList(), listOf(ref("a"))).map { it.artifact })
    }

    private fun ref(path: String, repository: String = "sample") = ArtifactRef(RepositoryId(repository), RepoPath(path))
}
