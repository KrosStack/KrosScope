package io.github.krosstack.kscope.service.freshness

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal class GitStatus(val head: String?, val paths: List<String>, val top: Path)

internal class GitChanges(private val executable: String, private val started: StartedProcesses) {
    fun head(checkout: Path): String? = run(checkout, HEAD)?.trim()?.takeIf { it.isNotEmpty() }

    fun status(checkout: Path): GitStatus? {
        val records = run(checkout, STATUS)?.split(NUL) ?: return null
        return GitStatus(
            head = records.firstOrNull { it.startsWith(OID) }?.removePrefix(OID),
            paths = pathsOf(records),
            top = workTree(checkout),
        )
    }

    private fun pathsOf(records: List<String>): List<String> {
        val paths = ArrayList<String>()
        val remaining = records.iterator()
        while (remaining.hasNext()) {
            val record = remaining.next()
            pathOf(record)?.let(paths::add)
            if (record.startsWith(RENAMED) && remaining.hasNext()) paths.add(remaining.next())
        }
        return paths.distinct().sorted()
    }

    private fun workTree(checkout: Path): Path =
        generateSequence(checkout) { it.parent }.firstOrNull { Files.exists(it.resolve(GIT_ENTRY)) } ?: checkout

    private fun pathOf(record: String): String? = when {
        record.startsWith(CHANGED) -> record.split(' ', limit = CHANGED_FIELDS).last()
        record.startsWith(RENAMED) -> record.split(' ', limit = RENAMED_FIELDS).last()
        record.startsWith(UNMERGED) -> record.split(' ', limit = UNMERGED_FIELDS).last()
        record.startsWith(UNTRACKED) -> record.removePrefix(UNTRACKED)
        else -> null
    }

    private fun run(directory: Path, arguments: List<String>): String? = try {
        val process = ProcessBuilder(listOf(executable) + arguments).directory(directory.toFile())
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        started.add(process)
        val output = process.inputStream.readAllBytes().toString(Charsets.UTF_8)
        output.takeIf { process.waitFor() == 0 }
    } catch (_: IOException) {
        null
    }

    private companion object {
        const val NUL = '\u0000'
        const val OID = "# branch.oid "
        const val CHANGED = "1 "
        const val RENAMED = "2 "
        const val UNMERGED = "u "
        const val UNTRACKED = "? "
        const val CHANGED_FIELDS = 9
        const val RENAMED_FIELDS = 10
        const val UNMERGED_FIELDS = 11
        const val GIT_ENTRY = ".git"
        val HEAD = listOf("rev-parse", "HEAD")
        val STATUS = listOf(
            "--no-optional-locks",
            "status",
            "--porcelain=v2",
            "--branch",
            "--untracked-files=all",
            "-z",
            "--",
            ".",
        )
    }
}
