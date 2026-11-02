package io.github.krosstack.kscope.benchmark.workspace.tasks

import io.github.krosstack.kscope.benchmark.localization.LocalizationGoldScope
import io.github.krosstack.kscope.benchmark.localization.LocalizationSymbolAddress
import io.github.krosstack.kscope.benchmark.localization.LocalizationTask
import kotlinx.serialization.Serializable

/**
 * A task of the workspace of repositories as its file holds it. Paths are workspace paths,
 * `<repository directory>/<path in the repository>`, and [relationships] name ids of the gold relationships.
 */
@Serializable
internal data class WorkspaceTaskFile(
    val id: String,
    val title: String,
    val statement: String,
    val category: String,
    val level: Int,
    val named: Boolean,
    val mustEdit: List<String>,
    val mustRead: List<String> = emptyList(),
    val symbols: List<LocalizationSymbolAddress> = emptyList(),
    val relationships: List<String> = emptyList(),
    val changeScope: LocalizationGoldScope? = null,
    val relevantTests: List<LocalizationSymbolAddress>? = null,
) {
    val editedRepositories: List<String>
        get() = mustEdit.map { it.substringBefore('/') }.distinct().sorted()

    val localization: LocalizationTask
        get() = LocalizationTask(id, statement, mustEdit, mustRead, symbols, relevantTests, changeScope)
}
