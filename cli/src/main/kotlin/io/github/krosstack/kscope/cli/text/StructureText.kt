package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Column
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.cli.text.format.Table
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.cli.text.format.TextDocument
import io.github.krosstack.kscope.cli.text.format.Tone
import io.github.krosstack.kscope.service.BuildRole
import io.github.krosstack.kscope.service.CheckoutRole
import io.github.krosstack.kscope.service.StructureKind
import io.github.krosstack.kscope.service.StructureResult

/** The result of `info --structure`: what the directory is, with the evidence for each statement. */
internal object StructureText {
    fun render(result: StructureResult, context: TextContext): String {
        val document = TextDocument(context)
        val tone = if (result.conclusion.kind == StructureKind.NONE) Tone.WARNING else Tone.GOOD
        document.conclusion(result.conclusion.summary, tone)
        val visited = Format.noun(result.directoriesVisited, "directory", "directories")
        document.line("Inspected $visited, ${result.depth} levels deep.")
        val skipped = Format.noun(result.ignoredDirectoriesSkipped, "ignored directory", "ignored directories")
        document.line("Skipped $skipped.")
        result.ignoreRulesNotice?.let { document.line(it) }
        evidence(result, document)
        git(result, document)
        builds(result, document)
        projects(result, document)
        nested(result, document)
        if (result.truncated) {
            document.hint("Stopped at the limit of directories; more may exist. Pass a smaller --structure-depth.")
        }
        return document.toString()
    }

    private fun evidence(result: StructureResult, document: TextDocument) {
        if (result.conclusion.evidence.isEmpty()) return
        document.section("Because")
        result.conclusion.evidence.forEach { document.line("  $it") }
    }

    private fun git(result: StructureResult, document: TextDocument) {
        val git = result.git
        if (git == null) {
            document.section("Git").line("  This directory is not inside a Git checkout.")
            return
        }
        val role = when (git.role) {
            CheckoutRole.REPOSITORY -> "the repository"
            CheckoutRole.LINKED_WORKTREE -> "a linked worktree of its repository"
            CheckoutRole.SUBMODULE -> "a submodule checkout"
        }
        document.section("Git").fields(
            listOf("Checkout" to git.directory, "This is" to role, "Evidence" to git.evidence),
        )
        if (result.worktrees.isEmpty()) return
        val table = Table(listOf(Column("other worktrees"), Column("exists"), Column("evidence")))
        result.worktrees.forEach { table.row(it.directory, if (it.exists) "yes" else "no", it.evidence) }
        document.section("Worktrees of this repository (${result.worktrees.size})").table(table)
    }

    private fun builds(result: StructureResult, document: TextDocument) {
        if (result.gradleBuilds.isEmpty()) return
        val table = Table(listOf(Column("directory"), Column("role"), Column("evidence")))
        result.gradleBuilds.forEach { table.row(it.directory, role(it.role), it.evidence) }
        document.section("Gradle builds (${result.gradleBuilds.size})").table(table)
    }

    private fun role(role: BuildRole): String = when (role) {
        BuildRole.TOP_LEVEL -> "top-level"
        BuildRole.INCLUDED -> "included build"
        BuildRole.NESTED_INDEPENDENT -> "nested independent build"
    }

    private fun projects(result: StructureResult, document: TextDocument) {
        if (result.projects.isEmpty()) return
        val table = Table(listOf(Column("directory"), Column("project"), Column("marker")))
        result.projects.forEach { table.row(it.directory, it.ecosystem, it.markerFile) }
        document.section("Other projects (${result.projects.size})").table(table)
    }

    private fun nested(result: StructureResult, document: TextDocument) {
        if (result.nestedRepositories.isEmpty()) return
        val table = Table(listOf(Column("directory"), Column("kind"), Column("evidence")))
        result.nestedRepositories.forEach { table.row(it.directory, kind(it.role, it.sameRepositoryAs), it.evidence) }
        document.section("Nested Git checkouts (${result.nestedRepositories.size})").table(table)
    }

    private fun kind(role: CheckoutRole, sameRepositoryAs: String?): String = when {
        role == CheckoutRole.SUBMODULE -> "submodule"
        sameRepositoryAs != null -> "worktree of the repository in $sameRepositoryAs"
        role == CheckoutRole.LINKED_WORKTREE -> "linked worktree"
        else -> "repository"
    }
}
