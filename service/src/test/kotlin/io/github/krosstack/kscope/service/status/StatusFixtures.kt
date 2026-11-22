package io.github.krosstack.kscope.service.status

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

internal object StatusFixtures {
    private val sideFiles = listOf("-shm", "-wal")

    fun status(
        workspace: Path,
        limitMillis: Long = STATUS_LIMIT_MILLIS,
        maxFiles: Int = STATUS_MAX_FILES,
        now: () -> Instant = Instant::now,
    ): IndexStatus = KrosScope.open(workspace, OpenOptions(tokenCache = false)).use {
        it.status(limitMillis, maxFiles, now)
    }

    private fun isSideFile(file: Path): Boolean = sideFiles.any { file.fileName.toString().endsWith(it) }

    fun indexFiles(workspace: Path): Map<String, Int> = Files.walk(workspace.resolve(".kscope")).use { paths ->
        paths.asSequence().filter { file -> file.isRegularFile() && !isSideFile(file) }
            .associate { it.relativeTo(workspace).toString() to Files.readAllBytes(it).contentHashCode() }
    }
}
