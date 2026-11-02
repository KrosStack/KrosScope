package io.github.krosstack.kscope.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import io.github.krosstack.kscope.cli.text.format.Hints
import io.github.krosstack.kscope.service.NotIndexedException
import io.github.krosstack.kscope.service.WorkspacePathException
import io.github.krosstack.kscope.service.level.LevelRefusedException
import io.github.krosstack.kscope.service.settings.InvalidSettingsException
import io.github.krosstack.kscope.service.trust.TrustStoreException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.sql.SQLException

/**
 * Runs a use case and turns the failures of the file system, the index and the settings file into an error message and
 * exit code 1.
 */
internal fun <T> CliktCommand.guarded(useCase: () -> T): T = try {
    useCase()
} catch (failure: InputFailure) {
    fail(failure.message.orEmpty())
} catch (failure: WorkspacePathException) {
    fail(failure.message.orEmpty())
} catch (_: NotIndexedException) {
    fail(Hints.NO_INDEX)
} catch (failure: InvalidSettingsException) {
    fail(failure.message.orEmpty())
} catch (failure: LevelRefusedException) {
    fail(failure.message.orEmpty())
} catch (failure: TrustStoreException) {
    fail(failure.message.orEmpty())
} catch (failure: IOException) {
    fail("The workspace could not be read or the index could not be written (${failure.message}). " + CHECK_ACCESS)
} catch (failure: SQLException) {
    fail("The index is unreadable (${failure.message}). Run `kscope scan --rebuild` to recreate it.")
} catch (failure: SerializationException) {
    fail("Input JSON could not be read (${failure.message}).")
}

private fun CliktCommand.fail(message: String): Nothing {
    echo("error: $message", err = true)
    throw ProgramResult(ExitCodes.FAILURE)
}

private const val CHECK_ACCESS = "Check that you can read the workspace and write to its .kscope directory."
