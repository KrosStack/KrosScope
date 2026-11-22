package io.github.krosstack.kscope.service.freshness

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.TestGit
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant

internal object FreshnessFixtures {
    val PATIENT = OpenOptions(level = ScanLevel.SYNTAX, freshnessLimitMillis = 60_000)

    fun write(root: Path, relative: String, text: String): Path {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        return Files.writeString(file, text)
    }

    fun repository(root: Path): Path {
        write(root, "lib/A.kt", "class A\n")
        write(root, "lib/B.kt", "class B\n")
        write(root, "other/C.kt", "class C\n")
        TestGit.run(root, listOf("init", "-q"))
        commit(root, "first")
        return root
    }

    fun repositories(workspace: Path, count: Int): List<Path> = (1..count).map { number ->
        val root = workspace.resolve("r" + number.toString().padStart(2, '0'))
        write(root, "lib/A$number.kt", "class A$number\n")
        TestGit.run(root, listOf("init", "-q"))
        commit(root, "first")
        root
    }

    fun commit(root: Path, message: String) {
        TestGit.run(root, listOf("add", "-A"))
        TestGit.run(root, listOf("commit", "-q", "-m", message))
    }

    fun scanned(workspace: Path, options: OpenOptions = PATIENT): Instant {
        KrosScope.open(workspace, options).use { it.scan() }
        return Instant.parse(checkNotNull(ScanTimeFile(workspace.resolve(".kscope")).read()).startedAt)
    }

    fun scannedWithBound(workspace: Path, bound: Int): Instant {
        val started = scanned(workspace)
        KrosScope.open(workspace, PATIENT).use { scope ->
            val file = ScanTimeFile(workspace.resolve(".kscope"))
            val times = checkNotNull(file.read())
            val recorded = ScanDirtyPaths(bound = bound).of(scope.checkouts(null))
            file.write(started, Instant.parse(times.finishedAt), recorded)
        }
        return started
    }

    fun scannedWithoutList(workspace: Path): Instant {
        val started = scanned(workspace)
        val finished = checkNotNull(ScanTimeFile(workspace.resolve(".kscope")).read()).finishedAt
        val old = """{"format":1,"startedAt":"$started","finishedAt":"$finished"}"""
        Files.writeString(workspace.resolve(".kscope/scan.json"), old)
        return started
    }

    fun editedBeforeScan(root: Path, vararg names: String) {
        val earlier = Instant.now().minusSeconds(60)
        names.forEach { name -> changed(root.resolve("lib/$name.kt"), "class ${name}2\n", earlier) }
    }

    fun freshness(workspace: Path, options: OpenOptions = PATIENT, folder: String? = null): IndexFreshness =
        KrosScope.open(workspace, options).use { it.freshness(folder) }

    fun changed(file: Path, text: String, at: Instant): Path {
        Files.writeString(file, text)
        return Files.setLastModifiedTime(file, FileTime.from(at))
    }
}
