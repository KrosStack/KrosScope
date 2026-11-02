package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.launch.ServiceLaunch
import java.nio.file.Path

internal class WindowsRunEntry(
    directory: ServiceDirectory,
    private val runner: CommandRunner,
    private val environment: (String) -> String?,
) : SessionEntry {
    private val file = LaunchFile(directory.launchFile)
    private val registration = RegistrationFile(directory.registrationFile)

    override fun install(launch: ServiceLaunch): Installation {
        val words = file.commandWords(launch)
        val commandLine = RegistryValue.commandLine(words)
        val registry = registry()
        return when {
            registry == null -> Installation.Refused(NO_SYSTEM_ROOT)
            commandLine.length > MAXIMUM_LENGTH -> Installation.Refused(tooLong(commandLine.length, words))
            else -> file.installing(launch) { register(registry, commandLine) }
        }
    }

    override fun uninstall(): Removal {
        val registry = registry()
        return when {
            registry != null && runner.run(query(registry)).succeeded -> unregister(registry)
            file.exists() -> removed()
            else -> Removal.NotInstalled
        }
    }

    override fun state(): EntryState {
        val outcome = registry()?.let { runner.run(query(it)) }
        return if (outcome is CommandOutcome.Finished && outcome.succeeded) {
            EntryState.Registered(RegistryValue.registeredCommand(outcome.output))
        } else {
            EntryState.NotRegistered
        }
    }

    private fun register(registry: String, commandLine: String): Installation {
        val importCommand = listOf(registry, "import", registration.path.toString())
        val outcome = registration.importing(commandLine) { runner.run(importCommand) }
        return when {
            outcome.succeeded -> Installation.Installed
            else -> Installation.Refused(outcome.failureOf(importCommand))
        }
    }

    private fun unregister(registry: String): Removal {
        val delete = listOf(registry, "delete", KEY, "/v", RegistryValue.NAME, "/f")
        val outcome = runner.run(delete)
        return if (outcome.succeeded) removed() else Removal.Refused(outcome.failureOf(delete))
    }

    private fun removed(): Removal {
        file.delete()
        registration.delete()
        return Removal.Removed
    }

    private fun query(registry: String): List<String> = listOf(registry, "query", KEY, "/v", RegistryValue.NAME)

    private fun registry(): String? =
        environment("SystemRoot")?.takeIf { it.isNotBlank() }?.let { Path.of(it, "System32", "reg.exe").toString() }

    private fun tooLong(length: Int, words: List<String>): String =
        "Windows keeps at most $MAXIMUM_LENGTH characters in a Run entry, and the command for this one has $length. " +
            "The two paths that make it long are ${words[0]} and ${file.path}. Use a shorter directory for the " +
            "service (the variable KSCOPE_SERVICE_HOME) or for Java and run `kscope service install` again. " +
            "Nothing was registered."

    private companion object {
        const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        const val MAXIMUM_LENGTH = 260
        const val NO_SYSTEM_ROOT = "The variable SystemRoot is not set, so reg.exe cannot be found. " +
            "Nothing was registered."
    }
}
