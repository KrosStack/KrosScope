package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.benchmark.score.FakeGraph
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.search.SearchHit
import io.github.krosstack.kscope.spi.search.SearchIndex
import io.github.krosstack.kscope.spi.search.SearchQuery
import io.github.krosstack.kscope.spi.search.SearchScope

internal class ReplayGraph(paths: List<String>) :
    GraphReader by FakeGraph(artifacts = paths.distinct().map(::replayArtifact)),
    SearchIndex {
    fun ref(path: String) = replayArtifact(path).ref

    override fun search(query: SearchQuery): List<SearchHit> = emptyList()
    override fun vocabularyContains(term: String, scope: SearchScope) = false
    override fun vocabularyWithPrefix(prefix: String, scope: SearchScope, limit: Int): List<String> = emptyList()
    override fun documentFrequency(term: String, scope: SearchScope) = 0
}

private fun replayArtifact(path: String) = Artifact(
    ArtifactRef(RepositoryId("replay"), RepoPath(path)),
    ArtifactType("test.source", ArtifactCategory.SOURCE),
    ContentHash("test"),
    1,
    1,
    null,
    null,
)
