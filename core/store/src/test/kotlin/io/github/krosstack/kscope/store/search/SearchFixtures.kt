package io.github.krosstack.kscope.store.search

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Samples
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.graph.GraphStore
import io.github.krosstack.kscope.spi.search.SearchContent
import io.github.krosstack.kscope.spi.search.SearchHit
import io.github.krosstack.kscope.spi.search.SearchQuery
import io.github.krosstack.kscope.spi.search.SearchScope
import io.github.krosstack.kscope.spi.search.SearchTerm
import io.github.krosstack.kscope.store.Builders
import io.github.krosstack.kscope.store.SqliteGraphStore

internal val kotlinClass = SymbolKind("kotlin.class", SymbolCategory.TYPE)
internal val kotlinFunction = SymbolKind("kotlin.function", SymbolCategory.CALLABLE)
internal val stringResource = SymbolKind("android.resource.string", SymbolCategory.RESOURCE)

internal fun searchSymbol(
    id: String,
    name: String,
    kind: SymbolKind = kotlinClass,
    path: String? = "src/main/Sample.kt",
    origin: SymbolOrigin = SymbolOrigin.Source,
): Symbol = Builders.symbol(id, name, path, origin).copy(
    kind = kind,
    qualifiedName = null,
    signature = null,
    documentation = null,
)

internal fun Symbol.documented(sentence: String): Symbol = copy(documentation = sentence)

internal fun sourceArtifact(
    path: String,
    hash: String = "h-$path",
    category: ArtifactCategory = ArtifactCategory.SOURCE,
): Artifact = Samples.artifact.copy(
    ref = Builders.artifactRef(path),
    type = ArtifactType("test.${category.name.lowercase()}", category),
    contentHash = ContentHash(hash),
    generated = null,
)

internal fun GraphStore.addSymbols(owner: String, vararg symbols: Symbol) = replace(Builders.symbols(owner, *symbols))

internal fun GraphStore.addArtifacts(vararg artifacts: Artifact) =
    replace(Builders.batch("inventory", artifacts.map { Fact.OfArtifact(it) }))

/** A few symbols of a task tracker, indexed in an in-memory store together with [extra] ones. */
internal object TaskTideSymbols {
    val repository = searchSymbol("repository", "TaskRepository", path = "core/data/TaskRepository.kt").copy(
        qualifiedName = "tasktide.data.TaskRepository",
        signature = "class TaskRepository(private val api: TaskApi)",
        documentation = "Stores tasks and syncs them with the server.",
    )
    val syncTasks = searchSymbol("sync-tasks", "syncTasks", kotlinFunction, "core/data/TaskRepository.kt").copy(
        qualifiedName = "tasktide.data.TaskRepository.syncTasks",
        signature = "suspend fun syncTasks(): Result",
    )
    val service = searchSymbol("service", "SyncService", path = "app/service/SyncService.kt").documented(
        "Background synchronization of the task list.",
    )
    val label = searchSymbol("label", "priority_label", stringResource, path = "app/res/values/strings.xml")
    val formatter = searchSymbol("formatter", "TaskPriorityFormatter", path = "ui/format/Formatter.kt").documented(
        "Formats the priority of a task.",
    )

    fun indexed(vararg extra: Symbol): GraphStore {
        val store = SqliteGraphStore.inMemory()
        store.addSymbols("unit", repository, syncTasks, service, label, formatter, *extra)
        return store.refreshed()
    }
}

/** Reads the texts from a map by path and counts the reads; indexes the hand-written files of [indexedCategories]. */
internal class FakeContent(
    var texts: Map<String, String> = emptyMap(),
    var indexedCategories: Set<ArtifactCategory> = setOf(ArtifactCategory.SOURCE),
) : SearchContent {
    var reads = 0
        private set

    override fun isIndexed(artifact: Artifact): Boolean =
        artifact.generated == null && artifact.type.category in indexedCategories

    override fun textOf(artifact: Artifact): String? {
        reads++
        return texts[artifact.ref.path.value]
    }
}

internal fun GraphStore.refreshed(content: SearchContent = FakeContent()): GraphStore = also {
    refreshSearchIndex(content)
}

internal fun GraphStore.symbolHits(vararg words: String, limit: Int = 50): List<SearchHit> =
    searchIndex().search(SearchQuery(words.map { SearchTerm(it) }, SearchScope.SYMBOLS, limit))

internal fun GraphStore.artifactHits(vararg words: String, limit: Int = 50): List<SearchHit> =
    searchIndex().search(SearchQuery(words.map { SearchTerm(it) }, SearchScope.ARTIFACTS, limit))

internal fun List<SearchHit>.symbolIds(): List<String> = map { (it.node as NodeRef.OfSymbol).id.value }

internal fun List<SearchHit>.artifactPaths(): List<String> = map { (it.node as NodeRef.OfArtifact).ref.path.value }
