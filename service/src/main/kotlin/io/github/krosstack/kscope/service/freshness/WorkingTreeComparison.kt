package io.github.krosstack.kscope.service.freshness

import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Instant

internal class TreeOutcome(val changed: Int, val doubt: String?)

internal class WorkingTreeComparison(private val since: Instant?, private val scanned: DirtyPaths?) {
    fun of(status: GitStatus): TreeOutcome {
        val time = since ?: return TreeOutcome(0, UNDATED)
        val now = status.paths.toSet()
        val edited = now.count { newer(status.top, it, time) }
        return when {
            scanned == null -> TreeOutcome(edited, NOT_RECORDED)
            scanned.exceeded -> TreeOutcome(edited, TOO_MANY)
            else -> TreeOutcome(edited + scanned.paths.count { it !in now }, null)
        }
    }

    private fun newer(top: Path, path: String, time: Instant): Boolean = try {
        Files.getLastModifiedTime(top.resolve(path)).toInstant() > time
    } catch (_: IOException) {
        false
    } catch (_: InvalidPathException) {
        false
    }

    companion object {
        const val UNDATED = "the time of the scan is not known; the next scan records it"
        const val NOT_RECORDED = "the scan did not record its changed files; the next scan records them"
        const val TOO_MANY = "the scan found too many changed files to record them"
    }
}
