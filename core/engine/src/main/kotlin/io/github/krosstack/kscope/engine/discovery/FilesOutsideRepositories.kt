package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ReasonCode
import java.nio.file.Path

/** Counts the files of a root that lie in no checkout, which a workspace of several repositories does not index. */
internal class FilesOutsideRepositories(private val root: Path, private val indexDirectory: Path) {
    fun notice(checkouts: List<String>): Diagnostic? {
        val skipped = checkouts.map { root.resolve(it) }.toSet() + indexDirectory
        val count = WorkspaceWalker.relativeFiles(root, skipping = skipped)
            .count { it != WorkspaceConfig.SETTINGS_FILE_NAME }
        return if (count == 0) null else noticeFor(count)
    }

    private fun noticeFor(count: Int) = Diagnostic(
        severity = DiagnosticSeverity.INFO,
        code = ReasonCode("workspace.files-outside-repositories"),
        message = "$count ${if (count == 1) "file" else "files"} of the workspace root belong to no repository " +
            "and are not indexed.",
        location = null,
    )
}
