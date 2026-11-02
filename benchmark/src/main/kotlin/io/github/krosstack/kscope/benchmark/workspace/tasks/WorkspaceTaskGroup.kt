package io.github.krosstack.kscope.benchmark.workspace.tasks

/** The sets of tasks that the results are aggregated over. */
internal enum class WorkspaceTaskGroup(val label: String, private val admits: (WorkspaceTaskFile) -> Boolean) {
    ALL("All tasks", { true }),
    NAMED("Named", { it.named }),
    NOT_NAMED("Not named", { !it.named }),
    ;

    fun includes(task: WorkspaceTaskFile): Boolean = admits(task)
}
