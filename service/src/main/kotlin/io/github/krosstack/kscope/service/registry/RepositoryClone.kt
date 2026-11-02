package io.github.krosstack.kscope.service.registry

import io.github.krosstack.kscope.service.registry.RegistrationOutcome.Reason
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Clones a Git URL into a new directory with the user's Git, the one network action of kscope. */
internal class RepositoryClone(private val git: String) {
    sealed interface Result {
        data object Cloned : Result

        data class Failed(val outcome: RegistrationOutcome.Refused) : Result
    }

    fun clone(url: String, ref: String?, target: Path): Result {
        if (Files.exists(target)) return failed(Reason.DIRECTORY_EXISTS, "$target exists already; pass --dir.")
        Files.createDirectories(target.parent)
        val command = listOf(git, "clone", "--quiet") + listOfNotNull(ref?.let { "--branch" }, ref) +
            listOf("--", url, target.toString())
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            process.outputStream.close()
            val output = process.inputStream.readAllBytes().toString(Charsets.UTF_8).trim()
            if (process.waitFor() == 0) Result.Cloned else failed(Reason.CLONE_FAILED, "git clone failed: $output")
        } catch (failure: IOException) {
            failed(Reason.GIT_UNAVAILABLE, "Git could not be started: ${failure.message}")
        }
    }

    private fun failed(reason: Reason, message: String) = Result.Failed(RegistrationOutcome.Refused(reason, message))
}
