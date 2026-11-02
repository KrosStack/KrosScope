package io.github.krosstack.kscope.benchmark.workspace.tasks

import io.github.krosstack.kscope.model.context.TokenCount

/** Assembles the result of a run: the scores of every group of tasks, then of every task. */
internal class WorkspaceTaskResults(
    private val tasks: List<WorkspaceTaskFile>,
    private val scoring: WorkspaceTaskScoring,
) {
    fun build(repositories: List<String>, budgets: List<TokenCount>): WorkspaceTasksResult = WorkspaceTasksResult(
        repositories,
        budgets.map { it.value }.sorted(),
        WorkspaceTaskGroup.entries.mapNotNull(::group),
        tasks.map(::task),
    )

    private fun group(group: WorkspaceTaskGroup): GroupScores? {
        val ids = tasks.filter(group::includes).map { it.id }
        return if (ids.isEmpty()) null else GroupScores(group.label, ids, scoring.over(ids.toSet()))
    }

    private fun task(task: WorkspaceTaskFile): TaskScores = TaskScores(
        task.id,
        task.title,
        task.named,
        task.editedRepositories,
        scoring.missingGoldFiles(task.id),
        scoring.over(setOf(task.id)),
    )
}
