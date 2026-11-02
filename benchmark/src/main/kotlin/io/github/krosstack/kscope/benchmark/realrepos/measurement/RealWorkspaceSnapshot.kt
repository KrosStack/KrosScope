package io.github.krosstack.kscope.benchmark.realrepos.measurement

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositorySpec
import io.github.krosstack.kscope.benchmark.realrepos.RealWorkspaceMember
import io.github.krosstack.kscope.benchmark.realrepos.members
import io.github.krosstack.kscope.benchmark.realrepos.workspaceKey
import java.nio.file.Path

internal class RealWorkspaceSnapshot(
    repositories: Map<String, RealRepositorySpec>,
    task: RealCommitTask,
    storage: RealRunStorage,
) {
    val workspace: Path = storage.scratch(task.workspaceKey())
    private val members: List<RealWorkspaceMember> = task.members()
    private val snapshots: List<RealSnapshot> = members.map { member ->
        val directory = if (task.workspace.isEmpty()) workspace else workspace.resolve(member.repository)
        RealSnapshot(repositories.getValue(member.repository), storage, directory)
    }

    fun prepare() {
        snapshots.forEach { it.prepare() }
    }

    fun checkoutParents() {
        members.zip(snapshots).forEach { (member, snapshot) -> snapshot.checkout(member.parent) }
    }

    fun checkoutCommits() {
        members.zip(snapshots).forEach { (member, snapshot) -> snapshot.checkout(member.commit) }
    }
}
