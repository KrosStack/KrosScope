package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.retrieval.ranking.SemanticFixture
import io.github.krosstack.kscope.retrieval.ranking.retrievalSymbol
import io.github.krosstack.kscope.spi.search.SearchHit

internal val APP = RepositoryId("app")
internal val LIB = RepositoryId("lib")

internal fun artifactIn(repository: RepositoryId, path: String): Artifact = Artifact(
    ArtifactRef(repository, RepoPath(path)),
    ArtifactType("kotlin.source", ArtifactCategory.SOURCE),
    ContentHash("hash"),
    10,
    1,
    null,
    null,
)

internal fun symbolHit(symbol: Symbol, score: Double): SearchHit =
    SearchHit(NodeRef.OfSymbol(symbol.id), score, listOf("task"), emptyMap())

internal fun artifactHit(artifact: Artifact, score: Double): SearchHit =
    SearchHit(NodeRef.OfArtifact(artifact.ref), score, listOf("task"), emptyMap())

internal class TwoRepositories(extraLibrary: Int = 0, mostCandidates: Int = SeedSearch.MOST_CANDIDATES) {
    val appStore = artifactIn(APP, "TaskStore.kt")
    val appReminders = artifactIn(APP, "Reminders.kt")
    val libSync = artifactIn(LIB, "TaskSync.kt")
    val libClock = artifactIn(LIB, "Clock.kt")
    val appGenerated = artifactIn(APP, "Generated.kt")
    val crowd = (0 until extraLibrary).map { artifactIn(LIB, "Crowd$it.kt") }
    val store = retrievalSymbol("TaskStore", appStore)
    val reminders = retrievalSymbol("TaskReminders", appReminders)
    val sync = retrievalSymbol("TaskSync", libSync)
    val clock = retrievalSymbol("TaskClock", libClock)
    val generated = retrievalSymbol(
        "TaskGenerated",
        appGenerated,
        origin = SymbolOrigin.Generated("test.generator", NodeRef.OfSymbol(sync.id)),
    )
    val crowded = crowd.mapIndexed { index, file -> retrievalSymbol("TaskCrowd$index", file) }
    val files = listOf(appStore, appReminders, appGenerated, libSync, libClock) + crowd
    val symbols = listOf(store, reminders, generated, sync, clock) + crowded
    val graph = SemanticFixture(
        files,
        symbols,
        listOf(
            relationship(NodeRef.OfSymbol(store.id), NodeRef.OfSymbol(sync.id)),
            relationship(NodeRef.OfSymbol(sync.id), NodeRef.OfSymbol(clock.id)),
            relationship(NodeRef.OfSymbol(reminders.id), NodeRef.OfSymbol(clock.id), RelationKind.TYPE_USES),
            relationship(NodeRef.OfSymbol(sync.id), NodeRef.OfSymbol(generated.id), RelationKind.GENERATES),
        ),
    )
    val symbolHits = crowded.mapIndexed { index, symbol -> symbolHit(symbol, CROWD_TOP - index) } +
        listOf(
            symbolHit(sync, 5.0),
            symbolHit(store, 4.0),
            symbolHit(clock, 3.0),
            symbolHit(reminders, 2.0),
            symbolHit(generated, 1.0),
        )
    val artifactHits = crowd.mapIndexed { index, file -> artifactHit(file, CROWD_TOP - index) } +
        listOf(artifactHit(libSync, 5.0), artifactHit(appStore, 4.0), artifactHit(libClock, 3.0)) +
        artifactHit(appReminders, 2.0)
    val index = RecordingIndex(RetrievalIndex(setOf("task"), symbolHits + artifactHits))
    val retrieval = TaskRetrieval(graph, index, mostCandidates)

    private companion object {
        const val CROWD_TOP = 1000.0
    }
}
