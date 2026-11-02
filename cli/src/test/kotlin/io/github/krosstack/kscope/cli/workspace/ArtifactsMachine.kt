package io.github.krosstack.kscope.cli.workspace

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.cli.parseWithExitCodes
import io.github.krosstack.kscope.service.registry.RepositoryRegistry
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** A workspace, a home directory and a registry file below a temporary directory, to run `workspace artifacts` on. */
internal class ArtifactsMachine : AutoCloseable {
    val base: Path = Files.createTempDirectory("kscope-cli-artifacts").toRealPath()
    val app: Path = Files.createDirectories(base.resolve("app"))
    val home: Path = Files.createDirectories(base.resolve("user"))
    val registryFile: Path = base.resolve("service").resolve(RepositoryRegistry.FILE_NAME)
    val registry = RepositoryRegistry(registryFile)
    private val userHome: (String) -> String? = { if (it == "user.home") home.toString() else null }

    fun folder(name: String): Path = Files.createDirectories(base.resolve(name))

    fun slashed(path: Path): String = path.toString().replace(File.separatorChar, '/')

    /** Runs `workspace artifacts` with [arguments] on the workspace, reporting usage errors as the tool does. */
    fun run(vararg arguments: String): CliktCommandTestResult =
        execute(listOf("artifacts") + arguments.toList() + listOf("--workspace", app.toString()))

    /** Runs `workspace` with [arguments] as given. */
    fun execute(arguments: List<String>): CliktCommandTestResult {
        val command = WorkspaceCommand(userHome) { registry }
        return command.test(arguments) { command.parseWithExitCodes(it) }
    }

    override fun close() {
        base.toFile().deleteRecursively()
    }
}
