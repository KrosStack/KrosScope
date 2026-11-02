package io.github.krosstack.kscope.benchmark.realrepos

internal fun RealCommitTask.members(): List<RealWorkspaceMember> =
    workspace.ifEmpty { listOf(RealWorkspaceMember(repository, parent, commit)) }

internal fun RealCommitTask.workspaceKey(): String {
    if (workspace.isEmpty()) return repository
    val members = workspace.map { it.repository }.sorted().joinToString(",")
    return "workspace:$repository:$members"
}
